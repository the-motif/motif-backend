package com.motif.settlement.repository

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

// 정산 배치용 집계·대량 UPDATE / JPA 대신 SQL
@Repository
class SettlementJdbcRepository(
    private val jdbc: JdbcClient,
) {

    // 미정산 판매를 이번 정산일로 묶음 / 이미 정산 끝난 창작자는 제외 (재실행 때 늦은 이벤트가 끼지 않게)
    // 대사 미해결 창작자도 제외 → 해결 뒤 정산일에 포함
    fun assignUnsettled(settlementDate: LocalDate, now: LocalDateTime): Int =
        jdbc.sql(
            """
            UPDATE sales_record SET settlement_date = :date, updated_at = :now
            WHERE settlement_date IS NULL
              AND occurred_at < :cutoff
              AND creator_id NOT IN (SELECT creator_id FROM daily_settlement WHERE settlement_date = :date)
              AND creator_id NOT IN (SELECT creator_id FROM reconciliation_mismatch WHERE resolved_at IS NULL)
            """,
        )
            .param("date", settlementDate)
            .param("now", now)
            .param("cutoff", settlementDate.atStartOfDay())
            .update()

    fun salesSummaries(settlementDate: LocalDate): Map<Long, SalesSummary> =
        jdbc.sql(
            """
            SELECT creator_id,
                   COALESCE(SUM(CASE WHEN type = 'SALE' THEN list_price ELSE 0 END), 0)    AS gross,
                   COALESCE(SUM(CASE WHEN type = 'REFUND' THEN -list_price ELSE 0 END), 0) AS refund,
                   COALESCE(SUM(fee_amount), 0)                                           AS fee
            FROM sales_record
            WHERE settlement_date = :date
            GROUP BY creator_id
            """,
        )
            .param("date", settlementDate)
            .query { rs, _ ->
                SalesSummary(rs.getLong("creator_id"), rs.getLong("gross"), rs.getLong("refund"), rs.getLong("fee"))
            }
            .list()
            .associateBy { it.creatorId }

    // 대사 미해결 불일치가 있는 창작자 / 정산 보류
    fun creatorsOnHold(): Set<Long> =
        jdbc.sql("SELECT DISTINCT creator_id FROM reconciliation_mismatch WHERE resolved_at IS NULL")
            .query { rs, _ -> rs.getLong(1) }
            .set()

    // 오늘 풀 보류금이 있는 창작자
    fun creatorsWithReleasableHold(settlementDate: LocalDate): Set<Long> =
        jdbc.sql(
            """
            SELECT DISTINCT creator_id FROM daily_settlement
            WHERE hold_release_date <= :date AND hold_released_at IS NULL AND hold_amount > 0
              AND settlement_date < :date
            """,
        )
            .param("date", settlementDate)
            .query { rs, _ -> rs.getLong(1) }
            .set()

    // 직전 정산이 마이너스인 창작자
    fun creatorsWithNegativeCarry(settlementDate: LocalDate): Set<Long> =
        jdbc.sql(
            """
            SELECT d.creator_id FROM daily_settlement d
            WHERE d.payable_amount < 0
              AND d.settlement_date = (SELECT MAX(p.settlement_date) FROM daily_settlement p
                                       WHERE p.creator_id = d.creator_id AND p.settlement_date < :date)
            """,
        )
            .param("date", settlementDate)
            .query { rs, _ -> rs.getLong(1) }
            .set()

    // 창작자 트랜잭션 안에서 호출 / 풀 보류금 합계 후 해제 표시
    fun releaseHolds(creatorId: Long, settlementDate: LocalDate, now: LocalDateTime): Long {
        val amount = jdbc.sql(
            """
            SELECT COALESCE(SUM(hold_amount), 0) FROM daily_settlement
            WHERE creator_id = :creatorId AND hold_release_date <= :date AND hold_released_at IS NULL
              AND hold_amount > 0 AND settlement_date < :date
            FOR UPDATE
            """,
        )
            .param("creatorId", creatorId)
            .param("date", settlementDate)
            .query(Long::class.java)
            .single()
        if (amount > 0) {
            jdbc.sql(
                """
                UPDATE daily_settlement SET hold_released_at = :now, updated_at = :now
                WHERE creator_id = :creatorId AND hold_release_date <= :date AND hold_released_at IS NULL
                  AND hold_amount > 0 AND settlement_date < :date
                """,
            )
                .param("creatorId", creatorId)
                .param("date", settlementDate)
                .param("now", now)
                .update()
        }
        return amount
    }

    // 직전 정산의 마이너스 금액 / 없으면 0
    fun carryOver(creatorId: Long, settlementDate: LocalDate): Long =
        jdbc.sql(
            """
            SELECT payable_amount FROM daily_settlement
            WHERE creator_id = :creatorId AND settlement_date < :date
            ORDER BY settlement_date DESC LIMIT 1
            """,
        )
            .param("creatorId", creatorId)
            .param("date", settlementDate)
            .query(Long::class.java)
            .optional()
            .map { minOf(it, 0L) }
            .orElse(0L)

    fun holdPolicy(at: LocalDateTime): HoldPolicy =
        jdbc.sql(
            """
            SELECT hold_rate, hold_days FROM hold_policy
            WHERE effective_from <= :at
            ORDER BY effective_from DESC, id DESC
            LIMIT 1
            """,
        )
            .param("at", at)
            .query { rs, _ -> HoldPolicy(rs.getBigDecimal("hold_rate"), rs.getInt("hold_days")) }
            .optional()
            .orElseThrow { IllegalStateException("보류 정책 없음") }
}

data class SalesSummary(val creatorId: Long, val grossSales: Long, val refundAmount: Long, val feeAmount: Long) {
    companion object {
        fun empty(creatorId: Long) = SalesSummary(creatorId, 0, 0, 0)
    }
}

data class HoldPolicy(val holdRate: BigDecimal, val holdDays: Int)
