package com.motif.member.dto

import com.motif.member.domain.Member
import com.motif.member.domain.MemberRole
import com.motif.member.domain.MemberStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class WithdrawRequest(
    // 본인 확인용
    @NotBlank
    val password: String = "",
)

data class SuspendRequest(
    @NotBlank
    @Size(max = 255)
    val reason: String = "",
)

data class AdminMemberResponse(
    val id: Long,
    val email: String,
    val nickname: String,
    val role: MemberRole,
    val status: MemberStatus,
    val suspendedReason: String?,
) {
    companion object {
        fun from(member: Member) = AdminMemberResponse(
            id = member.id!!,
            email = member.email,
            nickname = member.nickname,
            role = member.role,
            status = member.status,
            suspendedReason = member.suspendedReason,
        )
    }
}
