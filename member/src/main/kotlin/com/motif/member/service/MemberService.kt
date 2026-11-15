package com.motif.member.service

import com.motif.common.exception.BusinessException
import com.motif.member.domain.Member
import com.motif.member.dto.MemberResponse
import com.motif.member.dto.SignupRequest
import com.motif.member.exception.MemberErrorCode
import com.motif.member.repository.MemberRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MemberService(
    private val memberRepository: MemberRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    @Transactional
    fun signup(request: SignupRequest): MemberResponse {
        val email = normalizeEmail(request.email)
        if (memberRepository.existsByEmail(email)) {
            throw BusinessException(MemberErrorCode.DUPLICATE_EMAIL)
        }
        if (memberRepository.existsByNickname(request.nickname)) {
            throw BusinessException(MemberErrorCode.DUPLICATE_NICKNAME)
        }

        val member = Member(email, passwordEncoder.encode(request.password)!!, request.nickname)
        try {
            memberRepository.saveAndFlush(member)
        } catch (e: DataIntegrityViolationException) {
            // 동시 가입으로 위 확인을 통과한 경우 / 유니크 제약 이름으로 구분
            val errorCode = if (e.message.orEmpty().contains("uk_member_email")) {
                MemberErrorCode.DUPLICATE_EMAIL
            } else {
                MemberErrorCode.DUPLICATE_NICKNAME
            }
            throw BusinessException(errorCode)
        }
        return MemberResponse.from(member)
    }

    fun getMember(memberId: Long): MemberResponse {
        val member = memberRepository.findByIdOrNull(memberId)
            ?: throw BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)
        return MemberResponse.from(member)
    }

    companion object {
        fun normalizeEmail(email: String) = email.trim().lowercase()
    }
}
