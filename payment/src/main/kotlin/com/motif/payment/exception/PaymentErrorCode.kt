package com.motif.payment.exception

import com.motif.common.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class PaymentErrorCode(
    private val status: HttpStatus,
    private val code: String,
    private val message: String,
) : ErrorCode {

    INVALID_CHARGE_AMOUNT(HttpStatus.BAD_REQUEST, "PAYMENT_001", "충전 금액은 1,000~1,000,000원, 100원 단위입니다"),
    CHARGE_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_002", "충전 요청을 찾을 수 없습니다"),
    CHARGE_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT_003", "결제 금액이 요청 금액과 다릅니다"),
    PG_APPROVAL_FAILED(HttpStatus.BAD_GATEWAY, "PAYMENT_004", "결제 승인에 실패했습니다"),
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "PAYMENT_005", "포인트가 부족합니다"),
    REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "PAYMENT_006", "같은 요청을 처리 중입니다. 잠시 후 다시 시도해 주세요"),
    OWN_CONTENT(HttpStatus.BAD_REQUEST, "PAYMENT_007", "본인 콘텐츠는 구매할 수 없습니다"),
    PURCHASE_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_008", "구매 내역을 찾을 수 없습니다"),
    NOT_REFUNDABLE(HttpStatus.CONFLICT, "PAYMENT_009", "환불할 수 없는 구매입니다 (열람했거나 이미 환불됨)"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT, "PAYMENT_010", "다른 요청에 이미 사용한 요청 키입니다"),
    CHARGE_NOT_PENDING(HttpStatus.CONFLICT, "PAYMENT_011", "이미 처리된 충전 요청입니다");

    override fun getStatus() = status

    override fun getCode() = code

    override fun getMessage() = message
}
