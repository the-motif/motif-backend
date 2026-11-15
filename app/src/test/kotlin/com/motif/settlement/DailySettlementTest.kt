package com.motif.settlement

import com.motif.common.event.PurchaseCompletedEvent
import com.motif.common.event.PurchaseRefundedEvent
import com.motif.common.exception.BusinessException
import com.motif.settlement.domain.LedgerBucket
import com.motif.settlement.domain.LedgerType
import com.motif.settlement.domain.SalesType
import com.motif.settlement.repository.BalanceLedgerRepository
import com.motif.settlement.repository.CreatorBalanceRepository
import com.motif.settlement.repository.DailySettlementRepository
import com.motif.settlement.repository.SalesRecordRepository
import com.motif.settlement.service.DailySettlementJob
import com.motif.settlement.service.SalesRecordService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random

// 정산일은 과거 고정 (미래 날짜 불가), 창작자 ID는 무작위 → 로컬 DB 데이터와 안 섞임
@SpringBootTest
@Transactional
class DailySettlementTest @Autowired constructor(
    private val salesRecordService: SalesRecordService,
    private val salesRecordRepository: SalesRecordRepository,
    private val dailySettlementJob: DailySettlementJob,
    private val dailySettlementRepository: DailySettlementRepository,
    private val creatorBalanceRepository: CreatorBalanceRepository,
    private val balanceLedgerRepository: BalanceLedgerRepository,
    private val jdbc: JdbcClient,
) {

    private val creatorId = Random.nextLong(10_000_000, 99_999_999)
    private val day1 = LocalDate.of(2026, 3, 2)

    @Test
    fun `판매 이벤트 → 판매 원장, 같은 이벤트 두 번은 1행`() {
        val event = sale(nextId(), 3000, day1.atTime(10, 0))
        salesRecordService.onPurchaseCompleted(event)
        salesRecordService.onPurchaseCompleted(event)

        val record = salesRecordRepository.findByPurchaseIdAndType(event.purchaseId(), SalesType.SALE)!!
        assertThat(record.feeRate).isEqualByComparingTo("0.1500")
        assertThat(record.feeAmount).isEqualTo(450)
        assertThat(salesRecordRepository.existsByEventId(event.eventId())).isTrue()
        assertThat(salesRecordRepository.findAll().count { it.creatorId == creatorId }).isEqualTo(1)
    }

    @Test
    fun `일일 정산 - 수수료·보류금 계산, 잔액·원장 반영, 재실행해도 같은 결과`() {
        salesRecordService.onPurchaseCompleted(sale(nextId(), 3000, day1.atTime(10, 0)))
        salesRecordService.onPurchaseCompleted(sale(nextId(), 7000, day1.atTime(23, 59)))

        val result = dailySettlementJob.run(day1.plusDays(1))
        assertThat(result.failedCreatorIds).isEmpty()

        val settlement = settlementOn(day1.plusDays(1))
        assertThat(settlement.grossSales).isEqualTo(10_000)
        assertThat(settlement.feeAmount).isEqualTo(1_500)
        assertThat(settlement.netAmount).isEqualTo(8_500)
        assertThat(settlement.holdAmount).isEqualTo(850)
        assertThat(settlement.payableAmount).isEqualTo(7_650)
        assertThat(settlement.holdReleaseDate).isEqualTo(day1.plusDays(8))
        assertBalance(available = 7_650, held = 850)

        dailySettlementJob.run(day1.plusDays(1))
        assertBalance(available = 7_650, held = 850)
        assertThat(ledgers().map { it.type }).containsExactly(LedgerType.HOLD, LedgerType.DAILY_SETTLEMENT)
    }

    @Test
    fun `0시 이후 판매는 다음 정산, 정산 뒤 늦게 도착한 어제 판매도 다음 정산`() {
        salesRecordService.onPurchaseCompleted(sale(nextId(), 1000, day1.atTime(9, 0)))
        salesRecordService.onPurchaseCompleted(sale(nextId(), 2000, day1.plusDays(1).atTime(0, 0)))
        dailySettlementJob.run(day1.plusDays(1))

        salesRecordService.onPurchaseCompleted(sale(nextId(), 4000, day1.atTime(15, 0)))
        dailySettlementJob.run(day1.plusDays(1))
        assertThat(settlementOn(day1.plusDays(1)).grossSales).isEqualTo(1000)

        dailySettlementJob.run(day1.plusDays(2))
        assertThat(settlementOn(day1.plusDays(2)).grossSales).isEqualTo(6000)
    }

    @Test
    fun `환불이 많은 날은 마이너스 이월 → 다음 날 판매에서 차감`() {
        val purchaseId = nextId()
        salesRecordService.onPurchaseCompleted(sale(purchaseId, 3000, day1.atTime(10, 0)))
        dailySettlementJob.run(day1.plusDays(1))
        // net 2550, hold 255, payable 2295
        assertBalance(available = 2_295, held = 255)

        salesRecordService.onPurchaseRefunded(refund(purchaseId, day1.plusDays(1).atTime(12, 0)))
        dailySettlementJob.run(day1.plusDays(2))
        val minus = settlementOn(day1.plusDays(2))
        assertThat(minus.refundAmount).isEqualTo(3000)
        assertThat(minus.feeAmount).isEqualTo(-450)
        assertThat(minus.payableAmount).isEqualTo(-2550)
        assertBalance(available = 2_295, held = 255)

        salesRecordService.onPurchaseCompleted(sale(nextId(), 5000, day1.plusDays(2).atTime(10, 0)))
        dailySettlementJob.run(day1.plusDays(3))
        val next = settlementOn(day1.plusDays(3))
        // 4250 - 425 - 2550
        assertThat(next.carryOverAmount).isEqualTo(-2550)
        assertThat(next.payableAmount).isEqualTo(1_275)
        assertBalance(available = 2_295 + 1_275, held = 255 + 425)
    }

    @Test
    fun `보류 기간이 지나면 보류금 해제 → 출금 가능 금액으로`() {
        salesRecordService.onPurchaseCompleted(sale(nextId(), 10_000, day1.atTime(10, 0)))
        dailySettlementJob.run(day1.plusDays(1))
        assertBalance(available = 7_650, held = 850)

        dailySettlementJob.run(day1.plusDays(8))
        val release = settlementOn(day1.plusDays(8))
        assertThat(release.holdReleaseAmount).isEqualTo(850)
        assertThat(release.payableAmount).isEqualTo(850)
        assertBalance(available = 8_500, held = 0)

        val releasedAt = jdbc.sql("SELECT hold_released_at FROM daily_settlement WHERE creator_id = ? AND settlement_date = ?")
            .params(creatorId, day1.plusDays(1))
            .query(LocalDateTime::class.java)
            .single()
        assertThat(releasedAt).isNotNull()

        // 같은 보류금 두 번 안 풀림
        dailySettlementJob.run(day1.plusDays(9))
        assertBalance(available = 8_500, held = 0)
    }

    @Test
    fun `미래 날짜 정산은 SETTLEMENT_002`() {
        assertThrows<BusinessException> {
            dailySettlementJob.run(LocalDate.now().plusDays(2))
        }.also { assertThat(it.errorCode.code).isEqualTo("SETTLEMENT_002") }
    }

    private fun assertBalance(available: Long, held: Long) {
        val balance = creatorBalanceRepository.findByCreatorId(creatorId)!!
        assertThat(balance.availableAmount).isEqualTo(available)
        assertThat(balance.heldAmount).isEqualTo(held)
        // 원장 합계 = 잔액
        val ledgers = ledgers()
        assertThat(ledgers.filter { it.bucket == LedgerBucket.AVAILABLE }.sumOf { it.amount }).isEqualTo(available)
        assertThat(ledgers.filter { it.bucket == LedgerBucket.HELD }.sumOf { it.amount }).isEqualTo(held)
    }

    private fun ledgers() = balanceLedgerRepository.findAllByCreatorIdOrderByIdAsc(creatorId)

    private fun settlementOn(date: LocalDate) = dailySettlementRepository.findBySettlementDateAndCreatorId(date, creatorId)!!

    private fun nextId() = Random.nextLong(100_000_000, 999_999_999)

    private fun sale(purchaseId: Long, price: Long, at: LocalDateTime) = PurchaseCompletedEvent(
        UUID.randomUUID().toString(), purchaseId, 1L, creatorId, "CONTENT", 1L, price, at,
    )

    private fun refund(purchaseId: Long, at: LocalDateTime) = PurchaseRefundedEvent(
        UUID.randomUUID().toString(), purchaseId, 1L, creatorId, "CONTENT", 1L, 0L, at,
    )
}
