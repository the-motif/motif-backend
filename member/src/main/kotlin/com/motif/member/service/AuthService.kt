package com.motif.member.service

import com.motif.common.exception.BusinessException
import com.motif.member.auth.AccessTokenIssuer
import com.motif.member.auth.JwtProperties
import com.motif.member.auth.RefreshTokenStore
import com.motif.member.domain.Member
import com.motif.member.dto.LoginRequest
import com.motif.member.dto.TokenResponse
import com.motif.member.exception.MemberErrorCode
import com.motif.member.repository.MemberRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AuthService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val accessTokenIssuer: AccessTokenIssuer,
    private val refreshTokenStore: RefreshTokenStore,
    private val jwtProperties: JwtProperties,
) {

    fun login(request: LoginRequest): TokenResponse {
        val member = memberRepository.findByEmail(MemberService.normalizeEmail(request.email))
        if (member == null || !passwordEncoder.matches(request.password, member.password)) {
            throw BusinessException(MemberErrorCode.LOGIN_FAILED)
        }
        return issueTokens(member)
    }

    // 리프레시 토큰 회전 / 쓴 토큰은 폐기하고 새로 발급
    fun refresh(refreshToken: String): TokenResponse {
        val memberId = refreshTokenStore.consume(refreshToken)
            ?: throw BusinessException(MemberErrorCode.INVALID_REFRESH_TOKEN)
        val member = memberRepository.findByIdOrNull(memberId)
            ?: throw BusinessException(MemberErrorCode.INVALID_REFRESH_TOKEN)
        return issueTokens(member)
    }

    // 액세스 토큰은 만료까지 유효
    fun logout(refreshToken: String) {
        refreshTokenStore.revoke(refreshToken)
    }

    private fun issueTokens(member: Member): TokenResponse {
        if (!member.isActive()) {
            throw BusinessException(MemberErrorCode.INACTIVE_MEMBER)
        }
        val memberId = member.id!!
        return TokenResponse(
            accessToken = accessTokenIssuer.issue(memberId, member.role),
            refreshToken = refreshTokenStore.issue(memberId),
            expiresIn = jwtProperties.accessTokenTtl.seconds,
        )
    }
}
