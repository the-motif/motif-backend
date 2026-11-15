package com.motif.settlement.controller

import com.motif.common.response.SliceResponse
import com.motif.member.auth.CurrentMemberId
import com.motif.settlement.domain.DailySettlement
import com.motif.settlement.domain.SalesRecord
import com.motif.settlement.domain.SalesType
import com.motif.settlement.repository.CreatorBalanceRepository
import com.motif.settlement.repository.DailySettlementRepository
import com.motif.settlement.repository.SalesRecordRepository
import com.motif.settlement.service.DailySettlementJob
import com.motif.settlement.service.DailySettlementRunResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

data class CreatorBalanceResponse(val availableAmount: Long, val heldAmount: Long)

data class DailySettlementResponse(
    val settlementDate: LocalDate,
    val grossSales: Long,
    val refundAmount: Long,
    val feeAmount: Long,
    val netAmount: Long,
    val holdAmount: Long,
    val holdReleaseDate: LocalDate,
    val holdReleaseAmount: Long,
    val carryOverAmount: Long,
    val payableAmount: Long,
) {
    companion object {
        fun from(d: DailySettlement) = DailySettlementResponse(
            d.settlementDate, d.grossSales, d.refundAmount, d.feeAmount, d.netAmount, d.holdAmount,
            d.holdReleaseDate, d.holdReleaseAmount, d.carryOverAmount, d.payableAmount,
        )
    }
}

data class SalesRecordResponse(
    val purchaseId: Long,
    val type: SalesType,
    val listPrice: Long,
    val feeRate: BigDecimal,
    val feeAmount: Long,
    val occurredAt: LocalDateTime,
    val settlementDate: LocalDate?,
) {
    companion object {
        fun from(s: SalesRecord) = SalesRecordResponse(
            s.purchaseId, s.type, s.listPrice, s.feeRate, s.feeAmount, s.occurredAt, s.settlementDate,
        )
    }
}

@Tag(name = "정산 (창작자)")
@RestController
@PreAuthorize("hasRole('CREATOR')")
@Transactional(readOnly = true)
class CreatorSettlementController(
    private val creatorBalanceRepository: CreatorBalanceRepository,
    private val dailySettlementRepository: DailySettlementRepository,
    private val salesRecordRepository: SalesRecordRepository,
) {

    @Operation(summary = "내 정산 잔액", description = "available 출금 가능 / held 보류 중")
    @GetMapping("/api/creator/settlements/balance")
    fun balance(@CurrentMemberId creatorId: Long): CreatorBalanceResponse =
        creatorBalanceRepository.findByCreatorId(creatorId)
            ?.let { CreatorBalanceResponse(it.availableAmount, it.heldAmount) }
            ?: CreatorBalanceResponse(0, 0)

    @Operation(summary = "일별 정산 내역", description = "최신순")
    @GetMapping("/api/creator/settlements/daily")
    fun daily(
        @CurrentMemberId creatorId: Long,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "30") @Min(1) @Max(100) size: Int,
    ): SliceResponse<DailySettlementResponse> =
        SliceResponse.of(
            dailySettlementRepository.findAllByCreatorIdOrderBySettlementDateDesc(creatorId, PageRequest.of(page, size)),
            DailySettlementResponse::from,
        )

    @Operation(summary = "판매·환불 내역", description = "최신순 / settlementDate null이면 아직 정산 전")
    @GetMapping("/api/creator/settlements/sales")
    fun sales(
        @CurrentMemberId creatorId: Long,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): SliceResponse<SalesRecordResponse> =
        SliceResponse.of(
            salesRecordRepository.findAllByCreatorIdOrderByIdDesc(creatorId, PageRequest.of(page, size)),
            SalesRecordResponse::from,
        )
}

@Tag(name = "정산 (관리자)")
@RestController
class AdminSettlementController(
    private val dailySettlementJob: DailySettlementJob,
) {

    @Operation(summary = "일일 정산 수동 실행", description = "재실행 안전 (이미 정산한 창작자는 건너뜀) / 미래 날짜 불가")
    @PostMapping("/api/admin/settlements/daily/run")
    fun run(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ): DailySettlementRunResult = dailySettlementJob.run(date)
}
