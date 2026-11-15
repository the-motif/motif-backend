package com.motif.payment.controller

import com.motif.common.response.SliceResponse
import com.motif.member.auth.CurrentMemberId
import com.motif.payment.dto.ChargeConfirmRequest
import com.motif.payment.dto.ChargeRequest
import com.motif.payment.dto.ChargeResponse
import com.motif.payment.dto.PointHistoryResponse
import com.motif.payment.dto.PurchaseContentRequest
import com.motif.payment.dto.PurchaseResponse
import com.motif.payment.dto.RefundRequest
import com.motif.payment.dto.WalletResponse
import com.motif.payment.service.ChargeFacade
import com.motif.payment.service.PurchaseFacade
import com.motif.payment.service.WalletService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private const val IDEMPOTENCY_HEADER = "Idempotency-Key"
private const val IDEMPOTENCY_DESC = "요청마다 새 UUID / 재시도는 같은 값"

@Tag(name = "포인트")
@RestController
class PointController(
    private val walletService: WalletService,
    private val chargeFacade: ChargeFacade,
) {

    @Operation(summary = "내 포인트 잔액")
    @GetMapping("/api/points/me")
    fun balance(@CurrentMemberId memberId: Long): WalletResponse = walletService.getBalance(memberId)

    @Operation(summary = "포인트 내역", description = "최신순")
    @GetMapping("/api/points/me/history")
    fun history(
        @CurrentMemberId memberId: Long,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): SliceResponse<PointHistoryResponse> = walletService.getHistory(memberId, PageRequest.of(page, size))

    @Operation(summary = "충전 요청 (1단계)", description = "orderNo 발급 → 앱이 PG 결제창 호출 / 1,000~1,000,000원, 100원 단위")
    @PostMapping("/api/charges")
    @ResponseStatus(HttpStatus.CREATED)
    fun requestCharge(
        @CurrentMemberId memberId: Long,
        @Parameter(description = IDEMPOTENCY_DESC) @RequestHeader(IDEMPOTENCY_HEADER) @Size(min = 8, max = 64) idempotencyKey: String,
        @Valid @RequestBody request: ChargeRequest,
    ): ChargeResponse = chargeFacade.request(memberId, request.amount!!, idempotencyKey)

    @Operation(summary = "충전 승인 (2단계)", description = "PG 결제창에서 받은 paymentKey로 승인 / 모의 PG: paymentKey가 fail로 시작하면 실패")
    @PostMapping("/api/charges/confirm")
    fun confirmCharge(
        @CurrentMemberId memberId: Long,
        @Valid @RequestBody request: ChargeConfirmRequest,
    ): ChargeResponse = chargeFacade.confirm(memberId, request.orderNo, request.paymentKey, request.amount!!)
}

@Tag(name = "구매")
@RestController
class PurchaseController(
    private val purchaseFacade: PurchaseFacade,
) {

    @Operation(summary = "콘텐츠 구매", description = "포인트 차감 + 열람 권한 / 같은 Idempotency-Key 재요청은 같은 결과")
    @PostMapping("/api/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    fun purchase(
        @CurrentMemberId memberId: Long,
        @Parameter(description = IDEMPOTENCY_DESC) @RequestHeader(IDEMPOTENCY_HEADER) @Size(min = 8, max = 64) idempotencyKey: String,
        @Valid @RequestBody request: PurchaseContentRequest,
    ): PurchaseResponse = purchaseFacade.purchaseContent(memberId, request.contentId!!, idempotencyKey)

    @Operation(summary = "구매 환불", description = "본문 열람 전 + 구매 후 7일 이내")
    @PostMapping("/api/purchases/{id}/refund")
    fun refund(
        @CurrentMemberId memberId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: RefundRequest,
    ): PurchaseResponse = purchaseFacade.refund(memberId, id, request.reason)

    @Operation(summary = "내 구매 내역", description = "최신순")
    @GetMapping("/api/purchases/me")
    fun myPurchases(
        @CurrentMemberId memberId: Long,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): SliceResponse<PurchaseResponse> = purchaseFacade.getMyPurchases(memberId, PageRequest.of(page, size))
}
