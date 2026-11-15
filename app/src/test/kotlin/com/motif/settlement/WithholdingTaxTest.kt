package com.motif.settlement

import com.motif.common.exception.BusinessException
import com.motif.settlement.domain.CreatorType
import com.motif.settlement.service.WithholdingTaxJob
import com.motif.settlement.service.WithholdingTaxRepository
import com.motif.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID
import kotlin.random.Random

// 집계 월은 과거 고정, 창작자 ID 무작위
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class WithholdingTaxTest @Autowired constructor(
    private val job: WithholdingTaxJob,
    private val repository: WithholdingTaxRepository,
    private val mockMvc: MockMvc,
    private val jdbc: JdbcClient,
) {

    private val creatorId = Random.nextLong(10_000_000, 99_999_999)
    private val month = YearMonth.of(2026, 1)

    @Test
    fun `지급 완료분만 지급일 기준으로 창작자별 합계, 재실행해도 같은 결과`() {
        payout(100_000, 3_000, 300, paidAt = LocalDateTime.of(2026, 1, 5, 10, 0))
        payout(33_330, 990, 90, paidAt = LocalDateTime.of(2026, 1, 31, 23, 59))
        payout(50_000, 1_500, 150, paidAt = LocalDateTime.of(2026, 2, 1, 0, 0)) // 다음 달
        payout(20_000, 600, 60, status = "FAILED")
        payout(40_000, 1_200, 120, status = "PENDING")

        job.run(month)
        job.run(month)

        val row = mine().single()
        assertThat(row.taxMonth).isEqualTo("202601")
        assertThat(row.creatorType).isEqualTo(CreatorType.INDIVIDUAL)
        assertThat(row.payoutCount).isEqualTo(2)
        assertThat(row.grossAmount).isEqualTo(133_330)
        assertThat(row.incomeTax).isEqualTo(3_990)
        assertThat(row.localTax).isEqualTo(390)
    }

    @Test
    fun `월 중 사업자로 바뀌면 유형별로 행이 나뉨`() {
        payout(100_000, 3_000, 300, paidAt = LocalDateTime.of(2026, 1, 5, 10, 0))
        payout(70_000, 0, 0, paidAt = LocalDateTime.of(2026, 1, 20, 10, 0), type = "BUSINESS")

        job.run(month)

        assertThat(mine().associate { it.creatorType to it.incomeTax })
            .isEqualTo(mapOf(CreatorType.INDIVIDUAL to 3_000L, CreatorType.BUSINESS to 0L))
    }

    @Test
    fun `이번 달은 집계 불가 (아직 지급이 더 생길 수 있음)`() {
        val e = assertThrows<BusinessException> { job.run(YearMonth.now()) }
        assertThat(e.errorCode.code).isEqualTo("SETTLEMENT_024")
    }

    @Test
    fun `창작자는 연도별 내 내역 조회`() {
        payout(100_000, 3_000, 300, paidAt = LocalDateTime.of(2026, 1, 5, 10, 0))
        job.run(month)

        mockMvc.get("/api/creator/withholding-taxes?year=2026") {
            with(jwt().jwt { it.subject(creatorId.toString()) }.authorities(SimpleGrantedAuthority("ROLE_CREATOR")))
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].taxMonth") { value("202601") }
            jsonPath("$[0].incomeTax") { value(3_000) }
        }
    }

    private fun mine() = repository.findByMonth(month).filter { it.creatorId == creatorId }

    private fun payout(
        gross: Long,
        incomeTax: Long,
        localTax: Long,
        paidAt: LocalDateTime? = null,
        status: String = "COMPLETED",
        type: String = "INDIVIDUAL",
    ) {
        val created = paidAt ?: LocalDateTime.of(2026, 1, 10, 10, 0)
        val keys = GeneratedKeyHolder()
        jdbc.sql(
            """
            INSERT INTO withdrawal_request (creator_id, amount, idempotency_key, status, created_at, updated_at)
            VALUES (:creator, :amount, :key, 'APPROVED', :at, :at)
            """,
        )
            .param("creator", creatorId).param("amount", gross).param("key", UUID.randomUUID().toString()).param("at", created)
            .update(keys)
        jdbc.sql(
            """
            INSERT INTO payout (payout_no, withdrawal_request_id, creator_id, creator_type, gross_amount, income_tax, local_tax,
                                net_amount, bank_code, account_number, status, paid_at, created_at, updated_at)
            VALUES (:no, :requestId, :creator, :type, :gross, :income, :local, :net, '004', 'enc', :status, :paidAt, :at, :at)
            """,
        )
            .param("no", "PO-" + UUID.randomUUID().toString().replace("-", ""))
            .param("requestId", keys.key!!.toLong())
            .param("creator", creatorId)
            .param("type", type)
            .param("gross", gross)
            .param("income", incomeTax)
            .param("local", localTax)
            .param("net", gross - incomeTax - localTax)
            .param("status", status)
            .param("paidAt", if (status == "COMPLETED") paidAt else null)
            .param("at", created)
            .update()
    }
}
