package com.motif.settlement.service

import com.motif.settlement.domain.BalanceLedger
import com.motif.settlement.domain.DailySettlement
import com.motif.settlement.domain.LedgerBucket
import com.motif.settlement.domain.LedgerType
import com.motif.settlement.domain.SettlementCalculator
import com.motif.settlement.domain.SettlementInput
import com.motif.settlement.repository.BalanceLedgerRepository
import com.motif.settlement.repository.CreatorBalanceRepository
import com.motif.settlement.repository.DailySettlementRepository
import com.motif.settlement.repository.HoldPolicy
import com.motif.settlement.repository.SalesSummary
import com.motif.settlement.repository.SettlementJdbcRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

// 창작자 1명 정산 = 트랜잭션 1개 / 한 명 실패해도 다른 창작자는 진행
@Component
class CreatorSettlementWriter(
    private val settlementJdbcRepository: SettlementJdbcRepository,
    private val dailySettlementRepository: DailySettlementRepository,
    private val creatorBalanceRepository: CreatorBalanceRepository,
    private val balanceLedgerRepository: BalanceLedgerRepository,
    private val clock: Clock,
) {

    // 이미 정산했으면 false (재실행 안전)
    @Transactional
    fun settle(settlementDate: LocalDate, sales: SalesSummary, holdPolicy: HoldPolicy): Boolean {
        val creatorId = sales.creatorId
        val now = LocalDateTime.now(clock)
        creatorBalanceRepository.insertIfAbsent(creatorId, now)
        // 잔액 행 잠금 = 같은 창작자 동시 정산·출금 직렬화
        val balance = creatorBalanceRepository.findByCreatorIdForUpdate(creatorId)!!
        if (dailySettlementRepository.existsBySettlementDateAndCreatorId(settlementDate, creatorId)) {
            return false
        }

        val release = settlementJdbcRepository.releaseHolds(creatorId, settlementDate, now)
        val carryOver = settlementJdbcRepository.carryOver(creatorId, settlementDate)
        val result = SettlementCalculator.calculate(
            SettlementInput(
                grossSales = sales.grossSales,
                refundAmount = sales.refundAmount,
                feeAmount = sales.feeAmount,
                holdRate = holdPolicy.holdRate,
                holdReleaseAmount = release,
                carryOverAmount = carryOver,
            ),
        )
        val settlement = dailySettlementRepository.save(
            DailySettlement(
                settlementDate = settlementDate,
                creatorId = creatorId,
                grossSales = sales.grossSales,
                refundAmount = sales.refundAmount,
                feeAmount = sales.feeAmount,
                result = result,
                holdReleaseDate = settlementDate.plusDays(holdPolicy.holdDays.toLong()),
                holdReleaseAmount = release,
                carryOverAmount = carryOver,
            ),
        )
        val ref = settlement.id!!

        // 풀린 보류금은 payable에 포함 → HELD에서만 빼고 AVAILABLE은 DAILY_SETTLEMENT 한 줄로
        val ledgers = buildList<BalanceLedger> {
            if (result.holdAmount > 0) add(balance.apply(LedgerBucket.HELD, LedgerType.HOLD, result.holdAmount, ref))
            if (release > 0) add(balance.apply(LedgerBucket.HELD, LedgerType.HOLD_RELEASE, -release, ref))
            if (result.availableIncrease > 0) {
                add(balance.apply(LedgerBucket.AVAILABLE, LedgerType.DAILY_SETTLEMENT, result.availableIncrease, ref))
            }
        }
        balanceLedgerRepository.saveAll(ledgers)
        return true
    }
}
