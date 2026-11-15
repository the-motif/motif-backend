package com.motif.member.dto

import jakarta.validation.constraints.NotBlank

data class RefreshTokenRequest(
    @NotBlank
    val refreshToken: String = "",
)
