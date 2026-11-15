package com.motif.member.exception

import com.motif.common.exception.ErrorCode
import org.springframework.http.HttpStatus

enum class MemberErrorCode(
    private val status: HttpStatus,
    private val code: String,
    private val message: String,
) : ErrorCode {

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "MEMBER_001", "이미 가입된 이메일입니다"),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "MEMBER_002", "이미 사용 중인 닉네임입니다"),

    // 이메일·비밀번호 중 무엇이 틀렸는지 알려주지 않음
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "MEMBER_003", "이메일 또는 비밀번호가 올바르지 않습니다"),
    INACTIVE_MEMBER(HttpStatus.FORBIDDEN, "MEMBER_004", "이용할 수 없는 계정입니다"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "MEMBER_005", "다시 로그인해 주세요"),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_006", "회원을 찾을 수 없습니다"),
    ALREADY_CREATOR(HttpStatus.CONFLICT, "MEMBER_007", "이미 창작자이거나 신청할 수 없는 계정입니다"),
    APPLICATION_PENDING(HttpStatus.CONFLICT, "MEMBER_008", "심사 중인 신청이 있습니다"),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_009", "신청을 찾을 수 없습니다"),
    APPLICATION_ALREADY_REVIEWED(HttpStatus.CONFLICT, "MEMBER_010", "이미 심사된 신청입니다"),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "MEMBER_011", "비밀번호가 올바르지 않습니다"),
    INVALID_STATUS_CHANGE(HttpStatus.CONFLICT, "MEMBER_012", "현재 상태에서 변경할 수 없습니다");

    override fun getStatus() = status

    override fun getCode() = code

    override fun getMessage() = message
}
