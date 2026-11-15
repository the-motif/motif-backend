package com.motif.payment.outbox

import com.motif.common.exception.BusinessException
import com.motif.payment.domain.OutboxEvent
import com.motif.payment.domain.OutboxStatus
import com.motif.payment.exception.PaymentErrorCode
import com.motif.payment.repository.OutboxEventRepository
import com.motif.payment.service.PurchaseService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 이벤트 재발행 / PENDING으로 되돌리면 Relay가 다음 주기에 발행
// 받는 쪽(settlement)이 eventId로 중복을 거르므로 이미 반영된 이벤트를 다시 보내도 결과 같음
@Service
@Transactional
class OutboxAdminService(
    private val outboxEventRepository: OutboxEventRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun findByStatus(status: OutboxStatus, pageable: Pageable): Slice<OutboxEvent> =
        outboxEventRepository.findAllByStatusOrderByIdAsc(status, pageable)

    fun requeue(eventId: Long): OutboxEvent {
        val event = outboxEventRepository.findByIdForUpdate(eventId)
            ?: throw BusinessException(PaymentErrorCode.OUTBOX_EVENT_NOT_FOUND)
        if (event.status == OutboxStatus.PENDING) {
            throw BusinessException(PaymentErrorCode.OUTBOX_EVENT_PENDING)
        }
        log.info("outbox 재발행 id={} eventId={} 이전 상태={}", event.id, event.eventId, event.status)
        event.requeue()
        return event
    }

    // 구매 하나의 이벤트 전부 / 이미 PENDING인 건 그대로
    fun requeuePurchase(purchaseId: Long): Int =
        outboxEventRepository.findAllByAggregateTypeAndAggregateId(PurchaseService.AGGREGATE_PURCHASE, purchaseId)
            .filter { it.status != OutboxStatus.PENDING }
            .onEach {
                log.info("outbox 재발행 id={} eventId={} purchaseId={} 이전 상태={}", it.id, it.eventId, purchaseId, it.status)
                it.requeue()
            }
            .size
}
