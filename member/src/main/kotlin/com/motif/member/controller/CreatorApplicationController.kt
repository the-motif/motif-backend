package com.motif.member.controller

import com.motif.member.auth.memberId
import com.motif.member.dto.CreatorApplicationResponse
import com.motif.member.dto.CreatorApplyRequest
import com.motif.member.service.CreatorApplicationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "창작자 전환")
@RestController
@RequestMapping("/api/creator-applications")
class CreatorApplicationController(
    private val applicationService: CreatorApplicationService,
) {

    @Operation(summary = "창작자 전환 신청", description = "READER만 / 심사 중 신청은 1개만")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun apply(
        @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: CreatorApplyRequest,
    ): CreatorApplicationResponse = applicationService.apply(jwt.memberId(), request)

    @Operation(summary = "내 신청 내역", description = "최신순")
    @GetMapping("/me")
    fun myApplications(@AuthenticationPrincipal jwt: Jwt): List<CreatorApplicationResponse> =
        applicationService.getMyApplications(jwt.memberId())
}
