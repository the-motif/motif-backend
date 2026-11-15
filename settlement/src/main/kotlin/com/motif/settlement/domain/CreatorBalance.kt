package com.motif.settlement.domain

import com.motif.common.entity.BaseCreatedTimeEntity
import com.motif.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

// 창작자 잔액 / 변경은 비관적 락으로 읽은 뒤 apply()로만 → 원장 행 반환
@Entity
class CreatorBalance(
    creatorId: Long,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, unique = true, updatable = false)
    val creatorId: Long = creatorId

    @Column(nullable = false)
    var availableAmount: Long = 0
        protected set

    @Column(nullable = false)
    var heldAmount: Long = 0
        protected set

    fun apply(bucket: LedgerBucket, type: LedgerType, amount: Long, refId: Long, memo: String? = null): BalanceLedger {
        require(amount != 0L) { "0원 변경 금지" }
        val after = when (bucket) {
            LedgerBucket.AVAILABLE -> (availableAmount + amount).also { availableAmount = it }
            LedgerBucket.HELD -> (heldAmount + amount).also { heldAmount = it }
        }
        check(after >= 0) { "잔액 음수 creatorId=$creatorId bucket=$bucket after=$after" }
        return BalanceLedger(creatorId, bucket, type, amount, after, refId, memo)
    }
}

// 창작자 잔액 원장 / 행 추가만
@Entity
class BalanceLedger(
    creatorId: Long,
    bucket: LedgerBucket,
    type: LedgerType,
    amount: Long,
    balanceAfter: Long,
    refId: Long,
    memo: String?,
) : BaseCreatedTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, updatable = false)
    val creatorId: Long = creatorId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val bucket: LedgerBucket = bucket

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    val type: LedgerType = type

    @Column(nullable = false, updatable = false)
    val amount: Long = amount

    @Column(nullable = false, updatable = false)
    val balanceAfter: Long = balanceAfter

    @Column(nullable = false, updatable = false)
    val refId: Long = refId

    @Column(length = 255, updatable = false)
    val memo: String? = memo
}
