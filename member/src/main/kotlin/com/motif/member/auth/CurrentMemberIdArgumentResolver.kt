package com.motif.member.auth

import com.motif.common.exception.BusinessException
import com.motif.common.exception.CommonErrorCode
import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class CurrentMemberIdArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(CurrentMemberId::class.java)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Long? {
        val jwt = SecurityContextHolder.getContext().authentication?.principal as? Jwt
        if (jwt != null) {
            return jwt.memberId()
        }
        val required = parameter.getParameterAnnotation(CurrentMemberId::class.java)!!.required
        if (required) {
            throw BusinessException(CommonErrorCode.UNAUTHORIZED)
        }
        return null
    }
}
