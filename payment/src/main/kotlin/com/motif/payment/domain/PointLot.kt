package com.motif.payment.domain

import com.motif.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDateTime

// 충전·지급 건별 잔액 / 사용 순서: 무상 → 유상, 각각 만료 임박 순
@Entity
class PointLot(
    walletId: Long,
    kind: PointKind,
    sourceType: LotSource,
    sourceId: Long,
    amount: Long,
    expiresAt: LocalDateTime,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, updatable = false)
    val walletId: Long = walletId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val kind: PointKind = kind

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val sourceType: LotSource = sourceType

    @Column(nullable = false, updatable = false)
    val sourceId: Long = sourceId

    @Column(nullable = false, updatable = false)
    val originalAmount: Long = amount

    @Column(nullable = false)
    var remainingAmount: Long = amount
        protected set

    @Column(nullable = false, updatable = false)
    val expiresAt: LocalDateTime = expiresAt

    init {
        require(amount > 0) { "lot 금액은 양수" }
    }

    // 실제로 뺀 금액 반환 (남은 것보다 많이 요청하면 남은 만큼)
    fun use(requested: Long): Long {
        require(requested > 0)
        val used = minOf(requested, remainingAmount)
        remainingAmount -= used
        return used
    }

    fun restore(amount: Long) {
        require(amount > 0)
        check(remainingAmount + amount <= originalAmount) { "원래 금액 초과 복구 id=$id" }
        remainingAmount += amount
    }
}
