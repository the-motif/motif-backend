package com.motif.member.config

import com.motif.member.auth.AccessTokenIssuer
import com.motif.member.security.JsonAccessDeniedHandler
import com.motif.member.security.JsonAuthenticationEntryPoint
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain

// @EnableMethodSecurity: 모듈별 컨트롤러에서 @PreAuthorize로 역할 검사
@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val authenticationEntryPoint: JsonAuthenticationEntryPoint,
    private val accessDeniedHandler: JsonAccessDeniedHandler,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // 토큰 방식 API / 세션·폼 로그인 안 씀
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/api/members").permitAll()
                it.requestMatchers("/api/auth/**").permitAll()
                it.requestMatchers("/actuator/health", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // 둘러보기는 비로그인 허용 / 토큰 있으면 로그인 회원으로 처리
                it.requestMatchers(HttpMethod.GET, "/api/contents/**", "/api/categories/**").permitAll()
                it.requestMatchers("/api/admin/**").hasRole("ADMIN")
                it.anyRequest().authenticated()
            }
            // Authorization: Bearer 토큰 검증
            .oauth2ResourceServer {
                it.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()) }
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }
            .exceptionHandling {
                it.authenticationEntryPoint(authenticationEntryPoint)
                it.accessDeniedHandler(accessDeniedHandler)
            }
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    // role 클레임 → ROLE_READER 등 권한
    private fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val authoritiesConverter = JwtGrantedAuthoritiesConverter().apply {
            setAuthoritiesClaimName(AccessTokenIssuer.ROLE_CLAIM)
            setAuthorityPrefix("ROLE_")
        }
        return JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter(authoritiesConverter)
        }
    }
}
