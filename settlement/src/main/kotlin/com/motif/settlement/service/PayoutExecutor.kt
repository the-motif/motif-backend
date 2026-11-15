package com.motif.settlement.service

import com.motif.common.exception.BusinessException
import com.motif.common.lock.RedisDistributedLock
import com.motif.settlement.bank.BankTemporaryException
import com.motif.settlement.bank.BankTransferGateway
import com.motif.settlement.bank.TransferResult
import com.motif.settlement.domain.Payout
import com.motif.settlement.domain.PayoutStatus
import com.motif.settlement.exception.SettlementErrorCode
import com.motif.settlement.repository.PayoutRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration

// 송금 실행 / 트랜잭션 없음 (은행 응답 기다리는 동안 DB 커넥션 안 잡음)
// 일시 오류는 PENDING 유지 → 같은 payoutNo로 재시도 (은행이 중복 송금 거름)
@Service
class PayoutExecutor(
    private val payoutRepository: PayoutRepository,
    private val withdrawalService: WithdrawalService,
    private val bankGateway: BankTransferGateway,
    private val distributedLock: RedisDistributedLock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun execute(payoutId: Long): Payout {
        val lockKey = "payout:$payoutId"
        val token = distributedLock.tryLock(lockKey, LOCK_LEASE)
            ?: throw BusinessException(SettlementErrorCode.PAYOUT_IN_PROGRESS)
        try {
            val payout = payoutRepository.findById(payoutId)
                .orElseThrow { BusinessException(SettlementErrorCode.PAYOUT_NOT_FOUND) }
            if (payout.status != PayoutStatus.PENDING) return payout

            val result = try {
                bankGateway.transfer(payout.payoutNo, payout.bankCode, payout.accountNumber, payout.netAmount)
            } catch (e: BankTemporaryException) {
                log.warn("송금 일시 오류, 재시도 대기 payoutNo={}", payout.payoutNo, e)
                return payout
            }
            return when (result) {
                TransferResult.Success -> withdrawalService.completePayout(payoutId)
                is TransferResult.Failure -> withdrawalService.failPayout(payoutId, result.reason)
            }
        } finally {
            distributedLock.unlock(lockKey, token)
        }
    }

    // 관리자 수동 재시도
    fun retry(payoutId: Long): Payout {
        val payout = payoutRepository.findById(payoutId)
            .orElseThrow { BusinessException(SettlementErrorCode.PAYOUT_NOT_FOUND) }
        if (payout.status != PayoutStatus.PENDING) {
            throw BusinessException(SettlementErrorCode.PAYOUT_NOT_RETRYABLE)
        }
        return execute(payoutId)
    }

    // 일시 오류로 남은 지급 자동 재시도
    @Scheduled(fixedDelayString = "\${motif.payout.retry-delay-ms:600000}", initialDelayString = "\${motif.payout.retry-delay-ms:600000}")
    fun retryPending() {
        payoutRepository.findAllByStatusOrderByIdAsc(PayoutStatus.PENDING).forEach { payout ->
            try {
                execute(payout.id!!)
            } catch (e: Exception) {
                log.warn("지급 재시도 실패 payoutId={}", payout.id, e)
            }
        }
    }

    companion object {
        private val LOCK_LEASE: Duration = Duration.ofMinutes(1)
    }
}
