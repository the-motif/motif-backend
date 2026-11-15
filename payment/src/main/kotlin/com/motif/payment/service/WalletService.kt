package com.motif.payment.service

import com.motif.common.response.SliceResponse
import com.motif.payment.domain.PointWallet
import com.motif.payment.dto.PointHistoryResponse
import com.motif.payment.dto.WalletResponse
import com.motif.payment.repository.PointTransactionRepository
import com.motif.payment.repository.PointWalletRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class WalletService(
    private val walletRepository: PointWalletRepository,
    private val transactionRepository: PointTransactionRepository,
    private val clock: Clock,
) {

    // 지갑은 첫 충전 때 생성 / 동시 요청에도 1개
    @Transactional
    fun getOrCreate(memberId: Long): PointWallet {
        walletRepository.findByMemberId(memberId)?.let { return it }
        walletRepository.insertIfAbsent(memberId, LocalDateTime.now(clock))
        return walletRepository.findByMemberId(memberId)!!
    }

    fun getBalance(memberId: Long): WalletResponse =
        walletRepository.findByMemberId(memberId)?.let(WalletResponse::from) ?: WalletResponse.EMPTY

    fun getHistory(memberId: Long, pageable: Pageable): SliceResponse<PointHistoryResponse> {
        val wallet = walletRepository.findByMemberId(memberId)
            ?: return SliceResponse(emptyList(), pageable.pageNumber, pageable.pageSize, false)
        return SliceResponse.of(
            transactionRepository.findAllByWalletIdOrderByIdDesc(wallet.id!!, pageable),
            PointHistoryResponse::from,
        )
    }

    // UPDATE 후 다시 읽은 총 잔액 / 원장 balance_after용
    fun currentTotal(walletId: Long): Long = walletRepository.findById(walletId).get().totalBalance
}
