package com.motif.payment.service

import com.motif.common.exception.BusinessException
import com.motif.payment.domain.ChargeOrder
import com.motif.payment.domain.ChargeStatus
import com.motif.payment.domain.LotSource
import com.motif.payment.domain.PointKind
import com.motif.payment.domain.PointLot
import com.motif.payment.domain.PointRefType
import com.motif.payment.domain.PointTransaction
import com.motif.payment.domain.PointTxType
import com.motif.payment.exception.PaymentErrorCode
import com.motif.payment.pg.PgApproval
import com.motif.payment.repository.ChargeOrderRepository
import com.motif.payment.repository.PointLotRepository
import com.motif.payment.repository.PointTransactionRepository
import com.motif.payment.repository.PointWalletRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

// 충전 DB 처리 / PG 호출은 ChargeFacade에서 트랜잭션 밖으로
@Service
@Transactional
class ChargeService(
    private val chargeOrderRepository: ChargeOrderRepository,
    private val walletRepository: PointWalletRepository,
    private val lotRepository: PointLotRepository,
    private val transactionRepository: PointTransactionRepository,
    private val walletService: WalletService,
    private val clock: Clock,
) {

    fun createOrder(memberId: Long, amount: Long, idempotencyKey: String): ChargeOrder {
        validateAmount(amount)
        val orderNo = "CHG-" + UUID.randomUUID().toString().replace("-", "")
        return chargeOrderRepository.save(ChargeOrder(orderNo, memberId, amount, idempotencyKey))
    }

    // 승인 결과 반영: 주문 PAID → 지갑 증가 → lot 생성 → 원장 / 한 트랜잭션
    fun complete(orderNo: String, provider: String, approval: PgApproval): ChargeOrder {
        val order = chargeOrderRepository.findByOrderNoForUpdate(orderNo)
            ?: throw BusinessException(PaymentErrorCode.CHARGE_NOT_FOUND)
        if (order.status != ChargeStatus.PENDING) {
            throw BusinessException(PaymentErrorCode.CHARGE_NOT_PENDING)
        }
        val now = LocalDateTime.now(clock)
        order.markPaid(provider, approval.paymentKey, approval.payMethod, now)

        val walletId = walletService.getOrCreate(order.memberId).id!!
        walletRepository.increasePaid(walletId, order.amount, now)
        lotRepository.save(
            PointLot(walletId, PointKind.PAID, LotSource.CHARGE, order.id!!, order.amount, now.plusYears(PAID_POINT_VALID_YEARS)),
        )
        transactionRepository.save(
            PointTransaction(
                walletId = walletId,
                type = PointTxType.CHARGE,
                paidAmount = order.amount,
                freeAmount = 0,
                balanceAfter = walletService.currentTotal(walletId),
                refType = PointRefType.CHARGE_ORDER,
                refId = order.id!!,
            ),
        )
        // increasePaid가 영속성 컨텍스트를 비워서 다시 읽어 반환
        return chargeOrderRepository.findByOrderNo(orderNo)!!
    }

    fun fail(orderNo: String) {
        val order = chargeOrderRepository.findByOrderNoForUpdate(orderNo) ?: return
        if (order.status == ChargeStatus.PENDING) {
            order.markFailed()
        }
    }

    private fun validateAmount(amount: Long) {
        if (amount !in MIN_CHARGE..MAX_CHARGE || amount % CHARGE_UNIT != 0L) {
            throw BusinessException(PaymentErrorCode.INVALID_CHARGE_AMOUNT)
        }
    }

    companion object {
        const val MIN_CHARGE = 1_000L
        const val MAX_CHARGE = 1_000_000L
        const val CHARGE_UNIT = 100L
        const val PAID_POINT_VALID_YEARS = 5L
    }
}
