package com.motif.settlement.controller

import com.motif.common.exception.BusinessException
import com.motif.common.response.SliceResponse
import com.motif.payment.api.PaymentReconciliationApi
import com.motif.settlement.exception.SettlementErrorCode
import com.motif.member.auth.CurrentMemberId
import com.motif.settlement.domain.MismatchType
import com.motif.settlement.domain.ReconciliationMismatch
import com.motif.settlement.domain.ReconciliationRun
import com.motif.settlement.domain.ReconciliationStatus
import com.motif.settlement.domain.SalesType
import com.motif.settlement.repository.ReconciliationMismatchRepository
import com.motif.settlement.repository.ReconciliationRunRepository
import com.motif.settlement.service.ReconciliationJob
import com.motif.settlement.service.ReconciliationWriter
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.data.domain.PageRequest
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime

data class ReconciliationRunResponse(
    val runId: Long,
    val targetDate: LocalDate,
    val attempt: Int,
    val status: ReconciliationStatus,
    val paymentCount: Int?,
    val paymentAmount: Long?,
    val settlementCount: Int?,
    val settlementAmount: Long?,
    val mismatchCount: Int,
    val startedAt: LocalDateTime,
    val finishedAt: LocalDateTime?,
) {
    companion object {
        fun from(r: ReconciliationRun) = ReconciliationRunResponse(
            r.id!!, r.targetDate, r.attempt, r.status, r.paymentCount, r.paymentAmount,
            r.settlementCount, r.settlementAmount, r.mismatchCount, r.createdAt, r.finishedAt,
        )
    }
}

data class MismatchResponse(
    val mismatchId: Long,
    val runId: Long,
    val purchaseId: Long,
    val salesType: SalesType,
    val creatorId: Long,
    val mismatchType: MismatchType,
    val paymentAmount: Long?,
    val settlementAmount: Long?,
    val resolvedAt: LocalDateTime?,
    val resolvedBy: Long?,
    val resolutionNote: String?,
) {
    companion object {
        fun from(m: ReconciliationMismatch) = MismatchResponse(
            m.id!!, m.runId, m.purchaseId, m.salesType, m.creatorId, m.mismatchType,
            m.paymentAmount, m.settlementAmount, m.resolvedAt, m.resolvedBy, m.resolutionNote,
        )
    }
}

data class RepublishResponse(val purchaseId: Long, val requeuedEventCount: Int)

data class ResolveMismatchRequest(
    @NotBlank
    @Size(max = 255)
    val note: String = "",
)

@Tag(name = "정산 대사 (관리자)")
@RestController
class AdminReconciliationController(
    private val reconciliationJob: ReconciliationJob,
    private val writer: ReconciliationWriter,
    private val runRepository: ReconciliationRunRepository,
    private val mismatchRepository: ReconciliationMismatchRepository,
    private val paymentApi: PaymentReconciliationApi,
) {

    @Operation(summary = "대사 수동 실행", description = "지난 날짜만 / 다시 돌리면 attempt 증가, 이전 미해결 건은 자동으로 닫힘")
    @PostMapping("/api/admin/reconciliations/run")
    fun run(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate): ReconciliationRunResponse =
        ReconciliationRunResponse.from(reconciliationJob.run(date))

    @Operation(summary = "날짜별 대사 실행 이력", description = "최신 attempt 먼저")
    @GetMapping("/api/admin/reconciliations")
    fun runs(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate): List<ReconciliationRunResponse> =
        runRepository.findAllByTargetDateOrderByAttemptDesc(date).map(ReconciliationRunResponse::from)

    @Operation(summary = "실행별 불일치")
    @GetMapping("/api/admin/reconciliations/{runId}/mismatches")
    fun mismatchesOfRun(@PathVariable runId: Long): List<MismatchResponse> =
        mismatchRepository.findAllByRunIdOrderByIdAsc(runId).map(MismatchResponse::from)

    @Operation(summary = "불일치 목록", description = "resolved=false(기본) 미해결 = 정산 보류 중")
    @GetMapping("/api/admin/reconciliations/mismatches")
    fun mismatches(
        @RequestParam(defaultValue = "false") resolved: Boolean,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) size: Int,
    ): SliceResponse<MismatchResponse> {
        val pageable = PageRequest.of(page, size)
        val slice = if (resolved) {
            mismatchRepository.findAllByResolvedAtIsNotNullOrderByIdDesc(pageable)
        } else {
            mismatchRepository.findAllByResolvedAtIsNullOrderByIdAsc(pageable)
        }
        return SliceResponse.of(slice, MismatchResponse::from)
    }

    @Operation(
        summary = "불일치 구매의 이벤트 재발행",
        description = "결제 이벤트를 다시 보냄 (원장에 없음·환불 누락용) → 1초 뒤 대사 재실행하면 자동 해결",
    )
    @PostMapping("/api/admin/reconciliations/mismatches/{id}/republish")
    fun republish(@PathVariable id: Long): RepublishResponse {
        val mismatch = mismatchRepository.findById(id).orElseThrow { BusinessException(SettlementErrorCode.MISMATCH_NOT_FOUND) }
        return RepublishResponse(mismatch.purchaseId, paymentApi.republishEvents(mismatch.purchaseId))
    }

    @Operation(summary = "불일치 해결 처리", description = "원인 조치 후 메모와 함께 닫음 → 다음 일일 정산부터 보류 해제")
    @PostMapping("/api/admin/reconciliations/mismatches/{id}/resolve")
    fun resolve(
        @CurrentMemberId adminId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: ResolveMismatchRequest,
    ): MismatchResponse = MismatchResponse.from(writer.resolve(adminId, id, request.note))
}
