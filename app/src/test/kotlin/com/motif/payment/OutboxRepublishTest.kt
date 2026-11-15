package com.motif.payment

import com.motif.common.event.PurchaseCompletedEvent
import com.motif.payment.domain.OutboxStatus
import com.motif.payment.outbox.OutboxRelay
import com.motif.payment.outbox.OutboxWriter
import com.motif.payment.repository.OutboxEventRepository
import com.motif.payment.service.PurchaseService
import com.motif.settlement.domain.MismatchType
import com.motif.settlement.domain.ReconciliationStatus
import com.motif.settlement.repository.ReconciliationMismatchRepository
import com.motif.settlement.repository.SalesRecordRepository
import com.motif.settlement.service.ReconciliationJob
import com.motif.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

// 대상일 과거 고정, 창작자 ID 무작위
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class OutboxRepublishTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val outboxWriter: OutboxWriter,
    private val outboxRelay: OutboxRelay,
    private val outboxEventRepository: OutboxEventRepository,
    private val salesRecordRepository: SalesRecordRepository,
    private val reconciliationJob: ReconciliationJob,
    private val mismatchRepository: ReconciliationMismatchRepository,
    private val jdbc: JdbcClient,
) {

    private val creatorId = Random.nextLong(10_000_000, 99_999_999)
    private val day = LocalDate.of(2026, 2, 10)

    @Test
    fun `유실(FAILED) 이벤트 → 대사 불일치 → 재발행 → 대사 재실행으로 자동 해결`() {
        val purchaseId = purchase(5000, day.atTime(10, 0))
        val eventId = writeEvent(purchaseId, 5000, day.atTime(10, 0))
        markFailed(eventId)

        val first = reconciliationJob.run(day)
        val mismatch = mismatchRepository.findAllByRunIdOrderByIdAsc(first.id!!).single()
        assertThat(mismatch.mismatchType).isEqualTo(MismatchType.MISSING_IN_SETTLEMENT)

        mockMvc.post("/api/admin/reconciliations/mismatches/${mismatch.id}/republish") { with(admin()) }.andExpect {
            status { isOk() }
            jsonPath("$.requeuedEventCount") { value(1) }
        }
        outboxRelay.relayPending()

        assertThat(salesRecordRepository.existsByEventId(eventId)).isTrue()
        assertThat(reconciliationJob.run(day).status).isEqualTo(ReconciliationStatus.MATCHED)
        assertThat(mismatchRepository.findById(mismatch.id!!).get().resolved).isTrue()
    }

    @Test
    fun `이미 반영된 이벤트를 다시 보내도 판매 원장은 1건 (받는 쪽 중복 거름)`() {
        val purchaseId = purchase(3000, day.atTime(11, 0))
        val eventId = writeEvent(purchaseId, 3000, day.atTime(11, 0))
        outboxRelay.relayPending()
        val event = outboxEventRepository.findAll().single { it.eventId == eventId }
        assertThat(event.status).isEqualTo(OutboxStatus.PUBLISHED)

        mockMvc.post("/api/admin/outbox-events/${event.id}/requeue") { with(admin()) }.andExpect {
            jsonPath("$.status") { value("PENDING") }
            jsonPath("$.retryCount") { value(0) }
        }
        outboxRelay.relayPending()

        assertThat(salesRecordRepository.findAllByPurchaseIdIn(listOf(purchaseId))).hasSize(1)
    }

    @Test
    fun `FAILED 목록 조회, PENDING 재발행은 409`() {
        val eventId = writeEvent(purchase(1000, day.atTime(12, 0)), 1000, day.atTime(12, 0))
        val id = markFailed(eventId)

        mockMvc.get("/api/admin/outbox-events?status=FAILED&size=100") { with(admin()) }.andExpect {
            jsonPath("$.content[?(@.id == $id)].eventType") { value(PurchaseCompletedEvent.TYPE) }
        }
        mockMvc.post("/api/admin/outbox-events/$id/requeue") { with(admin()) }
        mockMvc.post("/api/admin/outbox-events/$id/requeue") { with(admin()) }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PAYMENT_013") }
        }
    }

    // 재시도 한도 넘은 상태로 / Relay와 같은 엔티티 메서드 사용
    private fun markFailed(eventId: String): Long {
        val event = outboxEventRepository.findAll().single { it.eventId == eventId }
        event.markRetry(1)
        assertThat(event.status).isEqualTo(OutboxStatus.FAILED)
        return event.id!!
    }

    private fun writeEvent(purchaseId: Long, price: Long, at: LocalDateTime): String {
        val eventId = UUID.randomUUID().toString()
        outboxWriter.append(
            eventId, PurchaseService.AGGREGATE_PURCHASE, purchaseId, PurchaseCompletedEvent.TYPE,
            PurchaseCompletedEvent(eventId, purchaseId, 1L, creatorId, "CONTENT", 1L, price, at),
        )
        return eventId
    }

    private fun purchase(price: Long, at: LocalDateTime): Long {
        val keys = GeneratedKeyHolder()
        jdbc.sql(
            """
            INSERT INTO purchase (buyer_id, recipient_id, seller_id, target_type, target_id, list_price,
                                  paid_point_amount, idempotency_key, status, created_at, updated_at)
            VALUES (1, 1, :seller, 'CONTENT', 1, :price, :price, :key, 'COMPLETED', :at, :at)
            """,
        )
            .param("seller", creatorId).param("price", price).param("key", UUID.randomUUID().toString()).param("at", at)
            .update(keys)
        return keys.key!!.toLong()
    }

    private fun admin() = jwt().jwt { it.subject("999999") }.authorities(SimpleGrantedAuthority("ROLE_ADMIN"))
}
