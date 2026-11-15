package com.motif.settlement.controller

import com.motif.member.auth.CurrentMemberId
import com.motif.settlement.service.WithholdingTaxJob
import com.motif.settlement.service.WithholdingTaxRepository
import com.motif.settlement.service.WithholdingTaxRow
import com.motif.settlement.service.WithholdingTaxRunResult
import com.motif.settlement.service.toTaxMonth
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth

data class WithholdingTaxMonthResponse(
    val taxMonth: String,
    val creatorCount: Int,
    val payoutCount: Int,
    val grossAmount: Long,
    val incomeTax: Long,
    val localTax: Long,
    val rows: List<WithholdingTaxRow>,
) {
    companion object {
        fun of(taxMonth: String, rows: List<WithholdingTaxRow>) = WithholdingTaxMonthResponse(
            taxMonth,
            rows.map { it.creatorId }.distinct().size,
            rows.sumOf { it.payoutCount },
            rows.sumOf { it.grossAmount },
            rows.sumOf { it.incomeTax },
            rows.sumOf { it.localTax },
            rows,
        )
    }
}

@Tag(name = "원천세 (관리자)")
@RestController
class AdminWithholdingTaxController(
    private val job: WithholdingTaxJob,
    private val repository: WithholdingTaxRepository,
) {

    @Operation(summary = "월 집계 실행", description = "지난 달만 / 다시 돌려도 같은 결과")
    @PostMapping("/api/admin/withholding-taxes/run")
    fun run(
        @Parameter(example = "2026-10") @RequestParam @DateTimeFormat(pattern = "yyyy-MM") month: YearMonth,
    ): WithholdingTaxRunResult = job.run(month)

    @Operation(summary = "월 집계 조회", description = "신고용 합계 + 창작자별 행")
    @GetMapping("/api/admin/withholding-taxes")
    fun month(
        @Parameter(example = "2026-10") @RequestParam @DateTimeFormat(pattern = "yyyy-MM") month: YearMonth,
    ): WithholdingTaxMonthResponse {
        val rows = repository.findByMonth(month)
        return WithholdingTaxMonthResponse.of(month.toTaxMonth(), rows)
    }
}

@Tag(name = "원천세 (창작자)")
@RestController
@PreAuthorize("hasRole('CREATOR')")
class CreatorWithholdingTaxController(
    private val repository: WithholdingTaxRepository,
) {

    @Operation(summary = "내 원천징수 내역", description = "연도별 월 합계 / 종합소득세 신고 참고용")
    @GetMapping("/api/creator/withholding-taxes")
    fun mine(
        @CurrentMemberId creatorId: Long,
        @RequestParam @Min(2020) @Max(2100) year: Int,
    ): List<WithholdingTaxRow> = repository.findByCreatorAndYear(creatorId, year)
}
