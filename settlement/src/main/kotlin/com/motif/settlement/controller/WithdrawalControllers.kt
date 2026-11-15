package com.motif.settlement.controller

import com.motif.common.response.SliceResponse
import com.motif.member.auth.CurrentMemberId
import com.motif.settlement.crypto.FieldEncryptor
import com.motif.settlement.domain.CreatorType
import com.motif.settlement.domain.Payout
import com.motif.settlement.domain.PayoutStatus
import com.motif.settlement.domain.SettlementAccount
import com.motif.settlement.domain.TaxProfile
import com.motif.settlement.domain.VerifyStatus
import com.motif.settlement.domain.WithdrawalRequest
import com.motif.settlement.domain.WithdrawalStatus
import com.motif.settlement.repository.PayoutRepository
import com.motif.settlement.repository.WithdrawalRequestRepository
import com.motif.settlement.service.PayoutExecutor
import com.motif.settlement.service.SettlementInfoService
import com.motif.settlement.service.WithdrawalService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

data class SettlementAccountRequest(
    @Schema(example = "004")
    @field:Pattern(regexp = "^[0-9]{3}$")
    val bankCode: String = "",

    @Schema(description = "숫자만", example = "12345678901234")
    @field:Pattern(regexp = "^[0-9]{10,16}$")
    val accountNumber: String = "",

    @Schema(example = "홍길동")
    @NotBlank
    @Size(max = 50)
    val holderName: String = "",
)

data class TaxProfileRequest(
    @field:NotNull
    val creatorType: CreatorType? = null,

    @Schema(description = "실명 또는 상호", example = "홍길동")
    @NotBlank
    @Size(max = 50)
    val legalName: String = "",

    @Schema(description = "개인만 / 숫자 13자리", example = "9001011234567")
    @field:Pattern(regexp = "^[0-9]{13}$")
    val residentNo: String? = null,

    @Schema(description = "사업자만 / 숫자 10자리", example = "1234567890")
    @field:Pattern(regexp = "^[0-9]{10}$")
    val businessNo: String? = null,
)

// 민감 정보는 뒤 4자리만
data class SettlementInfoResponse(
    val account: AccountView?,
    val taxProfile: TaxProfileView?,
    val withdrawable: Boolean,
) {
    data class AccountView(val bankCode: String, val accountNumber: String, val holderName: String, val verifyStatus: VerifyStatus)

    data class TaxProfileView(val creatorType: CreatorType, val legalName: String, val idNumber: String?, val verifyStatus: VerifyStatus)

    companion object {
        fun of(account: SettlementAccount?, tax: TaxProfile?) = SettlementInfoResponse(
            account?.let {
                AccountView(it.bankCode, FieldEncryptor.mask(it.accountNumber), it.holderName, it.verifyStatus)
            },
            tax?.let {
                TaxProfileView(it.creatorType, it.legalName, (it.residentNo ?: it.businessNo)?.let(FieldEncryptor::mask), it.verifyStatus)
            },
            account?.verifyStatus == VerifyStatus.VERIFIED && tax?.verifyStatus == VerifyStatus.VERIFIED,
        )
    }
}

data class WithdrawalCreateRequest(
    @Schema(example = "10000")
    @field:NotNull
    @field:Min(1)
    val amount: Long? = null,
)

data class ReviewRequest(
    @field:NotNull
    val approved: Boolean? = null,
)

data class RejectRequest(
    @NotBlank
    @Size(max = 255)
    val reason: String = "",
)

data class PayoutResponse(
    val payoutId: Long,
    val payoutNo: String,
    val grossAmount: Long,
    val incomeTax: Long,
    val localTax: Long,
    val netAmount: Long,
    val status: PayoutStatus,
    val failReason: String?,
    val paidAt: LocalDateTime?,
) {
    companion object {
        fun from(p: Payout) = PayoutResponse(
            p.id!!, p.payoutNo, p.grossAmount, p.incomeTax, p.localTax, p.netAmount, p.status, p.failReason, p.paidAt,
        )
    }
}

data class WithdrawalResponse(
    val withdrawalId: Long,
    val creatorId: Long,
    val amount: Long,
    val status: WithdrawalStatus,
    val rejectReason: String?,
    val requestedAt: LocalDateTime,
    val reviewedAt: LocalDateTime?,
    val payout: PayoutResponse?,
) {
    companion object {
        fun of(w: WithdrawalRequest, payout: Payout?) = WithdrawalResponse(
            w.id!!, w.creatorId, w.amount, w.status, w.rejectReason, w.createdAt, w.reviewedAt, payout?.let(PayoutResponse::from),
        )
    }
}

