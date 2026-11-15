package com.motif.settlement.repository

import com.motif.settlement.domain.ReconciliationMismatch
import com.motif.settlement.domain.ReconciliationRun
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface ReconciliationRunRepository : JpaRepository<ReconciliationRun, Long> {

    @Query("select coalesce(max(r.attempt), 0) from ReconciliationRun r where r.targetDate = :date")
    fun findMaxAttempt(@Param("date") date: LocalDate): Int

    fun findAllByTargetDateOrderByAttemptDesc(targetDate: LocalDate): List<ReconciliationRun>
}

interface ReconciliationMismatchRepository : JpaRepository<ReconciliationMismatch, Long> {

    fun findAllByRunIdOrderByIdAsc(runId: Long): List<ReconciliationMismatch>

    @Query(
        """
        select m from ReconciliationMismatch m
        where m.resolvedAt is null
          and m.runId in (select r.id from ReconciliationRun r where r.targetDate = :date)
        """,
    )
    fun findUnresolvedByTargetDate(@Param("date") date: LocalDate): List<ReconciliationMismatch>

    fun findAllByResolvedAtIsNullOrderByIdAsc(pageable: Pageable): Slice<ReconciliationMismatch>

    fun findAllByResolvedAtIsNotNullOrderByIdDesc(pageable: Pageable): Slice<ReconciliationMismatch>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ReconciliationMismatch m where m.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): ReconciliationMismatch?
}
