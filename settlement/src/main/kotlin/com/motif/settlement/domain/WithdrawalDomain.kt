package com.motif.settlement.domain

import com.motif.common.entity.BaseCreatedTimeEntity
import com.motif.common.entity.BaseTimeEntity
import com.motif.settlement.crypto.EncryptedStringConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.math.BigDecimal
import java.time.LocalDateTime

enum class VerifyStatus { PENDING, VERIFIED, REJECTED }

// INDIVIDUAL 개인 (원천징수 3.3%) / BUSINESS 사업자 (원천징수 없음)
enum class CreatorType { INDIVIDUAL, BUSINESS }

enum class WithdrawalStatus { REQUESTED, APPROVED, REJECTED, PAID, FAILED }

enum class PayoutStatus { PENDING, COMPLETED, FAILED }

// 정산 계좌 / 바꾸면 다시 PENDING
@Entity
class SettlementAccount(
    creatorId: Long,
    bankCode: String,
    accountNumber: String,
    holderName: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, unique = true, updatable = false)
    val creatorId: Long = creatorId

    @Column(nullable = false, length = 10)
    var bankCode: String = bankCode
        protected set

    @Convert(converter = EncryptedStringConverter::class)
    @Column(nullable = false, length = 512)
    var accountNumber: String = accountNumber
        protected set

    @Column(nullable = false, length = 50)
    var holderName: String = holderName
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var verifyStatus: VerifyStatus = VerifyStatus.PENDING
        protected set

    var verifiedAt: LocalDateTime? = null
        protected set

    fun change(bankCode: String, accountNumber: String, holderName: String) {
        this.bankCode = bankCode
        this.accountNumber = accountNumber
        this.holderName = holderName
        verifyStatus = VerifyStatus.PENDING
        verifiedAt = null
    }

    fun review(approved: Boolean, now: LocalDateTime) {
        verifyStatus = if (approved) VerifyStatus.VERIFIED else VerifyStatus.REJECTED
        verifiedAt = if (approved) now else null
    }
}

// 세금 정보 / 개인은 주민번호(암호화), 사업자는 사업자번호
@Entity
class TaxProfile(
    creatorId: Long,
    creatorType: CreatorType,
    legalName: String,
    residentNo: String?,
    businessNo: String?,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, unique = true, updatable = false)
    val creatorId: Long = creatorId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var creatorType: CreatorType = creatorType
        protected set

    @Column(nullable = false, length = 50)
    var legalName: String = legalName
        protected set

    @Convert(converter = EncryptedStringConverter::class)
    @Column(name = "resident_no_enc", length = 512)
    var residentNo: String? = residentNo
        protected set

    @Column(length = 20)
    var businessNo: String? = businessNo
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var verifyStatus: VerifyStatus = VerifyStatus.PENDING
        protected set

    var verifiedAt: LocalDateTime? = null
        protected set

    fun change(creatorType: CreatorType, legalName: String, residentNo: String?, businessNo: String?) {
        this.creatorType = creatorType
        this.legalName = legalName
        this.residentNo = residentNo
        this.businessNo = businessNo
        verifyStatus = VerifyStatus.PENDING
        verifiedAt = null
    }

    fun review(approved: Boolean, now: LocalDateTime) {
        verifyStatus = if (approved) VerifyStatus.VERIFIED else VerifyStatus.REJECTED
        verifiedAt = if (approved) now else null
    }
}

// 세율 / 행 추가만, 지급 시점 세율 적용
@Entity
class TaxPolicy(
    creatorType: CreatorType,
    incomeTaxRate: BigDecimal,
    localTaxRate: BigDecimal,
    effectiveFrom: LocalDateTime,
    createdBy: Long,
) : BaseCreatedTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val creatorType: CreatorType = creatorType

    // 지급액 대비
    @Column(nullable = false, precision = 5, scale = 4, updatable = false)
    val incomeTaxRate: BigDecimal = incomeTaxRate

    // 소득세 대비
    @Column(nullable = false, precision = 5, scale = 4, updatable = false)
    val localTaxRate: BigDecimal = localTaxRate

    @Column(nullable = false, updatable = false)
    val effectiveFrom: LocalDateTime = effectiveFrom

    @Column(nullable = false, updatable = false)
    val createdBy: Long = createdBy
}

