package com.motif.member.auth

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("motif.jwt")
data class JwtProperties(
    // HS256 서명 키 / 32바이트 이상
    val secret: String,
    val accessTokenTtl: Duration = Duration.ofMinutes(30),
    val refreshTokenTtl: Duration = Duration.ofDays(14),
) {
    init {
        require(secret.toByteArray().size >= 32) { "motif.jwt.secret은 32바이트 이상" }
    }
}
