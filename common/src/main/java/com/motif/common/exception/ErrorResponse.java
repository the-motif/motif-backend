package com.motif.common.exception;

import com.motif.common.trace.TraceId;
import java.util.List;
import org.jspecify.annotations.Nullable;

// traceId = 응답 헤더 X-Request-Id와 같은 값 / 문의 시 로그 찾기용
public record ErrorResponse(String code, String message, List<FieldError> errors, @Nullable String traceId) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getCode(), message, List.of(), TraceId.current());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), errors, TraceId.current());
    }

    // 검증 실패 필드별 사유
    public record FieldError(String field, String reason) {
    }
}
