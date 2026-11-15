package com.motif.payment.service

import com.motif.common.exception.BusinessException
import com.motif.common.response.SliceResponse
import com.motif.payment.domain.PurchaseTarget
import com.motif.payment.dto.PurchaseResponse
import com.motif.payment.exception.PaymentErrorCode
import com.motif.payment.repository.PurchaseRepository
import com.motif.payment.support.IdempotencyGuard
import com.motif.payment.support.RedisLockManager
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

// 구매 흐름 조립 / 멱등 키(Redis+DB) → 지갑 락(Redis) → 트랜잭션(PurchaseService)
// 락이 트랜잭션 바깥이라 커밋이 끝난 뒤 락 해제
@Service
class PurchaseFacade(
    private val purchaseService: PurchaseService,
    private val purchaseRepository: PurchaseRepository,
    private val idempotencyGuard: IdempotencyGuard,
    private val lockManager: RedisLockManager,
) {

    fun purchaseContent(buyerId: Long, contentId: Long, idempotencyKey: String): PurchaseResponse =
        idempotencyGuard.execute(
            scope = "purchase",
            key = idempotencyKey,
            findCompleted = {
                purchaseRepository.findByIdempotencyKey(idempotencyKey)?.let {
                    if (it.buyerId != buyerId || it.targetType != PurchaseTarget.CONTENT || it.targetId != contentId) {
                        throw BusinessException(PaymentErrorCode.IDEMPOTENCY_KEY_REUSED)
                    }
                    PurchaseResponse.from(it)
                }
            },
            action = {
                lockManager.withLock(walletLockKey(buyerId)) {
                    PurchaseResponse.from(purchaseService.purchaseContent(buyerId, contentId, idempotencyKey))
                }
            },
        )

    fun refund(buyerId: Long, purchaseId: Long, reason: String): PurchaseResponse =
        lockManager.withLock(walletLockKey(buyerId)) {
            PurchaseResponse.from(purchaseService.refund(buyerId, purchaseId, reason))
        }

    fun getMyPurchases(buyerId: Long, pageable: Pageable): SliceResponse<PurchaseResponse> =
        SliceResponse.of(purchaseRepository.findAllByBuyerIdOrderByIdDesc(buyerId, pageable), PurchaseResponse::from)

    private fun walletLockKey(memberId: Long) = "wallet:$memberId"
}
