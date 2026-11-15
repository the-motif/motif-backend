package com.motif.settlement.service

import com.motif.common.event.PurchaseCompletedEvent
import com.motif.common.event.PurchaseRefundedEvent
import com.motif.settlement.domain.RevenueType
import com.motif.settlement.domain.SalesRecord
import com.motif.settlement.domain.SalesType
import com.motif.settlement.domain.SettlementCalculator
import com.motif.settlement.repository.SalesRecordRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 결제 이벤트 → 판매 원장 / OutboxRelay 트랜잭션에 참여 (실패하면 발행도 롤백 → 재시도)
@Service
@Transactional
class SalesRecordService(
    private val salesRecordRepository: SalesRecordRepository,
    private val feePolicyService: FeePolicyService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun onPurchaseCompleted(event: PurchaseCompletedEvent) {
        if (salesRecordRepository.existsByEventId(event.eventId())) {
            log.info("이미 처리한 이벤트 eventId={}", event.eventId())
            return
        }
        val revenueType = RevenueType.valueOf(event.targetType())
        val feeRate = feePolicyService.applicable(event.sellerId(), revenueType, event.purchasedAt()).feeRate
        salesRecordRepository.save(
            SalesRecord(
                eventId = event.eventId(),
                purchaseId = event.purchaseId(),
                creatorId = event.sellerId(),
                type = SalesType.SALE,
                revenueType = revenueType,
                listPrice = event.listPrice(),
                feeRate = feeRate,
                feeAmount = SettlementCalculator.fee(event.listPrice(), feeRate),
                occurredAt = event.purchasedAt(),
            ),
        )
    }

    // 환불은 판매 때 요율·수수료를 그대로 음수로 (요율이 바뀌었어도)
    @EventListener
    fun onPurchaseRefunded(event: PurchaseRefundedEvent) {
        if (salesRecordRepository.existsByEventId(event.eventId())) {
            log.info("이미 처리한 이벤트 eventId={}", event.eventId())
            return
        }
        // 판매 행보다 먼저 오면 예외 → outbox 재시도
        val sale = salesRecordRepository.findByPurchaseIdAndType(event.purchaseId(), SalesType.SALE)
            ?: error("판매 기록 없는 환불 purchaseId=${event.purchaseId()}")
        salesRecordRepository.save(
            SalesRecord(
                eventId = event.eventId(),
                purchaseId = event.purchaseId(),
                creatorId = sale.creatorId,
                type = SalesType.REFUND,
                revenueType = sale.revenueType,
                listPrice = -sale.listPrice,
                feeRate = sale.feeRate,
                feeAmount = -sale.feeAmount,
                occurredAt = event.refundedAt(),
            ),
        )
    }
}
