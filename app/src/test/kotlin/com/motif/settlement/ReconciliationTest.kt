package com.motif.settlement

import com.motif.common.event.PurchaseCompletedEvent
import com.motif.common.event.PurchaseRefundedEvent
import com.motif.common.exception.BusinessException
import com.motif.settlement.domain.MismatchType
import com.motif.settlement.domain.ReconciliationMismatch
import com.motif.settlement.domain.ReconciliationStatus
import com.motif.settlement.repository.ReconciliationMismatchRepository
import com.motif.settlement.service.DailySettlementJob
import com.motif.settlement.service.ReconciliationJob
import com.motif.settlement.service.SalesRecordService
import com.motif.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

// 대상일은 과거 고정 (다른 데이터와 안 겹치는 날짜), 창작자 ID 무작위
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class ReconciliationTest @Autowired constructor(
    private val reconciliationJob: ReconciliationJob,
    private val dailySettlementJob: DailySettlementJob,
    private val salesRecordService: SalesRecordService,
    private val mismatchRepository: ReconciliationMismatchRepository,
    private val mockMvc: MockMvc,
    private val jdbc: JdbcClient,
) {

    private val creatorId = Random.nextLong(10_000_000, 99_999_999)
    private val day = LocalDate.of(2026, 2, 3)

    @Test
    fun `결제·원장 일치 - 판매·환불 모두 MATCHED`() {
        val p1 = purchase(3000, day.atTime(10, 0))
        val p2 = purchase(1000, day.atTime(11, 0), refundedAt = day.atTime(12, 0))
        salesRecordService.onPurchaseCompleted(sale(p1, 3000, day.atTime(10, 0)))
        salesRecordService.onPurchaseCompleted(sale(p2, 1000, day.atTime(11, 0)))
        salesRecordService.onPurchaseRefunded(refund(p2, day.atTime(12, 0)))

        val run = reconciliationJob.run(day)

        assertThat(run.status).isEqualTo(ReconciliationStatus.MATCHED)
        assertThat(run.paymentCount).isEqualTo(3)
        assertThat(run.paymentAmount).isEqualTo(3000)
        assertThat(run.settlementCount).isEqualTo(3)
        assertThat(run.settlementAmount).isEqualTo(3000)
    }

    @Test
    fun `판매 이벤트 유실 → MISSING_IN_SETTLEMENT + 그 창작자 정산 보류, 해결 후 정산`() {
        val ok = purchase(2000, day.atTime(9, 0))
        salesRecordService.onPurchaseCompleted(sale(ok, 2000, day.atTime(9, 0)))
        val lost = purchase(5000, day.atTime(10, 0))

        val run = reconciliationJob.run(day)
        assertThat(run.status).isEqualTo(ReconciliationStatus.MISMATCHED)
        val mismatch = mismatchRepository.findAllByRunIdOrderByIdAsc(run.id!!).single()
        assertThat(mismatch.purchaseId).isEqualTo(lost)
        assertThat(mismatch.mismatchType).isEqualTo(MismatchType.MISSING_IN_SETTLEMENT)
        assertThat(mismatch.creatorId).isEqualTo(creatorId)

        val held = dailySettlementJob.run(day.plusDays(1))
        assertThat(held.heldCreatorIds).contains(creatorId)
        assertThat(settlementDateOf(ok)).isNull()

        // 관리자 조치 (유실 이벤트 재처리) 후 해결 처리
        salesRecordService.onPurchaseCompleted(sale(lost, 5000, day.atTime(10, 0)))
        mockMvc.post("/api/admin/reconciliations/mismatches/${mismatch.id}/resolve") {
            with(jwt().jwt { it.subject("999999") }.authorities(SimpleGrantedAuthority("ROLE_ADMIN")))
            contentType = MediaType.APPLICATION_JSON
            content = """{"note":"outbox 재발행으로 판매 원장 복구"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.resolvedBy") { value(999999) }
        }

        val settled = dailySettlementJob.run(day.plusDays(1))
        assertThat(settled.heldCreatorIds).doesNotContain(creatorId)
        assertThat(settlementDateOf(ok)).isEqualTo(day.plusDays(1))
    }

    @Test
    fun `환불 반영 누락은 STATUS_DIFF, 금액 다르면 AMOUNT_DIFF`() {
        val refunded = purchase(3000, day.atTime(10, 0), refundedAt = day.atTime(15, 0))
        salesRecordService.onPurchaseCompleted(sale(refunded, 3000, day.atTime(10, 0)))
        val wrong = purchase(4000, day.atTime(11, 0))
        salesRecordService.onPurchaseCompleted(sale(wrong, 400, day.atTime(11, 0)))

        val types = mismatchRepository.findAllByRunIdOrderByIdAsc(reconciliationJob.run(day).id!!)
            .associate { it.purchaseId to it.mismatchType }

        assertThat(types).isEqualTo(mapOf(refunded to MismatchType.STATUS_DIFF, wrong to MismatchType.AMOUNT_DIFF))
    }

    @Test
    fun `자정 경계 - 결제는 D 23시59분, 원장은 D+1 0시면 일치로 봄`() {
        val p = purchase(1000, day.atTime(23, 59, 59, 999_000_000))
        salesRecordService.onPurchaseCompleted(sale(p, 1000, day.plusDays(1).atStartOfDay().plusNanos(1_000_000)))

        assertThat(reconciliationJob.run(day).status).isEqualTo(ReconciliationStatus.MATCHED)
    }

    @Test
    fun `재실행 - attempt 증가, 고쳐진 건은 이전 불일치 자동 해결`() {
        val lost = purchase(5000, day.atTime(10, 0))
        val first = reconciliationJob.run(day)
        val firstMismatch = mismatchRepository.findAllByRunIdOrderByIdAsc(first.id!!).single()

        salesRecordService.onPurchaseCompleted(sale(lost, 5000, day.atTime(10, 0)))
        val second = reconciliationJob.run(day)

        assertThat(second.attempt).isEqualTo(first.attempt + 1)
        assertThat(second.status).isEqualTo(ReconciliationStatus.MATCHED)
        val closed = mismatchRepository.findById(firstMismatch.id!!).get()
        assertThat(closed.resolvedBy).isEqualTo(ReconciliationMismatch.SYSTEM_RESOLVER)
        assertThat(closed.resolutionNote).startsWith("재대사 일치")
    }

    @Test
    fun `오늘·미래 날짜는 대사 불가`() {
        val e = assertThrows<BusinessException> { reconciliationJob.run(LocalDate.now().plusDays(1)) }
        assertThat(e.errorCode.code).isEqualTo("SETTLEMENT_020")
    }

    private fun purchase(price: Long, at: LocalDateTime, refundedAt: LocalDateTime? = null): Long {
        val keys = GeneratedKeyHolder()
        jdbc.sql(
            """
            INSERT INTO purchase (buyer_id, recipient_id, seller_id, target_type, target_id, list_price,
                                  paid_point_amount, idempotency_key, status, refunded_at, created_at, updated_at)
            VALUES (1, 1, :seller, 'CONTENT', 1, :price, :price, :key, :status, :refundedAt, :at, :at)
            """,
        )
            .param("seller", creatorId)
            .param("price", price)
            .param("key", UUID.randomUUID().toString())
            .param("status", if (refundedAt == null) "COMPLETED" else "REFUNDED")
            .param("refundedAt", refundedAt)
            .param("at", at)
            .update(keys)
        return keys.key!!.toLong()
    }

    private fun settlementDateOf(purchaseId: Long): LocalDate? =
        jdbc.sql("SELECT settlement_date FROM sales_record WHERE purchase_id = ? AND type = 'SALE'")
            .param(purchaseId)
            .query(LocalDate::class.java)
            .optional()
            .orElse(null)

    private fun sale(purchaseId: Long, price: Long, at: LocalDateTime) = PurchaseCompletedEvent(
        UUID.randomUUID().toString(), purchaseId, 1L, creatorId, "CONTENT", 1L, price, at,
    )

    private fun refund(purchaseId: Long, at: LocalDateTime) = PurchaseRefundedEvent(
        UUID.randomUUID().toString(), purchaseId, 1L, creatorId, "CONTENT", 1L, 0L, at,
    )
}
