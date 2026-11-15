package com.motif.settlement.domain

import com.motif.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

// 판매 원장 / 결제 이벤트 1건 = 1행, settlement_date는 정산 배치가 SQL로 채움
@Entity
class SalesRecord(
    eventId: String,
    purchaseId: Long,
    creatorId: Long,
    type: SalesType,
    revenueType: RevenueType,
    listPrice: Long,
    feeRate: BigDecimal,
    feeAmount: Long,
    occurredAt: LocalDateTime,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, unique = true, length = 64, updatable = false)
    val eventId: String = eventId

    @Column(nullable = false, updatable = false)
    val purchaseId: Long = purchaseId

    @Column(nullable = false, updatable = false)
    val creatorId: Long = creatorId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val type: SalesType = type

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val revenueType: RevenueType = revenueType

    // 환불이면 음수
    @Column(nullable = false, updatable = false)
    val listPrice: Long = listPrice

    @Column(nullable = false, precision = 5, scale = 4, updatable = false)
    val feeRate: BigDecimal = feeRate

    // 환불이면 음수
    @Column(nullable = false, updatable = false)
    val feeAmount: Long = feeAmount

    @Column(nullable = false, updatable = false)
    val occurredAt: LocalDateTime = occurredAt

    @Column(insertable = false, updatable = false)
    val settlementDate: LocalDate? = null
}
