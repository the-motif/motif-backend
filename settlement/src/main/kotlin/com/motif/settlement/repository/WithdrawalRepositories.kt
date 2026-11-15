package com.motif.settlement.repository

import com.motif.settlement.domain.CreatorType
import com.motif.settlement.domain.Payout
import com.motif.settlement.domain.PayoutStatus
import com.motif.settlement.domain.SettlementAccount
import com.motif.settlement.domain.TaxPolicy
import com.motif.settlement.domain.TaxProfile
import com.motif.settlement.domain.WithdrawalRequest
import com.motif.settlement.domain.WithdrawalStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface SettlementAccountRepository : JpaRepository<SettlementAccount, Long> {
    fun findByCreatorId(creatorId: Long): SettlementAccount?
}

interface TaxProfileRepository : JpaRepository<TaxProfile, Long> {
    fun findByCreatorId(creatorId: Long): TaxProfile?
}

interface TaxPolicyRepository : JpaRepository<TaxPolicy, Long> {

    fun findFirstByCreatorTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(
        creatorType: CreatorType,
        at: LocalDateTime,
    ): TaxPolicy?
}

interface WithdrawalRequestRepository : JpaRepository<WithdrawalRequest, Long> {

    fun findByIdempotencyKey(idempotencyKey: String): WithdrawalRequest?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WithdrawalRequest w where w.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): WithdrawalRequest?

    fun findAllByCreatorIdOrderByIdDesc(creatorId: Long, pageable: Pageable): Slice<WithdrawalRequest>

    fun findAllByStatusOrderByIdAsc(status: WithdrawalStatus, pageable: Pageable): Slice<WithdrawalRequest>
}

interface PayoutRepository : JpaRepository<Payout, Long> {

    fun findByWithdrawalRequestId(withdrawalRequestId: Long): Payout?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payout p where p.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Payout?

    fun findAllByStatusOrderByIdAsc(status: PayoutStatus): List<Payout>
}
