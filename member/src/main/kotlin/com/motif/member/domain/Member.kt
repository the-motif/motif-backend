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

@Entity
class Member(
    email: String,
    password: String,
    nickname: String,
) : BaseTimeEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(nullable = false, unique = true)
    var email: String = email
        protected set

    // BCrypt 해시
    @Column(nullable = false)
    var password: String = password
        protected set

    @Column(nullable = false, unique = true, length = 30)
    var nickname: String = nickname
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var role: MemberRole = MemberRole.READER
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: MemberStatus = MemberStatus.ACTIVE
        protected set

    var suspendedReason: String? = null
        protected set

    var withdrawnAt: LocalDateTime? = null
        protected set

    fun isActive() = status == MemberStatus.ACTIVE

    // 첫 관리자 생성용 / AdminAccountInitializer만 사용
    fun grantAdmin() {
        role = MemberRole.ADMIN
    }

    fun promoteToCreator() {
        check(role == MemberRole.READER) { "READER만 창작자 전환 가능 id=$id" }
        role = MemberRole.CREATOR
    }

    fun suspend(reason: String) {
        check(status == MemberStatus.ACTIVE) { "ACTIVE만 정지 가능 id=$id" }
        status = MemberStatus.SUSPENDED
        suspendedReason = reason
    }

    fun unsuspend() {
        check(status == MemberStatus.SUSPENDED) { "SUSPENDED만 해제 가능 id=$id" }
        status = MemberStatus.ACTIVE
        suspendedReason = null
    }

    // 이메일·닉네임은 유니크 유지 / 같은 이메일 재가입 불가
    fun withdraw(now: LocalDateTime) {
        check(status == MemberStatus.ACTIVE) { "ACTIVE만 탈퇴 가능 id=$id" }
        status = MemberStatus.WITHDRAWN
        withdrawnAt = now
    }
}
