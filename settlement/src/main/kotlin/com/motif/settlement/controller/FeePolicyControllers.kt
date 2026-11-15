package com.motif.settlement.controller

import com.motif.member.auth.CurrentMemberId
import com.motif.settlement.domain.FeePolicy
import com.motif.settlement.domain.RevenueType
import com.motif.settlement.service.FeePolicyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDateTime

data class FeePolicyCreateRequest(
    @Schema(description = "null이면 기본 요율 변경", example = "12")
    val creatorId: Long? = null,

    @Schema(example = "CONTENT")
    @field:NotNull
    val revenueType: RevenueType? = null,

    @Schema(description = "0.1000 = 10% / 최대 0.5000", example = "0.1000")
    @field:NotNull
    val feeRate: BigDecimal? = null,

    @Schema(description = "null이면 지금부터 / 과거 불가", example = "2026-12-01T00:00:00")
    val effectiveFrom: LocalDateTime? = null,

    @Schema(example = "2026 우수 창작자 계약")
    @NotBlank
    @Size(max = 255)
    val reason: String = "",
)

data class FeePolicyEndRequest(
    @Schema(description = "null이면 지금 / 과거 불가", example = "2027-01-01T00:00:00")
    val endAt: LocalDateTime? = null,

    @NotBlank
    @Size(max = 255)
    val reason: String = "",
)

data class FeePolicyResponse(
    val id: Long,
    val creatorId: Long?,
    val revenueType: RevenueType,
    val feeRate: BigDecimal,
    val effectiveFrom: LocalDateTime,
    val effectiveTo: LocalDateTime?,
    val reason: String,
    val createdBy: Long,
    val createdAt: LocalDateTime,
    val endedBy: Long?,
    val endReason: String?,
) {
    companion object {
        fun from(p: FeePolicy) = FeePolicyResponse(
            p.id!!, p.creatorId, p.revenueType, p.feeRate, p.effectiveFrom, p.effectiveTo,
            p.reason, p.createdBy, p.createdAt, p.endedBy, p.endReason,
        )
    }
}

// source: CONTRACT 계약 요율 / DEFAULT 기본 요율
data class AppliedFeeRateResponse(
    val revenueType: RevenueType,
    val feeRate: BigDecimal,
    val source: String,
    val effectiveFrom: LocalDateTime,
    val effectiveTo: LocalDateTime?,
)

@Tag(name = "수수료 정책 (관리자)")
@RestController
class AdminFeePolicyController(
    private val feePolicyService: FeePolicyService,
) {

    @Operation(summary = "수수료 정책 추가", description = "creatorId 있으면 창작자 계약 요율, 없으면 기본 요율 / 판매 시각 기준 적용, 소급 안 함")
    @PostMapping("/api/admin/fee-policies")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @CurrentMemberId adminId: Long,
        @Valid @RequestBody request: FeePolicyCreateRequest,
    ): FeePolicyResponse = FeePolicyResponse.from(
        feePolicyService.create(
            adminId, request.creatorId, request.revenueType!!, request.feeRate!!, request.effectiveFrom, request.reason,
        ),
    )

    @Operation(summary = "창작자 계약 요율 종료", description = "종료 시각부터 기본 요율 / 기본 요율은 종료 불가")
    @PostMapping("/api/admin/fee-policies/{id}/end")
    fun end(
        @CurrentMemberId adminId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: FeePolicyEndRequest,
    ): FeePolicyResponse = FeePolicyResponse.from(feePolicyService.end(adminId, id, request.endAt, request.reason))

    @Operation(summary = "수수료 정책 이력", description = "creatorId 없으면 기본 요율 이력 / 최신순")
    @GetMapping("/api/admin/fee-policies")
    fun history(@RequestParam(required = false) creatorId: Long?): List<FeePolicyResponse> =
        feePolicyService.history(creatorId).map(FeePolicyResponse::from)

    @Operation(summary = "창작자에게 지금 적용되는 요율")
    @GetMapping("/api/admin/fee-policies/applied")
    fun applied(@RequestParam creatorId: Long): List<AppliedFeeRateResponse> = applied(feePolicyService, creatorId)
}

@Tag(name = "정산 (창작자)")
@RestController
@PreAuthorize("hasRole('CREATOR')")
class CreatorFeeRateController(
    private val feePolicyService: FeePolicyService,
) {

    @Operation(summary = "내 수수료율", description = "판매 유형별 지금 적용 요율 / source CONTRACT 계약, DEFAULT 기본")
    @GetMapping("/api/creator/settlements/fee-rates")
    fun myRates(@CurrentMemberId creatorId: Long): List<AppliedFeeRateResponse> = applied(feePolicyService, creatorId)
}

private fun applied(service: FeePolicyService, creatorId: Long) =
    service.currentPolicies(creatorId).map { (type, p) ->
        AppliedFeeRateResponse(type, p.feeRate, if (p.isContract) "CONTRACT" else "DEFAULT", p.effectiveFrom, p.effectiveTo)
    }
