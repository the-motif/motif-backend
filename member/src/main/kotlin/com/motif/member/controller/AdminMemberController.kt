package com.motif.member.controller

import com.motif.member.dto.AdminMemberResponse
import com.motif.member.dto.SuspendRequest
import com.motif.member.service.MemberStatusService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "관리자: 회원")
@RestController
@RequestMapping("/api/admin/members")
class AdminMemberController(
    private val memberStatusService: MemberStatusService,
) {

    @Operation(summary = "회원 정지", description = "모든 기기 로그아웃, 남은 액세스 토큰 즉시 차단")
    @PostMapping("/{id}/suspend")
    fun suspend(@PathVariable id: Long, @Valid @RequestBody request: SuspendRequest): AdminMemberResponse =
        memberStatusService.suspend(id, request.reason)

    @Operation(summary = "정지 해제")
    @PostMapping("/{id}/unsuspend")
    fun unsuspend(@PathVariable id: Long): AdminMemberResponse =
        memberStatusService.unsuspend(id)
}
