package com.motif.member.controller

import com.motif.member.auth.memberId
import com.motif.member.dto.MemberResponse
import com.motif.member.dto.SignupRequest
import com.motif.member.dto.WithdrawRequest
import com.motif.member.service.MemberService
import com.motif.member.service.MemberStatusService
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

@Tag(name = "회원")
@RestController
@RequestMapping("/api/members")
class MemberController(
    private val memberService: MemberService,
    private val memberStatusService: MemberStatusService,
) {

    @Operation(summary = "회원가입", description = "가입 시 역할 READER")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun signup(@Valid @RequestBody request: SignupRequest): MemberResponse =
        memberService.signup(request)

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal jwt: Jwt): MemberResponse =
        memberService.getMember(jwt.memberId())

    @Operation(summary = "회원 탈퇴", description = "비밀번호 확인 / 모든 기기 로그아웃 / 같은 이메일 재가입 불가")
    @PostMapping("/me/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(@AuthenticationPrincipal jwt: Jwt, @Valid @RequestBody request: WithdrawRequest) =
        memberStatusService.withdraw(jwt.memberId(), request.password)
}
