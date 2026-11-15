package com.motif.common

import com.motif.common.event.PurchaseCompletedEvent
import com.motif.common.trace.TraceId
import com.motif.payment.outbox.OutboxRelay
import com.motif.payment.outbox.OutboxWriter
import com.motif.payment.repository.OutboxEventRepository
import com.motif.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.event.EventListener
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class TraceIdTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val outboxWriter: OutboxWriter,
    private val outboxRelay: OutboxRelay,
    private val outboxEventRepository: OutboxEventRepository,
    private val recorder: TraceRecorder,
) {

    @Test
    fun `헤더 없으면 새 추적 ID, 에러 응답 본문에도 같은 값`() {
        val result = mockMvc.get("/api/members/me").andExpect {
            status { isUnauthorized() }
            header { string("X-Request-Id", org.hamcrest.Matchers.matchesPattern("^[0-9a-f]{16}$")) }
        }.andReturn()

        val traceId = result.response.getHeader("X-Request-Id")
        assertThat(result.response.contentAsString).contains("\"traceId\":\"$traceId\"")
    }

    @Test
    fun `형식 맞는 X-Request-Id는 그대로 사용`() {
        mockMvc.get("/api/members/me") { header("X-Request-Id", "ios-7f3a9c21") }.andExpect {
            header { string("X-Request-Id", "ios-7f3a9c21") }
            jsonPath("$.traceId") { value("ios-7f3a9c21") }
        }
    }

    @Test
    fun `형식 틀린 값(로그 주입 시도)은 버리고 새로 만듦`() {
        mockMvc.get("/api/members/me") { header("X-Request-Id", "abc\nFAKE LOG LINE") }.andExpect {
            header { string("X-Request-Id", org.hamcrest.Matchers.matchesPattern("^[0-9a-f]{16}$")) }
        }
    }

    @Test
    fun `outbox에 요청 추적 ID 저장 → 발행 때 리스너가 같은 ID로 실행`() {
        val eventId = UUID.randomUUID().toString()
        TraceId.open("req-12345678").use {
            outboxWriter.append(eventId, "PURCHASE", 1L, PurchaseCompletedEvent.TYPE, sale(eventId))
        }
        assertThat(outboxEventRepository.findAll().single { it.eventId == eventId }.traceId).isEqualTo("req-12345678")

        outboxRelay.relayPending()

        assertThat(recorder.seen[eventId]).isEqualTo("req-12345678")
        assertThat(TraceId.current()).isNull()
    }

    private fun sale(eventId: String) = PurchaseCompletedEvent(
        eventId, Random.nextLong(100_000_000, 999_999_999), 1L, Random.nextLong(10_000_000, 99_999_999),
        "CONTENT", 1L, 1000L, LocalDateTime.now(),
    )

    @TestConfiguration
    class Config {
        @Bean
        fun traceRecorder() = TraceRecorder()
    }

    class TraceRecorder {
        val seen = mutableMapOf<String, String?>()

        @EventListener
        fun on(event: PurchaseCompletedEvent) {
            seen[event.eventId()] = TraceId.current()
        }
    }
}
