package com.motif.payment.pg

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime

// 모의 PG / paymentKey가 "fail"로 시작하면 승인 실패
// 실제 PG 연동 시 같은 인터페이스로 교체
@Component
class FakePaymentGateway(
    private val clock: Clock,
) : PaymentGateway {

    override val provider: String = "FAKE"

    override fun confirm(paymentKey: String, orderNo: String, amount: Long): PgApproval {
        if (paymentKey.startsWith("fail")) {
            throw PgApprovalException("모의 승인 실패 orderNo=$orderNo")
        }
        return PgApproval(paymentKey, "CARD", LocalDateTime.now(clock))
    }
}
