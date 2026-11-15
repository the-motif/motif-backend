package com.motif.settlement.service

import com.motif.common.exception.BusinessException
import com.motif.common.lock.RedisDistributedLock
import com.motif.settlement.exception.SettlementErrorCode
import com.motif.settlement.repository.SalesSummary
import com.motif.settlement.repository.SettlementJdbcRepository
import com.motif.common.trace.TraceId
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

// 일일 정산 / 정산일 D = D 0시 전에 일어난 미정산 판매·환불
// 순서: 락 → 판매 묶기(SQL) → 집계(SQL) → 창작자별 계산·저장(트랜잭션 각각)
// 대사 미해결 창작자는 판매 묶기·정산 모두 건너뜀 (보류금 해제·이월은 다음 정산에서)
@Service
class DailySettlementJob(
    private val distributedLock: RedisDistributedLock,
    private val settlementJdbcRepository: SettlementJdbcRepository,
    private val creatorSettlementWriter: CreatorSettlementWriter,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${motif.settlement.daily-cron:0 10 0 * * *}", zone = "Asia/Seoul")
    fun scheduledRun() {
        try {
            TraceId.run("settle") { run(LocalDate.now(clock)) }
        } catch (e: BusinessException) {
            log.info("일일 정산 건너뜀: {}", e.message)
        }
    }

    fun run(settlementDate: LocalDate): DailySettlementRunResult {
        if (settlementDate.isAfter(LocalDate.now(clock))) {
            throw BusinessException(SettlementErrorCode.FUTURE_SETTLEMENT_DATE)
        }
        val lockKey = "settlement:daily:$settlementDate"
        val token = distributedLock.tryLock(lockKey, LOCK_LEASE)
            ?: throw BusinessException(SettlementErrorCode.SETTLEMENT_RUNNING)
        try {
            return execute(settlementDate)
        } finally {
            distributedLock.unlock(lockKey, token)
        }
    }

    private fun execute(settlementDate: LocalDate): DailySettlementRunResult {
        val now = LocalDateTime.now(clock)
        val held = settlementJdbcRepository.creatorsOnHold()
        val assigned = settlementJdbcRepository.assignUnsettled(settlementDate, now)
        val sales = settlementJdbcRepository.salesSummaries(settlementDate)
        val creators = sales.keys +
            settlementJdbcRepository.creatorsWithReleasableHold(settlementDate) +
            settlementJdbcRepository.creatorsWithNegativeCarry(settlementDate) -
            held
        val holdPolicy = settlementJdbcRepository.holdPolicy(now)

        var settled = 0
        var skipped = 0
        val failed = mutableListOf<Long>()
        for (creatorId in creators.sorted()) {
            try {
                val summary = sales[creatorId] ?: SalesSummary.empty(creatorId)
                if (creatorSettlementWriter.settle(settlementDate, summary, holdPolicy)) settled++ else skipped++
            } catch (e: Exception) {
                // 실패한 창작자는 같은 날짜로 재실행하면 다시 처리
                log.error("창작자 정산 실패 date={} creatorId={}", settlementDate, creatorId, e)
                failed += creatorId
            }
        }
        log.info(
            "일일 정산 date={} 판매묶음={} 정산={} 건너뜀={} 실패={} 대사보류={}",
            settlementDate, assigned, settled, skipped, failed.size, held.size,
        )
        return DailySettlementRunResult(settlementDate, assigned, settled, skipped, failed, held.sorted())
    }

    companion object {
        private val LOCK_LEASE: Duration = Duration.ofMinutes(30)
    }
}

data class DailySettlementRunResult(
    val settlementDate: LocalDate,
    val assignedSalesCount: Int,
    val settledCreatorCount: Int,
    val skippedCreatorCount: Int,
    val failedCreatorIds: List<Long>,
    val heldCreatorIds: List<Long> = emptyList(),
)
