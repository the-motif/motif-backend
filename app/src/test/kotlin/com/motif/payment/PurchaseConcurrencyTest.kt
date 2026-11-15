package com.motif.payment

import com.motif.common.exception.BusinessException
import com.motif.member.domain.Member
import com.motif.member.repository.MemberRepository
import com.motif.payment.pg.PgApproval
import com.motif.payment.repository.PointWalletRepository
import com.motif.payment.service.ChargeService
import com.motif.payment.service.PurchaseService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// 트랜잭션 없는 테스트 (스레드마다 실제 커밋) → 끝나면 직접 삭제
// Redis 락을 거치지 않고 PurchaseService를 바로 호출 → DB 조건부 UPDATE만으로 막히는지 확인
@SpringBootTest(properties = ["motif.outbox.relay-delay-ms=3600000"])
@AutoConfigureMockMvc
class PurchaseConcurrencyTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val memberRepository: MemberRepository,
    private val chargeService: ChargeService,
    private val purchaseService: PurchaseService,
    private val walletRepository: PointWalletRepository,
    private val jsonMapper: JsonMapper,
    private val jdbc: JdbcClient,
) {

    private val suffix = UUID.randomUUID().toString().take(8)
    private var creatorId = 0L
    private var readerId = 0L
    private var categoryId = 0L

    @Test
    fun `잔액 3,000으로 1,000원 글 10개 동시 구매 → 정확히 3개 성공, 잔액 0, 원장 합 일치`() {
        creatorId = memberRepository.save(Member("cc-$suffix@motif.test", "hashed", "동시작가$suffix").apply { promoteToCreator() }).id!!
        readerId = memberRepository.save(Member("cr-$suffix@motif.test", "hashed", "동시독자$suffix")).id!!
        categoryId = createCategory()
        val contentIds = (1..10).map { publishedContent(price = 1000) }

        val order = chargeService.createOrder(readerId, 3000, UUID.randomUUID().toString())
        chargeService.complete(order.orderNo, "FAKE", PgApproval("pk-$suffix", "CARD", LocalDateTime.now()))

        val success = Collections.synchronizedList(mutableListOf<Long>())
        val failures = Collections.synchronizedList(mutableListOf<String>())
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(10)
        contentIds.forEach { contentId ->
            pool.submit {
                start.await()
                try {
                    success += purchaseService.purchaseContent(readerId, contentId, UUID.randomUUID().toString()).id!!
                } catch (e: BusinessException) {
                    failures += e.errorCode.code
                } catch (e: Exception) {
                    failures += e.javaClass.simpleName
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue()

        assertThat(success).hasSize(3)
        assertThat(failures).hasSize(7).containsOnly("PAYMENT_005")

        val wallet = walletRepository.findByMemberId(readerId)!!
        assertThat(wallet.paidBalance).isZero()
        val ledgerSum = jdbc.sql("SELECT COALESCE(SUM(amount), 0) FROM point_transaction WHERE wallet_id = ?")
            .param(wallet.id).query(Long::class.java).single()
        assertThat(ledgerSum).isZero()
        val lotRemain = jdbc.sql("SELECT COALESCE(SUM(remaining_amount), 0) FROM point_lot WHERE wallet_id = ?")
            .param(wallet.id).query(Long::class.java).single()
        assertThat(lotRemain).isZero()
    }

    @AfterEach
    fun cleanUp() {
        val walletId = jdbc.sql("SELECT id FROM point_wallet WHERE member_id = ?").param(readerId)
            .query(Long::class.java).optional().orElse(null)
        if (walletId != null) {
            jdbc.sql("DELETE FROM point_transaction WHERE wallet_id = ?").param(walletId).update()
            jdbc.sql("DELETE u FROM point_lot_usage u JOIN point_lot l ON u.lot_id = l.id WHERE l.wallet_id = ?").param(walletId).update()
            jdbc.sql("DELETE FROM point_lot WHERE wallet_id = ?").param(walletId).update()
        }
        jdbc.sql("DELETE FROM outbox_event WHERE aggregate_type = 'PURCHASE' AND aggregate_id IN (SELECT id FROM purchase WHERE buyer_id = ?)")
            .param(readerId).update()
        jdbc.sql("DELETE FROM purchase WHERE buyer_id = ?").param(readerId).update()
        jdbc.sql("DELETE FROM charge_order WHERE member_id = ?").param(readerId).update()
        jdbc.sql("DELETE FROM point_wallet WHERE member_id = ?").param(readerId).update()
        jdbc.sql("DELETE FROM content_access WHERE member_id = ?").param(readerId).update()
        jdbc.sql("DELETE b FROM content_body b JOIN content c ON b.content_id = c.id WHERE c.creator_id = ?").param(creatorId).update()
        jdbc.sql("DELETE FROM content WHERE creator_id = ?").param(creatorId).update()
        jdbc.sql("DELETE FROM category WHERE id = ?").param(categoryId).update()
        jdbc.sql("DELETE FROM member WHERE id IN (?, ?)").params(creatorId, readerId).update()
    }

    private fun createCategory(): Long = id(mockMvc.post("/api/admin/categories") {
        with(member(999_999L, "ADMIN"))
        contentType = MediaType.APPLICATION_JSON
        content = """{"name":"동시성$suffix","sortOrder":0}"""
    }.andReturn().response.contentAsString)

    private fun publishedContent(price: Long): Long {
        val id = id(mockMvc.post("/api/creator/contents") {
            with(member(creatorId, "CREATOR"))
            contentType = MediaType.APPLICATION_JSON
            content = """{"categoryId":$categoryId,"title":"글","preview":"앞","accessType":"PAID","price":$price,"body":"본문"}"""
        }.andReturn().response.contentAsString)
        mockMvc.post("/api/creator/contents/$id/publish") { with(member(creatorId, "CREATOR")) }
        return id
    }

    private fun id(body: String) = (jsonMapper.readValue(body, Map::class.java)["id"] as Number).toLong()

    private fun member(memberId: Long, role: String) =
        jwt().jwt { it.subject(memberId.toString()) }.authorities(SimpleGrantedAuthority("ROLE_$role"))
}
