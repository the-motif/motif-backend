package com.motif.member.controller

import com.motif.member.dto.LoginRequest
import com.motif.member.dto.RefreshTokenRequest
import com.motif.member.dto.TokenResponse
import com.motif.member.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "인증")
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {

    @Operation(summary = "로그인", description = "액세스 토큰(30분) + 리프레시 토큰(14일) 발급")
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): TokenResponse =
        authService.login(request)

    @Operation(summary = "토큰 재발급", description = "쓴 리프레시 토큰은 폐기")
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): TokenResponse =
        authService.refresh(request.refreshToken)

    @Operation(summary = "로그아웃", description = "리프레시 토큰 폐기")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@Valid @RequestBody request: RefreshTokenRequest) =
        authService.logout(request.refreshToken)
}
