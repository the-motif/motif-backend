package com.motif.member.domain

import com.motif.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDateTime

// pending_member_id는 DB 생성 컬럼 / 매핑 안 함
@Entity
class CreatorApplication(
    memberId: Long,
    intro: String,
    portfolioUrl: String?,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, updatable = false)
    val memberId: Long = memberId

    @Column(nullable = false, length = 500)
    val intro: String = intro

    @Column(length = 500)
    val portfolioUrl: String? = portfolioUrl

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ApplicationStatus = ApplicationStatus.REQUESTED
        protected set

    var rejectReason: String? = null
        protected set

    // 심사한 관리자 member.id
    var reviewedBy: Long? = null
        protected set

    var reviewedAt: LocalDateTime? = null
        protected set

    fun approve(adminId: Long, now: LocalDateTime) {
        review(adminId, now)
        status = ApplicationStatus.APPROVED
    }

    fun reject(adminId: Long, reason: String, now: LocalDateTime) {
        review(adminId, now)
        status = ApplicationStatus.REJECTED
        rejectReason = reason
    }

    private fun review(adminId: Long, now: LocalDateTime) {
        check(status == ApplicationStatus.REQUESTED) { "이미 심사된 신청 id=$id" }
        reviewedBy = adminId
        reviewedAt = now
    }
}
