package com.motif.payment.domain

import com.motif.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime

// 구매·환불과 같은 트랜잭션에 저장 → OutboxRelay가 커밋 후 발행
@Entity
class OutboxEvent(
    eventId: String,
    aggregateType: String,
    aggregateId: Long,
    eventType: String,
    payload: String,
    traceId: String? = null,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, unique = true, length = 64, updatable = false)
    val eventId: String = eventId

    @Column(nullable = false, length = 30, updatable = false)
    val aggregateType: String = aggregateType

    @Column(nullable = false, updatable = false)
    val aggregateId: Long = aggregateId

    @Column(nullable = false, length = 50, updatable = false)
    val eventType: String = eventType

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    val payload: String = payload

    @Column(length = 64, updatable = false)
    val traceId: String? = traceId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OutboxStatus = OutboxStatus.PENDING
        protected set

    @Column(nullable = false)
    var retryCount: Int = 0
        protected set

    var publishedAt: LocalDateTime? = null
        protected set

    fun markPublished(now: LocalDateTime) {
        status = OutboxStatus.PUBLISHED
        publishedAt = now
    }

    // 최대 횟수 넘으면 FAILED (수동 확인)
    fun markRetry(maxRetry: Int) {
        retryCount += 1
        if (retryCount >= maxRetry) {
            status = OutboxStatus.FAILED
        }
    }
}
