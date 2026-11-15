package com.motif.settlement.exception

import com.motif.common.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class SettlementErrorCode(
    private val status: HttpStatus,
    private val code: String,
    private val message: String,
) : ErrorCode {

    SETTLEMENT_RUNNING(HttpStatus.CONFLICT, "SETTLEMENT_001", "같은 날짜 정산이 실행 중입니다"),
    FUTURE_SETTLEMENT_DATE(HttpStatus.BAD_REQUEST, "SETTLEMENT_002", "미래 날짜는 정산할 수 없습니다"),
    INVALID_FEE_RATE(HttpStatus.BAD_REQUEST, "SETTLEMENT_003", "수수료율은 0~50%, 소수점 넷째 자리까지입니다"),
    RETROACTIVE_POLICY(HttpStatus.BAD_REQUEST, "SETTLEMENT_004", "적용·종료 시각은 현재 이후여야 합니다 (소급 불가)"),
    NOT_A_CREATOR(HttpStatus.BAD_REQUEST, "SETTLEMENT_005", "활동 중인 창작자가 아닙니다"),
    FEE_POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "SETTLEMENT_006", "수수료 정책을 찾을 수 없습니다"),
    FEE_POLICY_NOT_ENDABLE(HttpStatus.CONFLICT, "SETTLEMENT_007", "종료할 수 없는 정책입니다 (기본 요율이거나 이미 종료됨)"),
    SETTLEMENT_INFO_NOT_VERIFIED(HttpStatus.CONFLICT, "SETTLEMENT_008", "정산 계좌와 세금 정보 확인이 끝나야 출금할 수 있습니다"),
    INSUFFICIENT_AVAILABLE(HttpStatus.CONFLICT, "SETTLEMENT_009", "출금 가능 금액이 부족합니다"),
    INVALID_WITHDRAWAL_AMOUNT(HttpStatus.BAD_REQUEST, "SETTLEMENT_010", "출금은 10,000원 이상입니다"),
    WITHDRAWAL_NOT_FOUND(HttpStatus.NOT_FOUND, "SETTLEMENT_011", "출금 신청을 찾을 수 없습니다"),
    WITHDRAWAL_ALREADY_REVIEWED(HttpStatus.CONFLICT, "SETTLEMENT_012", "이미 처리된 출금 신청입니다"),
    PAYOUT_NOT_RETRYABLE(HttpStatus.CONFLICT, "SETTLEMENT_013", "재시도할 수 없는 지급입니다 (완료 또는 실패)"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT, "SETTLEMENT_014", "다른 요청에 이미 사용한 요청 키입니다"),
    SETTLEMENT_INFO_NOT_FOUND(HttpStatus.NOT_FOUND, "SETTLEMENT_015", "등록된 정산 정보가 없습니다"),
    INVALID_TAX_PROFILE(HttpStatus.BAD_REQUEST, "SETTLEMENT_016", "개인은 주민등록번호, 사업자는 사업자등록번호가 필요합니다"),
    PAYOUT_IN_PROGRESS(HttpStatus.CONFLICT, "SETTLEMENT_017", "같은 지급을 처리 중입니다"),
    PAYOUT_NOT_FOUND(HttpStatus.NOT_FOUND, "SETTLEMENT_018", "지급 건을 찾을 수 없습니다"),
    RECONCILIATION_RUNNING(HttpStatus.CONFLICT, "SETTLEMENT_019", "같은 날짜의 대사가 진행 중입니다"),
    INVALID_RECONCILIATION_DATE(HttpStatus.BAD_REQUEST, "SETTLEMENT_020", "대사는 지난 날짜만 실행할 수 있습니다"),
    MISMATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "SETTLEMENT_021", "대사 불일치 건을 찾을 수 없습니다"),
    MISMATCH_ALREADY_RESOLVED(HttpStatus.CONFLICT, "SETTLEMENT_022", "이미 해결된 불일치 건입니다"),
    WITHHOLDING_RUNNING(HttpStatus.CONFLICT, "SETTLEMENT_023", "같은 달의 원천세 집계가 진행 중입니다"),
    INVALID_TAX_MONTH(HttpStatus.BAD_REQUEST, "SETTLEMENT_024", "원천세 집계는 지난 달만 실행할 수 있습니다");

    override fun getStatus() = status

    override fun getCode() = code

    override fun getMessage() = message
}
