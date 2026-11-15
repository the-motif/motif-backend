package com.motif.payment.api

import com.motif.payment.domain.Purchase
import com.motif.payment.domain.PurchaseStatus
import com.motif.payment.repository.PurchaseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

// 정산 대사용 결제 거래 조회 / settlement가 호출
interface PaymentReconciliationApi {

    // [from, to) 구간에 일어난 판매(구매 시각)·환불(환불 시각)
    fun transactionsBetween(from: LocalDateTime, to: LocalDateTime): List<PaymentTransactionView>

    // 구매 ID별 전체 거래 / 날짜 경계 확인용
    fun transactionsOf(purchaseIds: Collection<Long>): List<PaymentTransactionView>
}

enum class PaymentTransactionKind { SALE, REFUND }

// amount는 정산 원장과 같은 부호 / 환불은 음수
data class PaymentTransactionView(
    val purchaseId: Long,
    val creatorId: Long,
    val kind: PaymentTransactionKind,
    val amount: Long,
    val occurredAt: LocalDateTime,
)

@Service
@Transactional(readOnly = true)
class PaymentReconciliationQuery(
    private val purchaseRepository: PurchaseRepository,
) : PaymentReconciliationApi {

    override fun transactionsBetween(from: LocalDateTime, to: LocalDateTime): List<PaymentTransactionView> =
        purchaseRepository.findAllCreatedBetween(from, to).map(::sale) +
            purchaseRepository.findAllRefundedBetween(from, to).map(::refund)

    override fun transactionsOf(purchaseIds: Collection<Long>): List<PaymentTransactionView> {
        if (purchaseIds.isEmpty()) return emptyList()
        return purchaseRepository.findAllById(purchaseIds).flatMap { p ->
            if (p.status == PurchaseStatus.REFUNDED) listOf(sale(p), refund(p)) else listOf(sale(p))
        }
    }

    private fun sale(p: Purchase) =
        PaymentTransactionView(p.id!!, p.sellerId, PaymentTransactionKind.SALE, p.listPrice, p.createdAt)

    private fun refund(p: Purchase) =
        PaymentTransactionView(p.id!!, p.sellerId, PaymentTransactionKind.REFUND, -p.listPrice, p.refundedAt!!)
}
