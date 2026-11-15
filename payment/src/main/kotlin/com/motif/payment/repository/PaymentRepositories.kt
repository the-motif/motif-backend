package com.motif.payment.repository

import com.motif.payment.domain.ChargeOrder
import com.motif.payment.domain.OutboxEvent
import com.motif.payment.domain.PointKind
import com.motif.payment.domain.PointLot
import com.motif.payment.domain.PointLotUsage
import com.motif.payment.domain.PointTransaction
import com.motif.payment.domain.PointWallet
import com.motif.payment.domain.Purchase
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface PointWalletRepository : JpaRepository<PointWallet, Long> {

    fun findByMemberId(memberId: Long): PointWallet?

    // 동시 생성돼도 에러 없이 1개만
    @Modifying
    @Query(
        value = """
            INSERT INTO point_wallet (member_id, paid_balance, free_balance, created_at, updated_at)
            VALUES (:memberId, 0, 0, :now, :now)
            ON DUPLICATE KEY UPDATE member_id = member_id
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(@Param("memberId") memberId: Long, @Param("now") now: LocalDateTime): Int

    // 증감식 UPDATE / 읽고-계산하고-덮어쓰기 금지
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PointWallet w set w.paidBalance = w.paidBalance + :amount, w.updatedAt = :now where w.id = :walletId")
    fun increasePaid(@Param("walletId") walletId: Long, @Param("amount") amount: Long, @Param("now") now: LocalDateTime): Int

    // 조건부 차감 / 0이면 잔액 부족
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update PointWallet w set w.paidBalance = w.paidBalance - :amount, w.updatedAt = :now
        where w.id = :walletId and w.paidBalance >= :amount
        """,
    )
    fun decreasePaid(@Param("walletId") walletId: Long, @Param("amount") amount: Long, @Param("now") now: LocalDateTime): Int
}

interface PointLotRepository : JpaRepository<PointLot, Long> {

    // 차감 대상: 남은 금액 있고 안 만료 / 무상 먼저, 만료 임박 순 / 행 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select l from PointLot l
        where l.walletId = :walletId and l.remainingAmount > 0 and l.expiresAt > :now
        order by case when l.kind = :freeKind then 0 else 1 end, l.expiresAt, l.id
        """,
    )
    fun findUsableForUpdate(
        @Param("walletId") walletId: Long,
        @Param("now") now: LocalDateTime,
        @Param("freeKind") freeKind: PointKind = PointKind.FREE,
    ): List<PointLot>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from PointLot l where l.id in :ids")
    fun findAllByIdForUpdate(@Param("ids") ids: Collection<Long>): List<PointLot>
}

interface PointLotUsageRepository : JpaRepository<PointLotUsage, Long> {

    fun findAllByPurchaseId(purchaseId: Long): List<PointLotUsage>
}

interface ChargeOrderRepository : JpaRepository<ChargeOrder, Long> {

    fun findByIdempotencyKey(idempotencyKey: String): ChargeOrder?

    fun findByOrderNo(orderNo: String): ChargeOrder?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChargeOrder c where c.orderNo = :orderNo")
    fun findByOrderNoForUpdate(@Param("orderNo") orderNo: String): ChargeOrder?
}

interface PurchaseRepository : JpaRepository<Purchase, Long> {

    fun findByIdempotencyKey(idempotencyKey: String): Purchase?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Purchase p where p.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Purchase?

    fun findAllByBuyerIdOrderByIdDesc(buyerId: Long, pageable: Pageable): Slice<Purchase>

    @Query("select p from Purchase p where p.createdAt >= :from and p.createdAt < :to")
    fun findAllCreatedBetween(@Param("from") from: LocalDateTime, @Param("to") to: LocalDateTime): List<Purchase>

    @Query("select p from Purchase p where p.refundedAt >= :from and p.refundedAt < :to")
    fun findAllRefundedBetween(@Param("from") from: LocalDateTime, @Param("to") to: LocalDateTime): List<Purchase>
}

interface PointTransactionRepository : JpaRepository<PointTransaction, Long> {

    fun findAllByWalletIdOrderByIdDesc(walletId: Long, pageable: Pageable): Slice<PointTransaction>
}

interface OutboxEventRepository : JpaRepository<OutboxEvent, Long> {

    @Query(
        value = "SELECT id FROM outbox_event WHERE status = 'PENDING' ORDER BY id LIMIT :limit",
        nativeQuery = true,
    )
    fun findPendingIds(@Param("limit") limit: Int): List<Long>

    // 다른 서버가 잠근 행은 건너뜀 (SKIP LOCKED) → null
    @Query(
        value = "SELECT * FROM outbox_event WHERE id = :id AND status = 'PENDING' FOR UPDATE SKIP LOCKED",
        nativeQuery = true,
    )
    fun findPendingByIdForUpdate(@Param("id") id: Long): OutboxEvent?

    fun findAllByAggregateTypeAndAggregateId(aggregateType: String, aggregateId: Long): List<OutboxEvent>
}
