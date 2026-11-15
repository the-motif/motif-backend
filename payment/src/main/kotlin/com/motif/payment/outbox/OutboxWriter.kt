package com.motif.payment.outbox

import com.motif.payment.domain.OutboxEvent
import com.motif.payment.repository.OutboxEventRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

// 호출한 쪽 트랜잭션에 함께 저장 (구매가 롤백되면 이벤트도 사라짐)
@Component
class OutboxWriter(
    private val outboxEventRepository: OutboxEventRepository,
    private val jsonMapper: JsonMapper,
) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun append(eventId: String, aggregateType: String, aggregateId: Long, eventType: String, event: Any) {
        outboxEventRepository.save(
            OutboxEvent(eventId, aggregateType, aggregateId, eventType, jsonMapper.writeValueAsString(event)),
        )
    }
}
