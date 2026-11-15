package com.motif.settlement.service

import com.motif.common.exception.BusinessException
import com.motif.settlement.domain.CreatorBalance
import com.motif.settlement.domain.LedgerBucket
import com.motif.settlement.domain.LedgerType
import com.motif.settlement.domain.Payout
import com.motif.settlement.domain.PayoutStatus
import com.motif.settlement.domain.TaxCalculator
import com.motif.settlement.domain.VerifyStatus
import com.motif.settlement.domain.WithdrawalRequest
import com.motif.settlement.domain.WithdrawalStatus
import com.motif.settlement.exception.SettlementErrorCode
import com.motif.settlement.repository.BalanceLedgerRepository
import com.motif.settlement.repository.CreatorBalanceRepository
import com.motif.settlement.repository.PayoutRepository
import com.motif.settlement.repository.SettlementAccountRepository
import com.motif.settlement.repository.TaxPolicyRepository
import com.motif.settlement.repository.TaxProfileRepository
import com.motif.settlement.repository.WithdrawalRequestRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

// 출금 DB 처리 / 은행 호출은 PayoutExecutor에서 트랜잭션 밖으로
// 잔액 변경은 전부 creator_balance 행 잠금 후 → 일일 정산과 직렬화
@Service
@Transactional
class WithdrawalService(
    private val withdrawalRepository: WithdrawalRequestRepository,
    private val payoutRepository: PayoutRepository,
    private val balanceRepository: CreatorBalanceRepository,
    private val ledgerRepository: BalanceLedgerRepository,
    private val accountRepository: SettlementAccountRepository,
    private val taxProfileRepository: TaxProfileRepository,
    private val taxPolicyRepository: TaxPolicyRepository,
    private val clock: Clock,
) {

    // 신청 즉시 AVAILABLE 차감 (같은 돈으로 두 번 신청 불가)
    fun request(creatorId: Long, amount: Long, idempotencyKey: String): WithdrawalRequest {
        if (amount < MIN_WITHDRAWAL) {
            throw BusinessException(SettlementErrorCode.INVALID_WITHDRAWAL_AMOUNT)
        }
        val balance = lockBalance(creatorId)
        // 잠금 뒤 확인 → 같은 키 동시 요청도 하나만
        withdrawalRepository.findByIdempotencyKey(idempotencyKey)?.let {
            if (it.creatorId != creatorId || it.amount != amount) {
                throw BusinessException(SettlementErrorCode.IDEMPOTENCY_KEY_REUSED)
            }
            return it
        }
        if (!isVerified(creatorId)) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_INFO_NOT_VERIFIED)
        }
        if (balance.availableAmount < amount) {
            throw BusinessException(SettlementErrorCode.INSUFFICIENT_AVAILABLE)
        }
        val request = withdrawalRepository.save(WithdrawalRequest(creatorId, amount, idempotencyKey))
        ledgerRepository.save(balance.apply(LedgerBucket.AVAILABLE, LedgerType.WITHDRAWAL, -amount, request.id!!))
        return request
    }

    fun reject(adminId: Long, withdrawalId: Long, reason: String): WithdrawalRequest {
        val request = lockRequested(withdrawalId)
        request.reject(adminId, reason, LocalDateTime.now(clock))
        val balance = lockBalance(request.creatorId)
        ledgerRepository.save(balance.apply(LedgerBucket.AVAILABLE, LedgerType.WITHDRAWAL_REJECT, request.amount, withdrawalId))
        return request
    }

    // 승인 = 세금 계산 + 계좌 스냅샷으로 지급 건 생성 / 송금은 커밋 후 PayoutExecutor
    fun approve(adminId: Long, withdrawalId: Long): Payout {
        val request = lockRequested(withdrawalId)
        val creatorId = request.creatorId
        val account = accountRepository.findByCreatorId(creatorId)
        val taxProfile = taxProfileRepository.findByCreatorId(creatorId)
        if (account?.verifyStatus != VerifyStatus.VERIFIED || taxProfile?.verifyStatus != VerifyStatus.VERIFIED) {
            throw BusinessException(SettlementErrorCode.SETTLEMENT_INFO_NOT_VERIFIED)
        }
        val now = LocalDateTime.now(clock)
        val policy = taxPolicyRepository
            .findFirstByCreatorTypeAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(taxProfile.creatorType, now)
            ?: error("세율 정책 없음 type=${taxProfile.creatorType}")

        request.approve(adminId, now)
        return payoutRepository.save(
            Payout(
                payoutNo = "PO-" + UUID.randomUUID().toString().replace("-", ""),
                withdrawalRequestId = withdrawalId,
                creatorId = creatorId,
                creatorType = taxProfile.creatorType,
                tax = TaxCalculator.calculate(request.amount, policy.incomeTaxRate, policy.localTaxRate),
                bankCode = account.bankCode,
                accountNumber = account.accountNumber,
            ),
        )
    }

    // 송금 성공 / 이미 처리됐으면 그대로
    fun completePayout(payoutId: Long): Payout {
        val payout = lockPayout(payoutId)
        if (payout.status != PayoutStatus.PENDING) return payout
        payout.complete(LocalDateTime.now(clock))
        withdrawalRepository.findByIdForUpdate(payout.withdrawalRequestId)!!.markPaid()
        return payout
    }

    // 송금 최종 실패 → 세전 금액을 AVAILABLE로 복구, 창작자가 다시 신청
    fun failPayout(payoutId: Long, reason: String): Payout {
        val payout = lockPayout(payoutId)
        if (payout.status != PayoutStatus.PENDING) return payout
        payout.fail(reason)
        val request = withdrawalRepository.findByIdForUpdate(payout.withdrawalRequestId)!!
        request.markFailed()
        val balance = lockBalance(payout.creatorId)
        ledgerRepository.save(balance.apply(LedgerBucket.AVAILABLE, LedgerType.PAYOUT_FAIL, payout.grossAmount, request.id!!))
        return payout
    }

    @Transactional(readOnly = true)
    fun isVerified(creatorId: Long): Boolean =
        accountRepository.findByCreatorId(creatorId)?.verifyStatus == VerifyStatus.VERIFIED &&
            taxProfileRepository.findByCreatorId(creatorId)?.verifyStatus == VerifyStatus.VERIFIED

    private fun lockBalance(creatorId: Long): CreatorBalance {
        balanceRepository.insertIfAbsent(creatorId, LocalDateTime.now(clock))
        return balanceRepository.findByCreatorIdForUpdate(creatorId)!!
    }

    private fun lockRequested(withdrawalId: Long): WithdrawalRequest {
        val request = withdrawalRepository.findByIdForUpdate(withdrawalId)
            ?: throw BusinessException(SettlementErrorCode.WITHDRAWAL_NOT_FOUND)
        if (request.status != WithdrawalStatus.REQUESTED) {
            throw BusinessException(SettlementErrorCode.WITHDRAWAL_ALREADY_REVIEWED)
        }
        return request
    }

    private fun lockPayout(payoutId: Long): Payout =
        payoutRepository.findByIdForUpdate(payoutId) ?: throw BusinessException(SettlementErrorCode.PAYOUT_NOT_FOUND)

    companion object {
        const val MIN_WITHDRAWAL = 10_000L
    }
}
