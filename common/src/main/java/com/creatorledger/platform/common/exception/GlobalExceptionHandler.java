package com.creatorledger.platform.common.exception;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException: code={}, message={}", errorCode.getCode(), e.getMessage());
        return toResponse(errorCode, ErrorResponse.of(errorCode, e.getMessage()));
    }

    // @Valid @RequestBody 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleNotValid(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new ErrorResponse.FieldError(error.getField(), error.getDefaultMessage()))
                .toList();
        return toResponse(CommonErrorCode.INVALID_INPUT, ErrorResponse.of(CommonErrorCode.INVALID_INPUT, errors));
    }

    // @RequestParam·@PathVariable 제약 검증 실패
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException e) {
        List<ErrorResponse.FieldError> errors = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ErrorResponse.FieldError(
                                result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
                .toList();
        return toResponse(CommonErrorCode.INVALID_INPUT, ErrorResponse.of(CommonErrorCode.INVALID_INPUT, errors));
    }

    // JSON 형식 오류, 필수 파라미터 누락, 타입 불일치
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        log.debug("Bad request: {}", e.getMessage());
        return toResponse(CommonErrorCode.INVALID_INPUT, ErrorResponse.of(CommonErrorCode.INVALID_INPUT));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e) {
        return toResponse(CommonErrorCode.NOT_FOUND, ErrorResponse.of(CommonErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return toResponse(CommonErrorCode.METHOD_NOT_ALLOWED, ErrorResponse.of(CommonErrorCode.METHOD_NOT_ALLOWED));
    }

    // 예상 못 한 예외 / 내부 메시지는 응답에 노출 금지
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected exception", e);
        return toResponse(CommonErrorCode.INTERNAL_ERROR, ErrorResponse.of(CommonErrorCode.INTERNAL_ERROR));
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode, ErrorResponse body) {
        return ResponseEntity.status(errorCode.getStatus()).body(body);
    }
}
