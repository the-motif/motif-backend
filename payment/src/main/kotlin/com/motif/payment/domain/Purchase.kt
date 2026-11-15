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

// list_price = coupon_discount + paid_point_amount + free_point_amount (DB CHECK)
@Entity
class Purchase(
    buyerId: Long,
    sellerId: Long,
    targetType: PurchaseTarget,
    targetId: Long,
    listPrice: Long,
    paidPointAmount: Long,
    freePointAmount: Long,
    idempotencyKey: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, updatable = false)
    val buyerId: Long = buyerId

    // 선물 아니면 buyerId
    @Column(nullable = false, updatable = false)
    val recipientId: Long = buyerId

    // 창작자 / 정산 대상
    @Column(nullable = false, updatable = false)
    val sellerId: Long = sellerId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val targetType: PurchaseTarget = targetType

    @Column(nullable = false, updatable = false)
    val targetId: Long = targetId

    // 판매 시점 정가 스냅샷 / 정산 기준
    @Column(nullable = false, updatable = false)
    val listPrice: Long = listPrice

    @Column(nullable = false, updatable = false)
    val couponDiscount: Long = 0

    @Column(updatable = false)
    val memberCouponId: Long? = null

    @Column(nullable = false, updatable = false)
    val paidPointAmount: Long = paidPointAmount

    @Column(nullable = false, updatable = false)
    val freePointAmount: Long = freePointAmount

    @Column(length = 200, updatable = false)
    val message: String? = null

    @Column(nullable = false, unique = true, length = 64, updatable = false)
    val idempotencyKey: String = idempotencyKey

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PurchaseStatus = PurchaseStatus.COMPLETED
        protected set

    var refundedAt: LocalDateTime? = null
        protected set

    var refundReason: String? = null
        protected set

    init {
        require(listPrice == couponDiscount + paidPointAmount + freePointAmount) { "구매 금액 합 불일치" }
    }

    fun refund(reason: String, now: LocalDateTime) {
        check(status == PurchaseStatus.COMPLETED) { "이미 환불됨 id=$id" }
        status = PurchaseStatus.REFUNDED
        refundReason = reason
        refundedAt = now
    }
}
