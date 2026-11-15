package com.motif.member.dto

import com.motif.member.domain.ApplicationStatus
import com.motif.member.domain.CreatorApplication
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CreatorApplyRequest(
    @Schema(example = "여행 에세이를 연재합니다")
    @NotBlank
    @Size(max = 500)
    val intro: String = "",

    @Schema(example = "https://blog.example.com")
    @Size(max = 500)
    @Pattern(regexp = "^https?://.*", message = "http(s)://로 시작하는 주소를 입력해 주세요")
    val portfolioUrl: String? = null,
)

data class CreatorRejectRequest(
    @NotBlank
    @Size(max = 255)
    val reason: String = "",
)

data class CreatorApplicationResponse(
    val id: Long,
    val memberId: Long,
    val intro: String,
    val portfolioUrl: String?,
    val status: ApplicationStatus,
    val rejectReason: String?,
    val reviewedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(application: CreatorApplication) = CreatorApplicationResponse(
            id = application.id!!,
            memberId = application.memberId,
            intro = application.intro,
            portfolioUrl = application.portfolioUrl,
            status = application.status,
            rejectReason = application.rejectReason,
            reviewedAt = application.reviewedAt,
            createdAt = application.createdAt,
        )
    }
}
