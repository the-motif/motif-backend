package com.motif.member.dto

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    // 액세스 토큰 만료까지 초
    val expiresIn: Long,
    val tokenType: String = "Bearer",
)
