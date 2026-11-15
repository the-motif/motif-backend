package com.motif.common.exception;

import java.util.List;

public record ErrorResponse(String code, String message, List<FieldError> errors) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getCode(), message, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), errors);
    }

    // 검증 실패 필드별 사유
    public record FieldError(String field, String reason) {
    }
}