@Tag(name = "출금 (창작자)")
@RestController
@PreAuthorize("hasRole('CREATOR')")
class CreatorWithdrawalController(
    private val settlementInfoService: SettlementInfoService,
    private val withdrawalService: WithdrawalService,
    private val withdrawalRepository: WithdrawalRequestRepository,
    private val payoutRepository: PayoutRepository,
) {

    @Operation(summary = "정산 계좌 등록·변경", description = "변경하면 다시 확인 대기(PENDING)")
    @PutMapping("/api/creator/settlement-account")
    fun saveAccount(@CurrentMemberId creatorId: Long, @Valid @RequestBody request: SettlementAccountRequest): SettlementInfoResponse {
        settlementInfoService.saveAccount(creatorId, request.bankCode, request.accountNumber, request.holderName)
        return info(creatorId)
    }

    @Operation(summary = "세금 정보 등록·변경", description = "개인 = 주민등록번호(3.3% 원천징수), 사업자 = 사업자등록번호")
    @PutMapping("/api/creator/tax-profile")
    fun saveTaxProfile(@CurrentMemberId creatorId: Long, @Valid @RequestBody request: TaxProfileRequest): SettlementInfoResponse {
        settlementInfoService.saveTaxProfile(creatorId, request.creatorType!!, request.legalName, request.residentNo, request.businessNo)
        return info(creatorId)
    }

    @Operation(summary = "내 정산 정보", description = "번호는 뒤 4자리만 / withdrawable = 둘 다 확인 완료")
    @GetMapping("/api/creator/settlement-info")
    fun info(@CurrentMemberId creatorId: Long): SettlementInfoResponse =
        SettlementInfoResponse.of(settlementInfoService.getAccount(creatorId), settlementInfoService.getTaxProfile(creatorId))

    @Operation(summary = "출금 신청", description = "10,000원 이상 / 신청 즉시 출금 가능 금액에서 차감")
    @PostMapping("/api/creator/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    fun request(
        @CurrentMemberId creatorId: Long,
        @Parameter(description = "요청마다 새 UUID / 재시도는 같은 값") @RequestHeader("Idempotency-Key") @Size(min = 8, max = 64) key: String,
        @Valid @RequestBody request: WithdrawalCreateRequest,
    ): WithdrawalResponse = WithdrawalResponse.of(withdrawalService.request(creatorId, request.amount!!, key), null)

    @Operation(summary = "내 출금 내역", description = "최신순, 지급 정보(세금·실지급액) 포함")
    @GetMapping("/api/creator/withdrawals")
    fun list(
        @CurrentMemberId creatorId: Long,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): SliceResponse<WithdrawalResponse> =
        SliceResponse.of(withdrawalRepository.findAllByCreatorIdOrderByIdDesc(creatorId, PageRequest.of(page, size))) {
            WithdrawalResponse.of(it, payoutRepository.findByWithdrawalRequestId(it.id!!))
        }
}

@Tag(name = "출금 (관리자)")
@RestController
class AdminWithdrawalController(
    private val settlementInfoService: SettlementInfoService,
    private val withdrawalService: WithdrawalService,
    private val payoutExecutor: PayoutExecutor,
    private val withdrawalRepository: WithdrawalRequestRepository,
    private val payoutRepository: PayoutRepository,
) {

    @Operation(summary = "정산 계좌 확인", description = "approved=false면 REJECTED")
    @PostMapping("/api/admin/creators/{creatorId}/settlement-account/review")
    fun reviewAccount(@PathVariable creatorId: Long, @Valid @RequestBody request: ReviewRequest): SettlementInfoResponse {
        settlementInfoService.reviewAccount(creatorId, request.approved!!)
        return info(creatorId)
    }

    @Operation(summary = "세금 정보 확인")
    @PostMapping("/api/admin/creators/{creatorId}/tax-profile/review")
    fun reviewTaxProfile(@PathVariable creatorId: Long, @Valid @RequestBody request: ReviewRequest): SettlementInfoResponse {
        settlementInfoService.reviewTaxProfile(creatorId, request.approved!!)
        return info(creatorId)
    }

    @Operation(summary = "창작자 정산 정보")
    @GetMapping("/api/admin/creators/{creatorId}/settlement-info")
    fun info(@PathVariable creatorId: Long): SettlementInfoResponse =
        SettlementInfoResponse.of(settlementInfoService.getAccount(creatorId), settlementInfoService.getTaxProfile(creatorId))

    @Operation(summary = "출금 신청 목록", description = "오래된 순")
    @GetMapping("/api/admin/withdrawals")
    fun list(
        @RequestParam(defaultValue = "REQUESTED") status: WithdrawalStatus,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) size: Int,
    ): SliceResponse<WithdrawalResponse> =
        SliceResponse.of(withdrawalRepository.findAllByStatusOrderByIdAsc(status, PageRequest.of(page, size))) {
            WithdrawalResponse.of(it, payoutRepository.findByWithdrawalRequestId(it.id!!))
        }

    @Operation(summary = "출금 승인 → 송금", description = "원천징수 계산 후 송금 / 일시 오류면 payout PENDING (자동·수동 재시도)")
    @PostMapping("/api/admin/withdrawals/{id}/approve")
    fun approve(@CurrentMemberId adminId: Long, @PathVariable id: Long): WithdrawalResponse {
        val payout = payoutExecutor.execute(withdrawalService.approve(adminId, id).id!!)
        return WithdrawalResponse.of(withdrawalRepository.findById(id).get(), payout)
    }

    @Operation(summary = "출금 반려", description = "금액은 출금 가능 금액으로 복구")
    @PostMapping("/api/admin/withdrawals/{id}/reject")
    fun reject(@CurrentMemberId adminId: Long, @PathVariable id: Long, @Valid @RequestBody request: RejectRequest): WithdrawalResponse =
        WithdrawalResponse.of(withdrawalService.reject(adminId, id, request.reason), null)

    @Operation(summary = "지급 재시도", description = "PENDING(일시 오류)만 / 같은 payoutNo로 송금")
    @PostMapping("/api/admin/payouts/{id}/retry")
    fun retry(@PathVariable id: Long): PayoutResponse = PayoutResponse.from(payoutExecutor.retry(id))
}
