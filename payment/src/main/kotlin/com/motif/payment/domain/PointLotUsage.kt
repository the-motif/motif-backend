package com.motif.payment.domain

import com.motif.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDateTime

// 구매 1건이 어느 lot에서 얼마 빠졌는지 / 환불은 같은 lot으로 복구
@Entity
class PointLotUsage(
    lotId: Long,
    purchaseId: Long,
    amount: Long,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, updatable = false)
    val lotId: Long = lotId

    @Column(nullable = false, updatable = false)
    val purchaseId: Long = purchaseId

    @Column(nullable = false, updatable = false)
    val amount: Long = amount

    var restoredAt: LocalDateTime? = null
        protected set

    fun markRestored(now: LocalDateTime) {
        check(restoredAt == null) { "이미 복구됨 id=$id" }
        restoredAt = now
    }
}
