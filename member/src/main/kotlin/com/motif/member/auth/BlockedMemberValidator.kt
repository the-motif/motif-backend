package com.motif.member.auth

import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt

// 서명·만료 검증 뒤 실행 / 차단 회원이면 401
class BlockedMemberValidator(
    private val blockedMemberStore: BlockedMemberStore,
) : OAuth2TokenValidator<Jwt> {

    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        val memberId = token.subject?.toLongOrNull()
            ?: return OAuth2TokenValidatorResult.failure(ERROR)
        return if (blockedMemberStore.isBlocked(memberId)) {
            OAuth2TokenValidatorResult.failure(ERROR)
        } else {
            OAuth2TokenValidatorResult.success()
        }
    }

    companion object {
        private val ERROR = OAuth2Error("invalid_token", "blocked member", null)
    }
}
