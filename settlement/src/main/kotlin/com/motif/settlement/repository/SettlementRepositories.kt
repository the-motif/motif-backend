package com.motif.settlement.repository

import com.motif.settlement.domain.CreatorBalance
import com.motif.settlement.domain.DailySettlement
import com.motif.settlement.domain.SalesRecord
import com.motif.settlement.domain.SalesType
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalDateTime

interface SalesRecordRepository : JpaRepository<SalesRecord, Long> {

    fun existsByEventId(eventId: String): Boolean

    fun findByPurchaseIdAndType(purchaseId: Long, type: SalesType): SalesRecord?

    fun findAllByCreatorIdOrderByIdDesc(creatorId: Long, pageable: Pageable): Slice<SalesRecord>

    @Query("select s from SalesRecord s where s.occurredAt >= :from and s.occurredAt < :to")
    fun findAllOccurredBetween(@Param("from") from: LocalDateTime, @Param("to") to: LocalDateTime): List<SalesRecord>

    fun findAllByPurchaseIdIn(purchaseIds: Collection<Long>): List<SalesRecord>
}

interface DailySettlementRepository : JpaRepository<DailySettlement, Long> {

    fun existsBySettlementDateAndCreatorId(settlementDate: LocalDate, creatorId: Long): Boolean

    fun findAllByCreatorIdOrderBySettlementDateDesc(creatorId: Long, pageable: Pageable): Slice<DailySettlement>

    fun findBySettlementDateAndCreatorId(settlementDate: LocalDate, creatorId: Long): DailySettlement?
}

interface CreatorBalanceRepository : JpaRepository<CreatorBalance, Long> {

    fun findByCreatorId(creatorId: Long): CreatorBalance?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from CreatorBalance b where b.creatorId = :creatorId")
    fun findByCreatorIdForUpdate(@Param("creatorId") creatorId: Long): CreatorBalance?

    // 동시 생성돼도 1개만
    @Modifying
    @Query(
        value = """
            INSERT INTO creator_balance (creator_id, available_amount, held_amount, created_at, updated_at)
            VALUES (:creatorId, 0, 0, :now, :now)
            ON DUPLICATE KEY UPDATE creator_id = creator_id
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(@Param("creatorId") creatorId: Long, @Param("now") now: LocalDateTime): Int
}

interface BalanceLedgerRepository : JpaRepository<com.motif.settlement.domain.BalanceLedger, Long> {

    fun findAllByCreatorIdOrderByIdAsc(creatorId: Long): List<com.motif.settlement.domain.BalanceLedger>
}

interface FeePolicyRepository : JpaRepository<com.motif.settlement.domain.FeePolicy, Long> {

    fun findAllByCreatorIdOrderByIdDesc(creatorId: Long): List<com.motif.settlement.domain.FeePolicy>

    fun findAllByCreatorIdIsNullOrderByIdDesc(): List<com.motif.settlement.domain.FeePolicy>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from FeePolicy p where p.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): com.motif.settlement.domain.FeePolicy?

    // 판매 시각 기준 적용 요율 / 유효한 계약 요율 우선, 없으면 기본값
    @Query(
        """
        select p from FeePolicy p
        where p.revenueType = :type and (p.creatorId = :creatorId or p.creatorId is null)
          and p.effectiveFrom <= :at and (p.effectiveTo is null or p.effectiveTo > :at)
        order by case when p.creatorId is null then 1 else 0 end, p.effectiveFrom desc, p.id desc
        """,
    )
    fun findApplicable(
        @Param("creatorId") creatorId: Long,
        @Param("type") type: com.motif.settlement.domain.RevenueType,
        @Param("at") at: LocalDateTime,
        pageable: Pageable,
    ): List<com.motif.settlement.domain.FeePolicy>
}
