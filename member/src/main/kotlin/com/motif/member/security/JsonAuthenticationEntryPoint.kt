package com.motif.member.security

import com.motif.common.exception.CommonErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

// 로그인 안 함 → 401
@Component
class JsonAuthenticationEntryPoint(
    private val errorWriter: SecurityErrorWriter,
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        errorWriter.write(response, CommonErrorCode.UNAUTHORIZED)
    }
}
