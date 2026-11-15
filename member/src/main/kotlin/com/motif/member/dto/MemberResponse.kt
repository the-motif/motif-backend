package com.motif.member.dto

import com.motif.member.domain.Member
import com.motif.member.domain.MemberRole
import java.time.LocalDateTime

data class MemberResponse(
    val id: Long,
    val email: String,
    val nickname: String,
    val role: MemberRole,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(member: Member) = MemberResponse(
            id = member.id!!,
            email = member.email,
            nickname = member.nickname,
            role = member.role,
            createdAt = member.createdAt,
        )
    }
}
