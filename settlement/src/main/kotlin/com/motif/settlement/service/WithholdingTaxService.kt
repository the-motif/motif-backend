package com.motif.settlement.service

import com.motif.common.exception.BusinessException
import com.motif.common.lock.RedisDistributedLock
import com.motif.settlement.domain.CreatorType
import com.motif.settlement.exception.SettlementErrorCode
import com.motif.common.trace.TraceId
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// 원천세 월 집계 / 매월 1일 01:00 전월분, 지급 완료(COMPLETED) payout의 paid_at 기준
// 재실행 = 그 달 행 삭제 후 다시 집계 (한 트랜잭션) → 몇 번 돌려도 같은 결과
@Service
class WithholdingTaxJob(
    private val distributedLock: RedisDistributedLock,
    private val repository: WithholdingTaxRepository,
    private val clock: Clock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${motif.withholding.cron:0 0 1 1 * *}", zone = "Asia/Seoul")
    fun scheduledRun() {
        try {
            TraceId.run("withholding") { run(YearMonth.now(clock).minusMonths(1)) }
        } catch (e: BusinessException) {
            log.info("원천세 집계 건너뜀: {}", e.message)
        }
    }

    fun run(month: YearMonth): WithholdingTaxRunResult {
        if (!month.isBefore(YearMonth.now(clock))) {
            throw BusinessException(SettlementErrorCode.INVALID_TAX_MONTH)
        }
        val lockKey = "withholding:$month"
        val token = distributedLock.tryLock(lockKey, LOCK_LEASE)
            ?: throw BusinessException(SettlementErrorCode.WITHHOLDING_RUNNING)
        try {
            val rows = repository.rebuild(month, LocalDateTime.now(clock))
            log.info("원천세 집계 month={} 행={}", month, rows)
            return WithholdingTaxRunResult(month.toTaxMonth(), rows)
        } finally {
            distributedLock.unlock(lockKey, token)
        }
    }

    companion object {
        private val LOCK_LEASE: Duration = Duration.ofMinutes(10)
    }
}

@Repository
class WithholdingTaxRepository(
    private val jdbc: JdbcClient,
) {

    @Transactional
    fun rebuild(month: YearMonth, now: LocalDateTime): Int {
        val taxMonth = month.toTaxMonth()
        jdbc.sql("DELETE FROM withholding_tax_monthly WHERE tax_month = :month").param("month", taxMonth).update()
        return jdbc.sql(
            """
            INSERT INTO withholding_tax_monthly
                (tax_month, creator_id, creator_type, payout_count, gross_amount, income_tax, local_tax, created_at, updated_at)
            SELECT :month, creator_id, creator_type, COUNT(*), SUM(gross_amount), SUM(income_tax), SUM(local_tax), :now, :now
            FROM payout
            WHERE status = 'COMPLETED' AND paid_at >= :from AND paid_at < :to
            GROUP BY creator_id, creator_type
            """,
        )
            .param("month", taxMonth)
            .param("now", now)
            .param("from", month.atDay(1).atStartOfDay())
            .param("to", month.plusMonths(1).atDay(1).atStartOfDay())
            .update()
    }

    @Transactional(readOnly = true)
    fun findByMonth(month: YearMonth): List<WithholdingTaxRow> =
        jdbc.sql("SELECT * FROM withholding_tax_monthly WHERE tax_month = :month ORDER BY creator_id, creator_type")
            .param("month", month.toTaxMonth())
            .query(::mapRow)
            .list()

    @Transactional(readOnly = true)
    fun findByCreatorAndYear(creatorId: Long, year: Int): List<WithholdingTaxRow> =
        jdbc.sql(
            """
            SELECT * FROM withholding_tax_monthly
            WHERE creator_id = :creatorId AND tax_month BETWEEN :from AND :to
            ORDER BY tax_month, creator_type
            """,
        )
            .param("creatorId", creatorId)
            .param("from", "${year}01")
            .param("to", "${year}12")
            .query(::mapRow)
            .list()

    private fun mapRow(rs: java.sql.ResultSet, @Suppress("UNUSED_PARAMETER") rowNum: Int) = WithholdingTaxRow(
        taxMonth = rs.getString("tax_month"),
        creatorId = rs.getLong("creator_id"),
        creatorType = CreatorType.valueOf(rs.getString("creator_type")),
        payoutCount = rs.getInt("payout_count"),
        grossAmount = rs.getLong("gross_amount"),
        incomeTax = rs.getLong("income_tax"),
        localTax = rs.getLong("local_tax"),
    )
}

data class WithholdingTaxRow(
    val taxMonth: String,
    val creatorId: Long,
    val creatorType: CreatorType,
    val payoutCount: Int,
    val grossAmount: Long,
    val incomeTax: Long,
    val localTax: Long,
)

data class WithholdingTaxRunResult(val taxMonth: String, val rowCount: Int)

private val TAX_MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMM")

fun YearMonth.toTaxMonth(): String = format(TAX_MONTH_FORMAT)
