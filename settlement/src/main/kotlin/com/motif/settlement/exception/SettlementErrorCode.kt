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
    FEE_POLICY_NOT_ENDABLE(HttpStatus.CONFLICT, "SETTLEMENT_007", "종료할 수 없는 정책입니다 (기본 요율이거나 이미 종료됨)");

    override fun getStatus() = status

    override fun getCode() = code

    override fun getMessage() = message
}
