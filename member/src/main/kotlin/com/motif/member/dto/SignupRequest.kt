package com.motif.member.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

// 누락 필드는 ""로 받아 필드별 에러로 처리 / @Pattern은 빈 값도 거부
data class SignupRequest(
    @Schema(example = "reader@motif.com")
    @NotBlank
    @Email
    @Size(max = 255)
    val email: String = "",

    @Schema(example = "password123")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).{8,64}$", message = "영문과 숫자를 포함해 8~64자로 입력해 주세요")
    val password: String = "",

    @Schema(example = "모티프독자")
    @Pattern(regexp = "^[가-힣A-Za-z0-9_]{2,30}$", message = "한글·영문·숫자·_ 2~30자로 입력해 주세요")
    val nickname: String = "",
)
