package com.motif.member.controller

import com.motif.member.auth.memberId
import com.motif.member.domain.ApplicationStatus
import com.motif.member.dto.CreatorApplicationResponse
import com.motif.member.dto.CreatorRejectRequest
import com.motif.member.service.CreatorApplicationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "관리자: 창작자 심사")
@RestController
@RequestMapping("/api/admin/creator-applications")
class AdminCreatorApplicationController(
    private val applicationService: CreatorApplicationService,
) {

    @Operation(summary = "신청 목록", description = "오래된 순 / page는 0부터")
    @GetMapping
    fun applications(
        @RequestParam(defaultValue = "REQUESTED") status: ApplicationStatus,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): List<CreatorApplicationResponse> =
        applicationService.getApplications(status, PageRequest.of(page, size))

    @Operation(summary = "승인", description = "회원 역할 CREATOR, 창작자 프로필 생성 / 새 역할은 토큰 재발급 후 반영")
    @PostMapping("/{id}/approve")
    fun approve(@PathVariable id: Long, @AuthenticationPrincipal jwt: Jwt): CreatorApplicationResponse =
        applicationService.approve(id, jwt.memberId())

    @Operation(summary = "반려")
    @PostMapping("/{id}/reject")
    fun reject(
        @PathVariable id: Long,
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: CreatorRejectRequest,
    ): CreatorApplicationResponse = applicationService.reject(id, jwt.memberId(), request.reason)
}