// 출금 신청 / 신청 때 AVAILABLE 차감, 반려·송금 실패 때 복구
@Entity
class WithdrawalRequest(
    creatorId: Long,
    amount: Long,
    idempotencyKey: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, updatable = false)
    val creatorId: Long = creatorId

    @Column(nullable = false, updatable = false)
    val amount: Long = amount

    @Column(nullable = false, unique = true, length = 64, updatable = false)
    val idempotencyKey: String = idempotencyKey

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: WithdrawalStatus = WithdrawalStatus.REQUESTED
        protected set

    @Column(length = 255)
    var rejectReason: String? = null
        protected set

    var reviewedBy: Long? = null
        protected set

    var reviewedAt: LocalDateTime? = null
        protected set

    fun approve(adminId: Long, now: LocalDateTime) {
        check(status == WithdrawalStatus.REQUESTED) { "REQUESTED만 승인 id=$id" }
        status = WithdrawalStatus.APPROVED
        reviewedBy = adminId
        reviewedAt = now
    }

    fun reject(adminId: Long, reason: String, now: LocalDateTime) {
        check(status == WithdrawalStatus.REQUESTED) { "REQUESTED만 반려 id=$id" }
        status = WithdrawalStatus.REJECTED
        rejectReason = reason
        reviewedBy = adminId
        reviewedAt = now
    }

    fun markPaid() {
        check(status == WithdrawalStatus.APPROVED) { "APPROVED만 지급 완료 id=$id" }
        status = WithdrawalStatus.PAID
    }

    fun markFailed() {
        check(status == WithdrawalStatus.APPROVED) { "APPROVED만 지급 실패 id=$id" }
        status = WithdrawalStatus.FAILED
    }
}

// 지급 / payoutNo는 재시도해도 같은 값 → 은행이 중복 송금 거름
@Entity
class Payout(
    payoutNo: String,
    withdrawalRequestId: Long,
    creatorId: Long,
    creatorType: CreatorType,
    tax: TaxResult,
    bankCode: String,
    accountNumber: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, unique = true, length = 64, updatable = false)
    val payoutNo: String = payoutNo

    @Column(nullable = false, unique = true, updatable = false)
    val withdrawalRequestId: Long = withdrawalRequestId

    @Column(nullable = false, updatable = false)
    val creatorId: Long = creatorId

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    val creatorType: CreatorType = creatorType

    @Column(nullable = false, updatable = false)
    val grossAmount: Long = tax.grossAmount

    @Column(nullable = false, updatable = false)
    val incomeTax: Long = tax.incomeTax

    @Column(nullable = false, updatable = false)
    val localTax: Long = tax.localTax

    @Column(nullable = false, updatable = false)
    val netAmount: Long = tax.netAmount

    @Column(nullable = false, length = 10, updatable = false)
    val bankCode: String = bankCode

    @Convert(converter = EncryptedStringConverter::class)
    @Column(nullable = false, length = 512, updatable = false)
    val accountNumber: String = accountNumber

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PayoutStatus = PayoutStatus.PENDING
        protected set

    @Column(length = 255)
    var failReason: String? = null
        protected set

    var paidAt: LocalDateTime? = null
        protected set

    fun complete(now: LocalDateTime) {
        check(status == PayoutStatus.PENDING) { "PENDING만 완료 id=$id" }
        status = PayoutStatus.COMPLETED
        paidAt = now
    }

    fun fail(reason: String) {
        check(status == PayoutStatus.PENDING) { "PENDING만 실패 id=$id" }
        status = PayoutStatus.FAILED
        failReason = reason.take(255)
    }
}
