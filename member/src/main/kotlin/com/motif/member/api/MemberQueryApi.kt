package com.motif.member.api

import com.motif.member.domain.MemberRole
import com.motif.member.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 다른 모듈이 부르는 회원 조회 / 엔티티 대신 값만 반환
@Service
@Transactional(readOnly = true)
class MemberQueryApi(
    private val memberRepository: MemberRepository,
) {

    // 회원 ID → 닉네임 / 없는 ID는 결과에서 빠짐
    fun getNicknames(memberIds: Collection<Long>): Map<Long, String> {
        if (memberIds.isEmpty()) return emptyMap()
        return memberRepository.findAllById(memberIds.toSet()).associate { it.id!! to it.nickname }
    }

    // 활동 중인 창작자인지
    fun isActiveCreator(memberId: Long): Boolean =
        memberRepository.findById(memberId).map { it.role == MemberRole.CREATOR && it.isActive() }.orElse(false)
}
