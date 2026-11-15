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

@Entity
class ChargeOrder(
    orderNo: String,
    memberId: Long,
    amount: Long,
    idempotencyKey: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    // PG에 넘기는 주문 번호
    @Column(nullable = false, unique = true, length = 64, updatable = false)
    val orderNo: String = orderNo

    @Column(nullable = false, updatable = false)
    val memberId: Long = memberId

    @Column(nullable = false, updatable = false)
    val amount: Long = amount

    @Column(nullable = false)
    var canceledAmount: Long = 0
        protected set

    @Column(nullable = false, unique = true, length = 64, updatable = false)
    val idempotencyKey: String = idempotencyKey

    @Column(length = 30)
    var pgProvider: String? = null
        protected set

    @Column(length = 20)
    var payMethod: String? = null
        protected set

    @Column(unique = true, length = 200)
    var pgPaymentKey: String? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ChargeStatus = ChargeStatus.PENDING
        protected set

    var paidAt: LocalDateTime? = null
        protected set

    var canceledAt: LocalDateTime? = null
        protected set

    fun markPaid(pgProvider: String, paymentKey: String, payMethod: String, now: LocalDateTime) {
        check(status == ChargeStatus.PENDING) { "PENDING만 승인 처리 id=$id" }
        this.pgProvider = pgProvider
        this.pgPaymentKey = paymentKey
        this.payMethod = payMethod
        this.status = ChargeStatus.PAID
        this.paidAt = now
    }

    fun markFailed() {
        check(status == ChargeStatus.PENDING) { "PENDING만 실패 처리 id=$id" }
        status = ChargeStatus.FAILED
    }
}
