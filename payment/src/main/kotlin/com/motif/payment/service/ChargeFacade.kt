package com.motif.payment.service

import com.motif.common.exception.BusinessException
import com.motif.payment.domain.ChargeStatus
import com.motif.payment.dto.ChargeResponse
import com.motif.payment.exception.PaymentErrorCode
import com.motif.payment.pg.PaymentGateway
import com.motif.payment.pg.PgApprovalException
import com.motif.payment.repository.ChargeOrderRepository
import com.motif.payment.support.IdempotencyGuard
import com.motif.payment.support.RedisLockManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

// 충전 흐름 조립 / 트랜잭션 없음 (PG 호출 동안 DB 커넥션 안 잡음)
@Service
class ChargeFacade(
    private val chargeService: ChargeService,
    private val chargeOrderRepository: ChargeOrderRepository,
    private val paymentGateway: PaymentGateway,
    private val idempotencyGuard: IdempotencyGuard,
    private val lockManager: RedisLockManager,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // 1단계: 주문 생성 / 같은 키로 다시 오면 같은 주문 반환
    fun request(memberId: Long, amount: Long, idempotencyKey: String): ChargeResponse =
        idempotencyGuard.execute(
            scope = "charge",
            key = idempotencyKey,
            findCompleted = {
                chargeOrderRepository.findByIdempotencyKey(idempotencyKey)?.let {
                    if (it.memberId != memberId || it.amount != amount) {
                        throw BusinessException(PaymentErrorCode.IDEMPOTENCY_KEY_REUSED)
                    }
                    ChargeResponse.from(it)
                }
            },
            action = { ChargeResponse.from(chargeService.createOrder(memberId, amount, idempotencyKey)) },
        )

    // 2단계: 앱이 PG 결제창에서 받은 paymentKey로 승인
    fun confirm(memberId: Long, orderNo: String, paymentKey: String, amount: Long): ChargeResponse =
        lockManager.withLock("charge:$orderNo") {
            val order = chargeOrderRepository.findByOrderNo(orderNo)
                ?.takeIf { it.memberId == memberId }
                ?: throw BusinessException(PaymentErrorCode.CHARGE_NOT_FOUND)

            // 같은 승인 재요청 → 결과만 반환
            if (order.status == ChargeStatus.PAID && order.pgPaymentKey == paymentKey) {
                return@withLock ChargeResponse.from(order)
            }
            if (order.status != ChargeStatus.PENDING) {
                throw BusinessException(PaymentErrorCode.CHARGE_NOT_PENDING)
            }
            // 앱이 보낸 금액이 주문 금액과 다르면 위변조 의심
            if (order.amount != amount) {
                throw BusinessException(PaymentErrorCode.CHARGE_AMOUNT_MISMATCH)
            }

            val approval = try {
                paymentGateway.confirm(paymentKey, orderNo, amount)
            } catch (e: PgApprovalException) {
                log.warn("PG 승인 실패 orderNo={}", orderNo, e)
                chargeService.fail(orderNo)
                throw BusinessException(PaymentErrorCode.PG_APPROVAL_FAILED)
            }

            // PG 승인 후 DB 반영 실패 → 돈은 나갔는데 포인트 없음 / 웹훅·대사로 복구 대상
            try {
                ChargeResponse.from(chargeService.complete(orderNo, paymentGateway.provider, approval))
            } catch (e: Exception) {
                log.error("PG 승인 후 충전 반영 실패 orderNo={} paymentKey={}", orderNo, paymentKey, e)
                throw e
            }
        }
}
