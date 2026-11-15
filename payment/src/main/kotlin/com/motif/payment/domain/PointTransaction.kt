package com.motif.payment.domain

import com.motif.common.entity.BaseCreatedTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

// 포인트 원장 / 행 추가만, 수정·삭제 금지
@Entity
class PointTransaction(
    walletId: Long,
    type: PointTxType,
    paidAmount: Long,
    freeAmount: Long,
    balanceAfter: Long,
    refType: PointRefType,
    refId: Long,
) : BaseCreatedTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, updatable = false)
    val walletId: Long = walletId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val type: PointTxType = type

    // 증가 + / 감소 -
    @Column(nullable = false, updatable = false)
    val amount: Long = paidAmount + freeAmount

    @Column(nullable = false, updatable = false)
    val paidAmount: Long = paidAmount

    @Column(nullable = false, updatable = false)
    val freeAmount: Long = freeAmount

    @Column(nullable = false, updatable = false)
    val balanceAfter: Long = balanceAfter

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val refType: PointRefType = refType

    @Column(nullable = false, updatable = false)
    val refId: Long = refId
}
