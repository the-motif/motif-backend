package com.motif.payment.outbox

import com.motif.common.event.PurchaseCompletedEvent
import com.motif.common.event.PurchaseRefundedEvent
import com.motif.payment.domain.OutboxEvent
import com.motif.payment.repository.OutboxEventRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.LocalDateTime

// PENDING 이벤트를 Spring 이벤트로 발행 / 2단계에서 Kafka 발행으로 교체
// 이벤트 1건 = 트랜잭션 1개 / 리스너가 같은 트랜잭션에 참여 → 리스너 저장과 PUBLISHED가 함께 커밋
@Component
class OutboxRelay(
    private val outboxEventRepository: OutboxEventRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val jsonMapper: JsonMapper,
    private val clock: Clock,
    transactionManager: PlatformTransactionManager,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)
    private val newTx = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    @Scheduled(fixedDelayString = "\${motif.outbox.relay-delay-ms:1000}")
    fun scheduledRelay() {
        relayPending()
    }

    // 발행한 건수 반환
    fun relayPending(): Int =
        outboxEventRepository.findPendingIds(BATCH_SIZE).count { relay(it) }

    private fun relay(id: Long): Boolean =
        try {
            tx.execute {
                // 다른 서버가 잡은 행은 건너뜀
                val event = outboxEventRepository.findPendingByIdForUpdate(id) ?: return@execute false
                eventPublisher.publishEvent(toEvent(event))
                event.markPublished(LocalDateTime.now(clock))
                true
            } == true
        } catch (e: Exception) {
            log.warn("outbox 발행 실패 id={}", id, e)
            newTx.executeWithoutResult {
                outboxEventRepository.findById(id).ifPresent { it.markRetry(MAX_RETRY) }
            }
            false
        }

    private fun toEvent(event: OutboxEvent): Any = when (event.eventType) {
        PurchaseCompletedEvent.TYPE -> jsonMapper.readValue(event.payload, PurchaseCompletedEvent::class.java)
        PurchaseRefundedEvent.TYPE -> jsonMapper.readValue(event.payload, PurchaseRefundedEvent::class.java)
        else -> error("알 수 없는 이벤트 타입 ${event.eventType}")
    }

    companion object {
        private const val BATCH_SIZE = 100
        private const val MAX_RETRY = 5
    }
}
