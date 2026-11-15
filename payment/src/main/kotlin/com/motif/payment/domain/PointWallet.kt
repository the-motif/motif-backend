package com.motif.payment.domain

import com.motif.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

// 잔액 변경은 PointWalletRepository의 조건부·증감식 UPDATE로만 / 필드 직접 수정 금지
@Entity
class PointWallet(
    memberId: Long,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, unique = true, updatable = false)
    val memberId: Long = memberId

    @Column(nullable = false)
    var paidBalance: Long = 0
        protected set

    @Column(nullable = false)
    var freeBalance: Long = 0
        protected set

    val totalBalance: Long
        get() = paidBalance + freeBalance
}
