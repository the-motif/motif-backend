package com.motif.member.security

import com.motif.common.exception.CommonErrorCode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

// 로그인은 했지만 권한 없음 → 403
@Component
class JsonAccessDeniedHandler(
    private val errorWriter: SecurityErrorWriter,
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        errorWriter.write(response, CommonErrorCode.FORBIDDEN)
    }
}
