package com.motif.settlement

import com.motif.common.event.PurchaseCompletedEvent
import com.motif.member.domain.Member
import com.motif.member.repository.MemberRepository
import com.motif.settlement.domain.SalesType
import com.motif.settlement.repository.SalesRecordRepository
import com.motif.settlement.service.SalesRecordService
import com.motif.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class FeePolicyApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val memberRepository: MemberRepository,
    private val salesRecordService: SalesRecordService,
    private val salesRecordRepository: SalesRecordRepository,
    private val jsonMapper: JsonMapper,
    private val clock: Clock,
) {

    private var creatorId = 0L
    private var readerId = 0L

    @BeforeEach
    fun setUp() {
        creatorId = memberRepository.save(Member("fee-c@motif.test", "hashed", "요율작가").apply { promoteToCreator() }).id!!
        readerId = memberRepository.save(Member("fee-r@motif.test", "hashed", "요율독자")).id!!
    }

    @Test
    fun `관리자만 설정 - 창작자·독자는 403`() {
        listOf(member(creatorId, "CREATOR"), member(readerId, "READER")).forEach { auth ->
            mockMvc.post("/api/admin/fee-policies") {
                with(auth)
                json(contract("0.1000"))
            }.andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("COMMON_005") }
            }
        }
    }

    @Test
    fun `계약 요율 설정 → 창작자에게 CONTRACT로 보이고 이후 판매에 적용, 이전 판매는 기본 요율`() {
        val before = LocalDateTime.now(clock).minusMinutes(1)
        createContract("0.1000").andExpect {
            status { isCreated() }
            jsonPath("$.creatorId") { value(creatorId) }
            jsonPath("$.createdBy") { value(ADMIN_ID) }
        }

        mockMvc.get("/api/creator/settlements/fee-rates") { with(member(creatorId, "CREATOR")) }.andExpect {
            status { isOk() }
            jsonPath("$[?(@.revenueType == 'CONTENT')].source") { value("CONTRACT") }
            jsonPath("$[?(@.revenueType == 'CONTENT')].feeRate") { value(0.1) }
            jsonPath("$[?(@.revenueType == 'DONATION')].source") { value("DEFAULT") }
        }

        // 계약 이후 판매 10%, 계약 이전 판매(늦게 도착)는 15% 그대로
        assertThat(feeOf(10_000, LocalDateTime.now(clock).plusSeconds(1))).isEqualTo(1_000)
        assertThat(feeOf(10_000, before)).isEqualTo(1_500)
    }

    @Test
    fun `계약 종료 → 종료 시각부터 기본 요율, 두 번 종료 409, 기본 요율 종료 409`() {
        val policyId = id(createContract("0.1000"))
        val endAt = LocalDateTime.now(clock).plusHours(1)

        end(policyId, endAt).andExpect {
            status { isOk() }
            jsonPath("$.endedBy") { value(ADMIN_ID) }
        }
        assertThat(feeOf(10_000, endAt.minusMinutes(1))).isEqualTo(1_000)
        assertThat(feeOf(10_000, endAt.plusMinutes(1))).isEqualTo(1_500)

        end(policyId, null).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("SETTLEMENT_007") }
        }

        val defaultId = id(mockMvc.post("/api/admin/fee-policies") {
            with(admin())
            json("""{"revenueType":"DONATION","feeRate":"0.0400","reason":"후원 기본 인하"}""")
        })
        end(defaultId, null).andExpect { jsonPath("$.code") { value("SETTLEMENT_007") } }
    }

    @Test
    fun `검증 - 과거 적용 400, 요율 범위·자릿수 400, 창작자 아님 400`() {
        mockMvc.post("/api/admin/fee-policies") {
            with(admin())
            json(contract("0.1000", effectiveFrom = "2020-01-01T00:00:00"))
        }.andExpect { jsonPath("$.code") { value("SETTLEMENT_004") } }

        listOf("0.6000", "0.12345", "-0.0100").forEach { rate ->
            mockMvc.post("/api/admin/fee-policies") {
                with(admin())
                json(contract(rate))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("SETTLEMENT_003") }
            }
        }

        mockMvc.post("/api/admin/fee-policies") {
            with(admin())
            json(contract("0.1000", target = readerId))
        }.andExpect { jsonPath("$.code") { value("SETTLEMENT_005") } }
    }

    @Test
    fun `이력 조회 - 최신순`() {
        createContract("0.1000")
        createContract("0.0800")

        mockMvc.get("/api/admin/fee-policies?creatorId=$creatorId") { with(admin()) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].feeRate") { value(0.08) }
        }
        mockMvc.get("/api/admin/fee-policies/applied?creatorId=$creatorId") { with(admin()) }.andExpect {
            jsonPath("$[?(@.revenueType == 'CONTENT')].feeRate") { value(0.08) }
        }
    }

    private fun feeOf(price: Long, at: LocalDateTime): Long {
        val purchaseId = Random.nextLong(100_000_000, 999_999_999)
        salesRecordService.onPurchaseCompleted(
            PurchaseCompletedEvent(UUID.randomUUID().toString(), purchaseId, readerId, creatorId, "CONTENT", 1L, price, at),
        )
        return salesRecordRepository.findByPurchaseIdAndType(purchaseId, SalesType.SALE)!!.feeAmount
    }

    private fun createContract(rate: String) = mockMvc.post("/api/admin/fee-policies") {
        with(admin())
        json(contract(rate))
    }

    private fun end(policyId: Long, endAt: LocalDateTime?) = mockMvc.post("/api/admin/fee-policies/$policyId/end") {
        with(admin())
        json(if (endAt == null) """{"reason":"계약 종료"}""" else """{"endAt":"$endAt","reason":"계약 종료"}""")
    }

    private fun contract(rate: String, target: Long = creatorId, effectiveFrom: String? = null) =
        """{"creatorId":$target,"revenueType":"CONTENT","feeRate":"$rate","reason":"우수 창작자 계약"""" +
            (effectiveFrom?.let { ""","effectiveFrom":"$it"""" } ?: "") + "}"

    private fun id(result: org.springframework.test.web.servlet.ResultActionsDsl): Long =
        (jsonMapper.readValue(result.andReturn().response.contentAsString, Map::class.java)["id"] as Number).toLong()

    private fun MockHttpServletRequestDsl.json(body: String) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun member(memberId: Long, role: String) =
        jwt().jwt { it.subject(memberId.toString()) }.authorities(SimpleGrantedAuthority("ROLE_$role"))

    private fun admin() = member(ADMIN_ID, "ADMIN")

    companion object {
        private const val ADMIN_ID = 999_999L
    }
}
