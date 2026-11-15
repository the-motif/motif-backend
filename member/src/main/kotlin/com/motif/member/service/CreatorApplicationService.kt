package com.motif.member.service

import com.motif.common.exception.BusinessException
import com.motif.member.domain.ApplicationStatus
import com.motif.member.domain.CreatorApplication
import com.motif.member.domain.CreatorProfile
import com.motif.member.domain.MemberRole
import com.motif.member.dto.CreatorApplicationResponse
import com.motif.member.dto.CreatorApplyRequest
import com.motif.member.exception.MemberErrorCode
import com.motif.member.repository.CreatorApplicationRepository
import com.motif.member.repository.CreatorProfileRepository
import com.motif.member.repository.MemberRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class CreatorApplicationService(
    private val memberRepository: MemberRepository,
    private val applicationRepository: CreatorApplicationRepository,
    private val profileRepository: CreatorProfileRepository,
    private val clock: Clock,
) {

    @Transactional
    fun apply(memberId: Long, request: CreatorApplyRequest): CreatorApplicationResponse {
        val member = memberRepository.findByIdOrNull(memberId)
            ?: throw BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)
        if (member.role != MemberRole.READER || !member.isActive()) {
            throw BusinessException(MemberErrorCode.ALREADY_CREATOR)
        }
        if (applicationRepository.existsByMemberIdAndStatus(memberId, ApplicationStatus.REQUESTED)) {
            throw BusinessException(MemberErrorCode.APPLICATION_PENDING)
        }

        val application = CreatorApplication(memberId, request.intro, request.portfolioUrl?.ifBlank { null })
        try {
            applicationRepository.saveAndFlush(application)
        } catch (e: DataIntegrityViolationException) {
            // 동시 신청 / uk_creator_application_pending
            throw BusinessException(MemberErrorCode.APPLICATION_PENDING)
        }
        return CreatorApplicationResponse.from(application)
    }

    fun getMyApplications(memberId: Long): List<CreatorApplicationResponse> =
        applicationRepository.findAllByMemberIdOrderByIdDesc(memberId).map(CreatorApplicationResponse::from)

    fun getApplications(status: ApplicationStatus, pageable: Pageable): List<CreatorApplicationResponse> =
        applicationRepository.findAllByStatusOrderByIdAsc(status, pageable).map(CreatorApplicationResponse::from)

    // 신청 APPROVED + 회원 CREATOR + 창작자 프로필 생성을 한 트랜잭션으로
    @Transactional
    fun approve(applicationId: Long, adminId: Long): CreatorApplicationResponse {
        val application = findRequestedForUpdate(applicationId)
        val member = memberRepository.findByIdOrNull(application.memberId)
            ?: throw BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)
        val memberId = member.id!!

        application.approve(adminId, now())
        member.promoteToCreator()
        if (profileRepository.findByMemberId(memberId) == null) {
            profileRepository.save(CreatorProfile(memberId, application.intro))
        }
        return CreatorApplicationResponse.from(application)
    }

    @Transactional
    fun reject(applicationId: Long, adminId: Long, reason: String): CreatorApplicationResponse {
        val application = findRequestedForUpdate(applicationId)
        application.reject(adminId, reason, now())
        return CreatorApplicationResponse.from(application)
    }

    private fun findRequestedForUpdate(applicationId: Long): CreatorApplication {
        val application = applicationRepository.findByIdForUpdate(applicationId)
            ?: throw BusinessException(MemberErrorCode.APPLICATION_NOT_FOUND)
        if (application.status != ApplicationStatus.REQUESTED) {
            throw BusinessException(MemberErrorCode.APPLICATION_ALREADY_REVIEWED)
        }
        return application
    }

    private fun now() = LocalDateTime.now(clock)
}
