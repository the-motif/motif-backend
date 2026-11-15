package com.motif.settlement.domain

import com.motif.common.entity.BaseCreatedTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.math.BigDecimal
import java.time.LocalDateTime

// 수수료 정책 / 행 추가만, 바꿀 수 있는 건 계약 종료(effective_to) 한 번뿐
// creatorId null = 기본 요율, 값 있음 = 창작자 계약 요율 (기본보다 우선)
@Entity
class FeePolicy(
    creatorId: Long?,
    revenueType: RevenueType,
    feeRate: BigDecimal,
    effectiveFrom: LocalDateTime,
    reason: String,
    createdBy: Long,
) : BaseCreatedTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(updatable = false)
    val creatorId: Long? = creatorId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val revenueType: RevenueType = revenueType

    @Column(nullable = false, precision = 5, scale = 4, updatable = false)
    val feeRate: BigDecimal = feeRate

    @Column(nullable = false, updatable = false)
    val effectiveFrom: LocalDateTime = effectiveFrom

    var effectiveTo: LocalDateTime? = null
        protected set

    @Column(length = 255, updatable = false)
    val reason: String = reason

    @Column(nullable = false, updatable = false)
    val createdBy: Long = createdBy

    var endedBy: Long? = null
        protected set

    @Column(length = 255)
    var endReason: String? = null
        protected set

    val isContract: Boolean get() = creatorId != null

    fun end(at: LocalDateTime, adminId: Long, reason: String) {
        check(isContract && effectiveTo == null) { "종료 불가 id=$id" }
        effectiveTo = maxOf(at, effectiveFrom)
        endedBy = adminId
        endReason = reason
    }
}
