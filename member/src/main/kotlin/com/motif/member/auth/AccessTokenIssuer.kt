package com.motif.member.auth

import com.motif.member.domain.MemberRole
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class AccessTokenIssuer(
    private val jwtEncoder: JwtEncoder,
    private val jwtProperties: JwtProperties,
    private val clock: Clock,
) {

    // sub = 회원 ID / role = 권한 (SecurityConfig에서 ROLE_ 붙여 읽음)
    fun issue(memberId: Long, role: MemberRole): String {
        val now = clock.instant()
        val claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(memberId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(jwtProperties.accessTokenTtl))
            .claim(ROLE_CLAIM, role.name)
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
    }

    companion object {
        const val ISSUER = "motif"
        const val ROLE_CLAIM = "role"
    }
}
