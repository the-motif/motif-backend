package com.motif.settlement.domain

import com.motif.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDate
import java.time.LocalDateTime

// 정산일 + 창작자 1행 (유니크) / hold_released_at은 해제 배치가 SQL로 채움
@Entity
class DailySettlement(
    settlementDate: LocalDate,
    creatorId: Long,
    grossSales: Long,
    refundAmount: Long,
    feeAmount: Long,
    result: SettlementResult,
    holdReleaseDate: LocalDate,
    holdReleaseAmount: Long,
    carryOverAmount: Long,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, updatable = false)
    val settlementDate: LocalDate = settlementDate

    @Column(nullable = false, updatable = false)
    val creatorId: Long = creatorId

    @Column(nullable = false, updatable = false)
    val grossSales: Long = grossSales

    @Column(nullable = false, updatable = false)
    val refundAmount: Long = refundAmount

    @Column(nullable = false, updatable = false)
    val feeAmount: Long = feeAmount

    @Column(nullable = false, updatable = false)
    val netAmount: Long = result.netAmount

    @Column(nullable = false, updatable = false)
    val holdAmount: Long = result.holdAmount

    @Column(nullable = false, updatable = false)
    val holdReleaseDate: LocalDate = holdReleaseDate

    @Column(insertable = false, updatable = false)
    val holdReleasedAt: LocalDateTime? = null

    @Column(nullable = false, updatable = false)
    val holdReleaseAmount: Long = holdReleaseAmount

    @Column(nullable = false, updatable = false)
    val carryOverAmount: Long = carryOverAmount

    @Column(nullable = false, updatable = false)
    val payableAmount: Long = result.payableAmount

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: DailySettlementStatus = DailySettlementStatus.CALCULATED
        protected set
}
