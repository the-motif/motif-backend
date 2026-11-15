package com.motif.payment.service

import com.motif.common.event.PurchaseCompletedEvent
import com.motif.common.event.PurchaseRefundedEvent
import com.motif.common.exception.BusinessException
import com.motif.content.api.ContentPurchaseApi
import com.motif.content.domain.AccessSource
import com.motif.payment.domain.PointKind
import com.motif.payment.domain.PointLotUsage
import com.motif.payment.domain.PointRefType
import com.motif.payment.domain.PointTransaction
import com.motif.payment.domain.PointTxType
import com.motif.payment.domain.Purchase
import com.motif.payment.domain.PurchaseStatus
import com.motif.payment.domain.PurchaseTarget
import com.motif.payment.exception.PaymentErrorCode
import com.motif.payment.outbox.OutboxWriter
import com.motif.payment.repository.PointLotRepository
import com.motif.payment.repository.PointLotUsageRepository
import com.motif.payment.repository.PointTransactionRepository
import com.motif.payment.repository.PointWalletRepository
import com.motif.payment.repository.PurchaseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

// 구매·환불 DB 처리 / 지갑 락·멱등은 PurchaseFacade
@Service
@Transactional
class PurchaseService(
    private val contentPurchaseApi: ContentPurchaseApi,
    private val walletService: WalletService,
    private val walletRepository: PointWalletRepository,
    private val lotRepository: PointLotRepository,
    private val lotUsageRepository: PointLotUsageRepository,
    private val purchaseRepository: PurchaseRepository,
    private val transactionRepository: PointTransactionRepository,
    private val outboxWriter: OutboxWriter,
    private val clock: Clock,
) {

    // 판매 정보 → 잔액 조건부 차감 → 구매 저장 → lot별 차감 기록 → 원장 → 열람 권한 → outbox
    fun purchaseContent(buyerId: Long, contentId: Long, idempotencyKey: String): Purchase {
        val sale = contentPurchaseApi.getSaleInfo(contentId)
        if (sale.creatorId == buyerId) {
            throw BusinessException(PaymentErrorCode.OWN_CONTENT)
        }
        if (contentPurchaseApi.hasAccess(buyerId, contentId)) {
            throw BusinessException(com.motif.content.exception.ContentErrorCode.ALREADY_OWNED)
        }

        val now = LocalDateTime.now(clock)
        val price = sale.price
        val walletId = walletService.getOrCreate(buyerId).id!!
        // 1차는 유상 포인트만 (무상은 2차)
        if (walletRepository.decreasePaid(walletId, price, now) == 0) {
            throw BusinessException(PaymentErrorCode.INSUFFICIENT_BALANCE)
        }

        val purchase = purchaseRepository.save(
            Purchase(
                buyerId = buyerId,
                sellerId = sale.creatorId,
                targetType = PurchaseTarget.CONTENT,
                targetId = contentId,
                listPrice = price,
                paidPointAmount = price,
                freePointAmount = 0,
                idempotencyKey = idempotencyKey,
            ),
        )
        val purchaseId = purchase.id!!
        useLots(walletId, purchaseId, price, now)

        transactionRepository.save(
            PointTransaction(
                walletId = walletId,
                type = PointTxType.USE,
                paidAmount = -price,
                freeAmount = 0,
                balanceAfter = walletService.currentTotal(walletId),
                refType = PointRefType.PURCHASE,
                refId = purchaseId,
            ),
        )
        contentPurchaseApi.grantAccess(buyerId, contentId, purchaseId, AccessSource.PURCHASE)

        val eventId = UUID.randomUUID().toString()
        outboxWriter.append(
            eventId, AGGREGATE_PURCHASE, purchaseId, PurchaseCompletedEvent.TYPE,
            PurchaseCompletedEvent(eventId, purchaseId, buyerId, sale.creatorId, PurchaseTarget.CONTENT.name, contentId, price, now),
        )
        return purchase
    }

    // 열람 전·7일 이내만 / 쓴 lot에 그대로 복구
    fun refund(buyerId: Long, purchaseId: Long, reason: String): Purchase {
        val purchase = purchaseRepository.findByIdForUpdate(purchaseId)
            ?.takeIf { it.buyerId == buyerId }
            ?: throw BusinessException(PaymentErrorCode.PURCHASE_NOT_FOUND)
        val now = LocalDateTime.now(clock)
        if (purchase.status != PurchaseStatus.COMPLETED ||
            purchase.createdAt.plusDays(REFUND_PERIOD_DAYS).isBefore(now) ||
            contentPurchaseApi.isViewed(buyerId, purchase.targetId)
        ) {
            throw BusinessException(PaymentErrorCode.NOT_REFUNDABLE)
        }

        purchase.refund(reason, now)
        val restored = restoreLots(purchaseId, now)
        val walletId = walletService.getOrCreate(buyerId).id!!
        if (restored > 0) {
            walletRepository.increasePaid(walletId, restored, now)
        }
        transactionRepository.save(
            PointTransaction(
                walletId = walletId,
                type = PointTxType.REFUND,
                paidAmount = restored,
                freeAmount = 0,
                balanceAfter = walletService.currentTotal(walletId),
                refType = PointRefType.PURCHASE,
                refId = purchaseId,
            ),
        )
        contentPurchaseApi.revokeAccess(buyerId, purchase.targetId)

        val eventId = UUID.randomUUID().toString()
        outboxWriter.append(
            eventId, AGGREGATE_PURCHASE, purchaseId, PurchaseRefundedEvent.TYPE,
            PurchaseRefundedEvent(
                eventId, purchaseId, buyerId, purchase.sellerId, purchase.targetType.name,
                purchase.targetId, purchase.listPrice, now,
            ),
        )
        // increasePaid가 영속성 컨텍스트를 비워서 다시 읽어 반환
        return purchaseRepository.findById(purchaseId).get()
    }

    private fun useLots(walletId: Long, purchaseId: Long, amount: Long, now: LocalDateTime) {
        var left = amount
        for (lot in lotRepository.findUsableForUpdate(walletId, now)) {
            if (left == 0L) break
            if (lot.kind != PointKind.PAID) continue
            val used = lot.use(left)
            lotUsageRepository.save(PointLotUsage(lot.id!!, purchaseId, used))
            left -= used
        }
        // 지갑 잔액은 충분한데 lot 합이 부족 → 데이터 불일치, 전체 롤백
        check(left == 0L) { "lot 잔액 부족 walletId=$walletId 부족분=$left" }
    }

    // 만료된 lot 몫은 복구 안 함 (환불 불가 금액)
    private fun restoreLots(purchaseId: Long, now: LocalDateTime): Long {
        val usages = lotUsageRepository.findAllByPurchaseId(purchaseId)
        val lots = lotRepository.findAllByIdForUpdate(usages.map { it.lotId }).associateBy { it.id!! }
        var restored = 0L
        for (usage in usages) {
            val lot = lots.getValue(usage.lotId)
            usage.markRestored(now)
            if (lot.expiresAt.isAfter(now)) {
                lot.restore(usage.amount)
                restored += usage.amount
            }
        }
        return restored
    }

    companion object {
        const val AGGREGATE_PURCHASE = "PURCHASE"
        const val REFUND_PERIOD_DAYS = 7L
    }
}
