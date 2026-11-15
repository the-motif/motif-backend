package com.motif.member.service

import com.motif.common.exception.BusinessException
import com.motif.member.auth.BlockedMemberStore
import com.motif.member.auth.RefreshTokenStore
import com.motif.member.domain.Member
import com.motif.member.domain.MemberStatus
import com.motif.member.dto.AdminMemberResponse
import com.motif.member.exception.MemberErrorCode
import com.motif.member.repository.MemberRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

// 탈퇴·정지 / DB 변경 후 Redis로 토큰 폐기·차단
@Service
@Transactional
class MemberStatusService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
    private val refreshTokenStore: RefreshTokenStore,
    private val blockedMemberStore: BlockedMemberStore,
    private val clock: Clock,
) {

    fun withdraw(memberId: Long, password: String) {
        val member = findMember(memberId)
        if (!passwordEncoder.matches(password, member.password)) {
            throw BusinessException(MemberErrorCode.PASSWORD_MISMATCH)
        }
        requireStatus(member, MemberStatus.ACTIVE)

        member.withdraw(LocalDateTime.now(clock))
        memberRepository.flush()
        logoutEverywhere(memberId)
    }

    fun suspend(memberId: Long, reason: String): AdminMemberResponse {
        val member = findMember(memberId)
        requireStatus(member, MemberStatus.ACTIVE)

        member.suspend(reason)
        memberRepository.flush()
        logoutEverywhere(memberId)
        return AdminMemberResponse.from(member)
    }

    fun unsuspend(memberId: Long): AdminMemberResponse {
        val member = findMember(memberId)
        requireStatus(member, MemberStatus.SUSPENDED)

        member.unsuspend()
        memberRepository.flush()
        blockedMemberStore.unblock(memberId)
        return AdminMemberResponse.from(member)
    }

    // 리프레시 토큰 전부 폐기 + 남은 액세스 토큰 차단
    private fun logoutEverywhere(memberId: Long) {
        refreshTokenStore.revokeAll(memberId)
        blockedMemberStore.block(memberId)
    }

    private fun findMember(memberId: Long): Member =
        memberRepository.findByIdOrNull(memberId) ?: throw BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)

    private fun requireStatus(member: Member, expected: MemberStatus) {
        if (member.status != expected) {
            throw BusinessException(MemberErrorCode.INVALID_STATUS_CHANGE)
        }
    }
}
