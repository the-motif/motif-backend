package com.motif.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @Schema(example = "reader@motif.com")
    @NotBlank
    val email: String = "",

    @Schema(example = "password123")
    @NotBlank
    val password: String = "",
)
