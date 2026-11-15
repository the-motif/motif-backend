package com.motif.settlement

import com.motif.member.domain.Member
import com.motif.member.repository.MemberRepository
import com.motif.settlement.domain.LedgerBucket
import com.motif.settlement.domain.LedgerType
import com.motif.settlement.repository.BalanceLedgerRepository
import com.motif.settlement.repository.CreatorBalanceRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WithdrawalApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val memberRepository: MemberRepository,
    private val balanceRepository: CreatorBalanceRepository,
    private val ledgerRepository: BalanceLedgerRepository,
    private val jsonMapper: JsonMapper,
    private val jdbc: JdbcClient,
) {

    private var creatorId = 0L

    @BeforeEach
    fun setUp() {
        creatorId = memberRepository.save(Member("wd-c@motif.test", "hashed", "출금작가").apply { promoteToCreator() }).id!!
        giveAvailable(100_000)
    }

    @Test
    fun `정산 정보 등록 → 번호는 뒤 4자리만, 관리자 확인 전엔 출금 409`() {
        registerAccount("12345678901234").andExpect {
            status { isOk() }
            jsonPath("$.account.accountNumber") { value("**********1234") }
            jsonPath("$.account.verifyStatus") { value("PENDING") }
            jsonPath("$.withdrawable") { value(false) }
        }
        registerTax().andExpect {
            jsonPath("$.taxProfile.idNumber") { value("*********4567") }
        }

        withdraw(20_000).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("SETTLEMENT_008") }
        }

        review("settlement-account")
        review("tax-profile").andExpect { jsonPath("$.withdrawable") { value(true) } }
    }

    @Test
    fun `계좌번호·주민번호는 DB에 암호문으로 저장`() {
        registerAccount("12345678901234")
        registerTax()

        val storedAccount = jdbc.sql("SELECT account_number FROM settlement_account WHERE creator_id = ?")
            .param(creatorId).query(String::class.java).single()
        val storedResident = jdbc.sql("SELECT resident_no_enc FROM tax_profile WHERE creator_id = ?")
            .param(creatorId).query(String::class.java).single()
        assertThat(storedAccount).doesNotContain("12345678901234")
        assertThat(storedResident).doesNotContain("9001011234567")
    }

    @Test
    fun `세금 정보 - 개인인데 주민번호 없으면 400`() {
        mockMvc.put("/api/creator/tax-profile") {
            with(creator())
            json("""{"creatorType":"INDIVIDUAL","legalName":"홍길동"}""")
        }.andExpect { jsonPath("$.code") { value("SETTLEMENT_016") } }
    }

    @Test
    fun `출금 신청 → 즉시 차감, 같은 키는 같은 신청, 부족·최소 금액 검증`() {
        verified("12345678901234")
        val key = UUID.randomUUID().toString()
        val id = id(withdraw(30_000, key).andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("REQUESTED") }
        })
        assertThat(id(withdraw(30_000, key))).isEqualTo(id)
        assertAvailable(70_000)

        withdraw(80_000).andExpect { jsonPath("$.code") { value("SETTLEMENT_009") } }
        withdraw(9_999).andExpect { jsonPath("$.code") { value("SETTLEMENT_010") } }
        withdraw(40_000, key).andExpect { jsonPath("$.code") { value("SETTLEMENT_014") } }
    }

    @Test
    fun `승인 → 개인 3·3% 원천징수 후 송금 완료, 다시 승인 409`() {
        verified("12345678901234")
        val id = id(withdraw(100_000))

        approve(id).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("PAID") }
            jsonPath("$.payout.grossAmount") { value(100_000) }
            jsonPath("$.payout.incomeTax") { value(3_000) }
            jsonPath("$.payout.localTax") { value(300) }
            jsonPath("$.payout.netAmount") { value(96_700) }
            jsonPath("$.payout.status") { value("COMPLETED") }
        }
        assertAvailable(0)
        approve(id).andExpect { jsonPath("$.code") { value("SETTLEMENT_012") } }
    }

    @Test
    fun `반려 → 출금 가능 금액 복구`() {
        verified("12345678901234")
        val id = id(withdraw(50_000))

        mockMvc.post("/api/admin/withdrawals/$id/reject") {
            with(admin())
            json("""{"reason":"계좌 확인 필요"}""")
        }.andExpect {
            jsonPath("$.status") { value("REJECTED") }
            jsonPath("$.rejectReason") { value("계좌 확인 필요") }
        }
        assertAvailable(100_000)
        assertThat(ledgerTypes()).containsExactly(LedgerType.ADJUSTMENT, LedgerType.WITHDRAWAL, LedgerType.WITHDRAWAL_REJECT)
    }

    @Test
    fun `송금 최종 실패 → FAILED + 금액 복구`() {
        verified("9990000000")
        val id = id(withdraw(50_000))

        approve(id).andExpect {
            jsonPath("$.status") { value("FAILED") }
            jsonPath("$.payout.status") { value("FAILED") }
            jsonPath("$.payout.failReason") { value("모의 은행: 계좌 없음") }
        }
        assertAvailable(100_000)
        assertThat(ledgerTypes().last()).isEqualTo(LedgerType.PAYOUT_FAIL)
    }

    @Test
    fun `송금 일시 오류 → PENDING 유지(복구 안 함), 재시도도 PENDING`() {
        verified("9980000000")
        val id = id(withdraw(50_000))

        val payoutId = (map(approve(id).andExpect {
            jsonPath("$.status") { value("APPROVED") }
            jsonPath("$.payout.status") { value("PENDING") }
        })["payout"] as Map<*, *>)["payoutId"]
        assertAvailable(50_000)

        mockMvc.post("/api/admin/payouts/$payoutId/retry") { with(admin()) }.andExpect {
            jsonPath("$.status") { value("PENDING") }
        }
    }

    @Test
    fun `독자는 창작자 출금 API 403`() {
        mockMvc.get("/api/creator/withdrawals") {
            with(jwt().jwt { it.subject("1") }.authorities(SimpleGrantedAuthority("ROLE_READER")))
        }.andExpect { status { isForbidden() } }
    }

    private fun verified(accountNumber: String) {
        registerAccount(accountNumber)
        registerTax()
        review("settlement-account")
        review("tax-profile")
    }

    private fun registerAccount(number: String) = mockMvc.put("/api/creator/settlement-account") {
        with(creator())
        json("""{"bankCode":"004","accountNumber":"$number","holderName":"홍길동"}""")
    }

    private fun registerTax() = mockMvc.put("/api/creator/tax-profile") {
        with(creator())
        json("""{"creatorType":"INDIVIDUAL","legalName":"홍길동","residentNo":"9001011234567"}""")
    }

    private fun review(target: String) = mockMvc.post("/api/admin/creators/$creatorId/$target/review") {
        with(admin())
        json("""{"approved":true}""")
    }

    private fun withdraw(amount: Long, key: String = UUID.randomUUID().toString()) = mockMvc.post("/api/creator/withdrawals") {
        with(creator())
        header("Idempotency-Key", key)
        json("""{"amount":$amount}""")
    }

    private fun approve(id: Long) = mockMvc.post("/api/admin/withdrawals/$id/approve") { with(admin()) }

    // 테스트용 잔액 / 관리자 보정(ADJUSTMENT) 원장으로
    private fun giveAvailable(amount: Long) {
        balanceRepository.insertIfAbsent(creatorId, LocalDateTime.now())
        val balance = balanceRepository.findByCreatorIdForUpdate(creatorId)!!
        ledgerRepository.save(balance.apply(LedgerBucket.AVAILABLE, LedgerType.ADJUSTMENT, amount, 0, "테스트"))
    }

    private fun assertAvailable(amount: Long) {
        assertThat(balanceRepository.findByCreatorId(creatorId)!!.availableAmount).isEqualTo(amount)
        assertThat(ledgerRepository.findAllByCreatorIdOrderByIdAsc(creatorId).filter { it.bucket == LedgerBucket.AVAILABLE }
            .sumOf { it.amount }).isEqualTo(amount)
    }

    private fun ledgerTypes() = ledgerRepository.findAllByCreatorIdOrderByIdAsc(creatorId).map { it.type }

    private fun map(result: ResultActionsDsl) = jsonMapper.readValue(result.andReturn().response.contentAsString, Map::class.java)

    private fun id(result: ResultActionsDsl) = (map(result)["withdrawalId"] as Number).toLong()

    private fun MockHttpServletRequestDsl.json(body: String) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun creator() = jwt().jwt { it.subject(creatorId.toString()) }.authorities(SimpleGrantedAuthority("ROLE_CREATOR"))

    private fun admin() = jwt().jwt { it.subject("999999") }.authorities(SimpleGrantedAuthority("ROLE_ADMIN"))
}
