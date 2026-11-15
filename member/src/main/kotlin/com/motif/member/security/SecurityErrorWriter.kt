package com.motif.member.security

import com.motif.common.exception.ErrorCode
import com.motif.common.exception.ErrorResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

// Security 필터 단계 에러 / GlobalExceptionHandler와 같은 응답 모양
@Component
class SecurityErrorWriter(
    private val jsonMapper: JsonMapper,
) {

    fun write(response: HttpServletResponse, errorCode: ErrorCode) {
        response.status = errorCode.status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        jsonMapper.writeValue(response.writer, ErrorResponse.of(errorCode))
    }
}
