package com.motif.member.security

import com.motif.common.exception.CommonErrorCode
import com.motif.common.exception.ErrorResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

// @PreAuthorize 실패는 컨트롤러 안에서 발생 / GlobalExceptionHandler(Exception → 500)보다 먼저 처리
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
class SecurityExceptionAdvice {

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(e: AccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(CommonErrorCode.FORBIDDEN.status).body(ErrorResponse.of(CommonErrorCode.FORBIDDEN))

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(e: AuthenticationException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(CommonErrorCode.UNAUTHORIZED.status).body(ErrorResponse.of(CommonErrorCode.UNAUTHORIZED))
}
