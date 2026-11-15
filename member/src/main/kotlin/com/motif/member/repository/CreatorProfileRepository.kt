package com.motif.member.repository

import com.motif.member.domain.CreatorProfile
import org.springframework.data.jpa.repository.JpaRepository

interface CreatorProfileRepository : JpaRepository<CreatorProfile, Long> {

    fun findByMemberId(memberId: Long): CreatorProfile?
}
