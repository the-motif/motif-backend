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

enum class ReconciliationStatus { RUNNING, MATCHED, MISMATCHED, FAILED }

enum class MismatchType {
    MISSING_IN_SETTLEMENT, // 결제엔 있고 판매 원장엔 없음 / 이벤트 유실
    MISSING_IN_PAYMENT, // 판매 원장엔 있고 결제엔 없음
    AMOUNT_DIFF,
    STATUS_DIFF, // 환불 반영 차이
}

// 대사 1회 실행 / 같은 날짜 재실행은 attempt 증가
@Entity
class ReconciliationRun(
    targetDate: LocalDate,
    attempt: Int,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, updatable = false)
    val targetDate: LocalDate = targetDate

    @Column(nullable = false, updatable = false)
    val attempt: Int = attempt

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReconciliationStatus = ReconciliationStatus.RUNNING
        protected set

    var paymentCount: Int? = null
        protected set

    var paymentAmount: Long? = null
        protected set

    var settlementCount: Int? = null
        protected set

    var settlementAmount: Long? = null
        protected set

    @Column(nullable = false)
    var mismatchCount: Int = 0
        protected set

    var finishedAt: LocalDateTime? = null
        protected set

    fun finish(summary: ReconciliationSummary, mismatchCount: Int, now: LocalDateTime) {
        check(status == ReconciliationStatus.RUNNING) { "RUNNING만 종료 id=$id" }
        paymentCount = summary.paymentCount
        paymentAmount = summary.paymentAmount
        settlementCount = summary.settlementCount
        settlementAmount = summary.settlementAmount
        this.mismatchCount = mismatchCount
        status = if (mismatchCount == 0) ReconciliationStatus.MATCHED else ReconciliationStatus.MISMATCHED
        finishedAt = now
    }

    fun fail(now: LocalDateTime) {
        status = ReconciliationStatus.FAILED
        finishedAt = now
    }
}

// 불일치 1건 / 미해결이면 해당 창작자 일일 정산 보류
@Entity
class ReconciliationMismatch(
    runId: Long,
    purchaseId: Long,
    salesType: SalesType,
    creatorId: Long,
    mismatchType: MismatchType,
    paymentAmount: Long?,
    settlementAmount: Long?,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, updatable = false)
    val runId: Long = runId

    @Column(nullable = false, updatable = false)
    val purchaseId: Long = purchaseId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val salesType: SalesType = salesType

    @Column(nullable = false, updatable = false)
    val creatorId: Long = creatorId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    val mismatchType: MismatchType = mismatchType

    @Column(updatable = false)
    val paymentAmount: Long? = paymentAmount

    @Column(updatable = false)
    val settlementAmount: Long? = settlementAmount

    var resolvedAt: LocalDateTime? = null
        protected set

    var resolvedBy: Long? = null
        protected set

    @Column(length = 255)
    var resolutionNote: String? = null
        protected set

    val resolved: Boolean get() = resolvedAt != null

    fun resolve(adminId: Long, note: String, now: LocalDateTime) {
        check(!resolved) { "이미 해결 id=$id" }
        resolvedAt = now
        resolvedBy = adminId
        resolutionNote = note
    }

    companion object {
        const val SYSTEM_RESOLVER = 0L
    }
}

data class ReconciliationSummary(
    val paymentCount: Int,
    val paymentAmount: Long,
    val settlementCount: Int,
    val settlementAmount: Long,
)
