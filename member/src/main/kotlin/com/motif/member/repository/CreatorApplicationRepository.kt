package com.motif.member.repository

import com.motif.member.domain.ApplicationStatus
import com.motif.member.domain.CreatorApplication
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface CreatorApplicationRepository : JpaRepository<CreatorApplication, Long> {

    fun existsByMemberIdAndStatus(memberId: Long, status: ApplicationStatus): Boolean

    fun findAllByMemberIdOrderByIdDesc(memberId: Long): List<CreatorApplication>

    fun findAllByStatusOrderByIdAsc(status: ApplicationStatus, pageable: Pageable): List<CreatorApplication>

    // 관리자 동시 심사 방지 / SELECT ... FOR UPDATE
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CreatorApplication a where a.id = :id")
    fun findByIdForUpdate(id: Long): CreatorApplication?
}
