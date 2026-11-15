package com.motif.payment

import com.motif.common.event.PurchaseCompletedEvent
import com.motif.content.api.ContentPurchaseApi
import com.motif.member.domain.Member
import com.motif.member.repository.MemberRepository
import com.motif.payment.outbox.OutboxRelay
import com.motif.payment.repository.OutboxEventRepository
import com.motif.payment.repository.PointLotRepository
import com.motif.payment.service.PurchaseService
import com.motif.settlement.domain.SalesType
import com.motif.settlement.repository.SalesRecordRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class PaymentApiTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val memberRepository: MemberRepository,
    private val contentPurchaseApi: ContentPurchaseApi,
    private val lotRepository: PointLotRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val outboxRelay: OutboxRelay,
    private val salesRecordRepository: SalesRecordRepository,
    private val jsonMapper: JsonMapper,
) {

    @Autowired
    private lateinit var events: ApplicationEvents

    private var creatorId = 0L
    private var readerId = 0L
    private var contentId = 0L

    @BeforeEach
    fun setUp() {
        creatorId = memberRepository.save(Member("pay-writer@motif.test", "hashed", "결제작가")
            .apply { promoteToCreator() }).id!!
        readerId = memberRepository.save(Member("pay-reader@motif.test", "hashed", "결제독자")).id!!
        contentId = publishedPaidContent(price = 3000)
    }

    @Test
    fun `충전 - 요청 → 승인 → 잔액·lot·내역 반영, 같은 승인 재요청은 같은 결과`() {
        val orderNo = requestCharge(10_000)
        confirm(orderNo, "pk-ok-1", 10_000).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("PAID") }
        }
        confirm(orderNo, "pk-ok-1", 10_000).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("PAID") }
        }

        mockMvc.get("/api/points/me") { with(reader()) }.andExpect {
            jsonPath("$.paidBalance") { value(10_000) }
            jsonPath("$.totalBalance") { value(10_000) }
        }
        mockMvc.get("/api/points/me/history") { with(reader()) }.andExpect {
            jsonPath("$.content.length()") { value(1) }
            jsonPath("$.content[0].type") { value("CHARGE") }
            jsonPath("$.content[0].balanceAfter") { value(10_000) }
        }
    }

    @Test
    fun `충전 - 같은 멱등 키 재요청은 같은 주문, 금액 다르면 422, 범위 밖 400`() {
        val key = UUID.randomUUID().toString()
        val first = orderNo(chargeRequest(5_000, key))
        val second = orderNo(chargeRequest(5_000, key))
        assertThat(second).isEqualTo(first)

        chargeRequest(6_000, key).andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("PAYMENT_010") }
        }
        chargeRequest(1_050).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("PAYMENT_001") }
        }
    }

    @Test
    fun `충전 승인 - 금액 위변조 400, PG 실패 502 후 FAILED라 재승인 409`() {
        val orderNo = requestCharge(5_000)
        confirm(orderNo, "pk-ok", 50_000).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("PAYMENT_003") }
        }
        confirm(orderNo, "fail-key", 5_000).andExpect {
            status { isBadGateway() }
            jsonPath("$.code") { value("PAYMENT_004") }
        }
        confirm(orderNo, "pk-ok", 5_000).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PAYMENT_011") }
        }
    }

    @Test
    fun `구매 - 잔액 차감 + 열람 권한 + outbox, 같은 키 재요청은 같은 구매`() {
        charge(10_000)
        val key = UUID.randomUUID().toString()
        val purchaseId = purchaseId(purchase(contentId, key).andExpect {
            status { isCreated() }
            jsonPath("$.listPrice") { value(3000) }
            jsonPath("$.paidPointAmount") { value(3000) }
            jsonPath("$.sellerId") { value(creatorId) }
        })
        assertThat(purchaseId(purchase(contentId, key))).isEqualTo(purchaseId)

        mockMvc.get("/api/points/me") { with(reader()) }.andExpect {
            jsonPath("$.paidBalance") { value(7_000) }
        }
        assertThat(contentPurchaseApi.hasAccess(readerId, contentId)).isTrue()

        val outbox = outboxEventRepository.findAllByAggregateTypeAndAggregateId(PurchaseService.AGGREGATE_PURCHASE, purchaseId)
        assertThat(outbox).hasSize(1)
        assertThat(outbox[0].eventType).isEqualTo(PurchaseCompletedEvent.TYPE)

        outboxRelay.relayPending()
        val published = events.stream(PurchaseCompletedEvent::class.java).toList()
        assertThat(published.map { it.purchaseId }).contains(purchaseId)
        assertThat(outboxEventRepository.findById(outbox[0].id!!).get().status.name).isEqualTo("PUBLISHED")

        // settlement가 같은 트랜잭션에서 판매 원장에 기록
        val sale = salesRecordRepository.findByPurchaseIdAndType(purchaseId, SalesType.SALE)!!
        assertThat(sale.creatorId).isEqualTo(creatorId)
        assertThat(sale.feeAmount).isEqualTo(450)
    }

    @Test
    fun `구매 - 잔액 부족 409, 본인 글 400, 이미 소유 409`() {
        charge(1_000)
        purchase(contentId).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PAYMENT_005") }
        }
        mockMvc.post("/api/purchases") {
            with(member(creatorId, "CREATOR"))
            header("Idempotency-Key", UUID.randomUUID().toString())
            json("""{"contentId":$contentId}""")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("PAYMENT_007") }
        }

        charge(10_000)
        purchase(contentId).andExpect { status { isCreated() } }
        purchase(contentId).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CONTENT_009") }
        }
    }

    @Test
    fun `lot - 만료 임박 순으로 나눠 차감`() {
        charge(2_000)
        charge(2_000)
        purchase(contentId).andExpect { status { isCreated() } }

        val remains = lotRepository.findAll().filter { it.sourceType.name == "CHARGE" }
            .sortedBy { it.id }.takeLast(2).map { it.remainingAmount }
        assertThat(remains).containsExactly(0L, 1_000L)
    }

    @Test
    fun `환불 - 열람 전이면 잔액·lot 복구 + 권한 회수, 두 번째는 409`() {
        charge(5_000)
        val purchaseId = purchaseId(purchase(contentId))

        refund(purchaseId).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("REFUNDED") }
        }
        mockMvc.get("/api/points/me") { with(reader()) }.andExpect {
            jsonPath("$.paidBalance") { value(5_000) }
        }
        assertThat(contentPurchaseApi.hasAccess(readerId, contentId)).isFalse()
        assertThat(outboxEventRepository.findAllByAggregateTypeAndAggregateId(PurchaseService.AGGREGATE_PURCHASE, purchaseId))
            .hasSize(2)

        refund(purchaseId).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PAYMENT_009") }
        }
    }

    @Test
    fun `환불 - 열람했으면 409, 남의 구매는 404`() {
        charge(5_000)
        val purchaseId = purchaseId(purchase(contentId))
        mockMvc.get("/api/contents/$contentId") { with(reader()) }.andExpect {
            jsonPath("$.accessible") { value(true) }
        }

        refund(purchaseId).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("PAYMENT_009") }
        }
        mockMvc.post("/api/purchases/$purchaseId/refund") {
            with(member(creatorId, "CREATOR"))
            json("""{"reason":"남의 것"}""")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("PAYMENT_008") }
        }
    }

    @Test
    fun `비로그인 401, 멱등 키 헤더 없으면 400`() {
        mockMvc.get("/api/points/me").andExpect { status { isUnauthorized() } }
        mockMvc.post("/api/purchases") {
            with(reader())
            json("""{"contentId":$contentId}""")
        }.andExpect { status { isBadRequest() } }
    }

    private fun charge(amount: Long) {
        val orderNo = requestCharge(amount)
        confirm(orderNo, "pk-" + UUID.randomUUID(), amount).andExpect { status { isOk() } }
    }

    private fun requestCharge(amount: Long) = orderNo(chargeRequest(amount).andExpect { status { isCreated() } })

    private fun chargeRequest(amount: Long, key: String = UUID.randomUUID().toString()) = mockMvc.post("/api/charges") {
        with(reader())
        header("Idempotency-Key", key)
        json("""{"amount":$amount}""")
    }

    private fun confirm(orderNo: String, paymentKey: String, amount: Long) = mockMvc.post("/api/charges/confirm") {
        with(reader())
        json("""{"orderNo":"$orderNo","paymentKey":"$paymentKey","amount":$amount}""")
    }

    private fun purchase(contentId: Long, key: String = UUID.randomUUID().toString()) = mockMvc.post("/api/purchases") {
        with(reader())
        header("Idempotency-Key", key)
        json("""{"contentId":$contentId}""")
    }

    private fun refund(purchaseId: Long) = mockMvc.post("/api/purchases/$purchaseId/refund") {
        with(reader())
        json("""{"reason":"잘못 구매"}""")
    }

    private fun publishedPaidContent(price: Long): Long {
        val categoryId = field(mockMvc.post("/api/admin/categories") {
            with(member(999_999L, "ADMIN"))
            json("""{"name":"결제테스트","sortOrder":0}""")
        }, "id").toLong()
        val id = field(mockMvc.post("/api/creator/contents") {
            with(member(creatorId, "CREATOR"))
            json(
                """{"categoryId":$categoryId,"title":"유료 글","preview":"앞","accessType":"PAID",
                   "price":$price,"body":"본문"}""",
            )
        }, "id").toLong()
        mockMvc.post("/api/creator/contents/$id/publish") { with(member(creatorId, "CREATOR")) }
        return id
    }

    private fun orderNo(result: ResultActionsDsl) = field(result, "orderNo")

    private fun purchaseId(result: ResultActionsDsl) = field(result, "purchaseId").toLong()

    private fun field(result: ResultActionsDsl, name: String): String {
        val body = result.andReturn().response.contentAsString
        return jsonMapper.readValue(body, Map::class.java)[name].toString()
    }

    private fun MockHttpServletRequestDsl.json(body: String) {
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun reader() = member(readerId, "READER")

    private fun member(memberId: Long, role: String) =
        jwt().jwt { it.subject(memberId.toString()) }.authorities(SimpleGrantedAuthority("ROLE_$role"))
}
