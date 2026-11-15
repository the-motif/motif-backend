package com.motif.member.auth

import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Configuration
@EnableConfigurationProperties(JwtProperties::class)
class JwtConfig(jwtProperties: JwtProperties) {

    private val secretKey: SecretKey = SecretKeySpec(jwtProperties.secret.toByteArray(), "HmacSHA256")

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(secretKey))

    // 서명 → 만료(exp)·발급자(iss) → 차단 회원 순서로 검증
    @Bean
    fun jwtDecoder(blockedMemberStore: BlockedMemberStore): JwtDecoder {
        val decoder = NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
        decoder.setJwtValidator(
            DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(AccessTokenIssuer.ISSUER),
                BlockedMemberValidator(blockedMemberStore),
            ),
        )
        return decoder
    }
}
