package com.motif.payment.pg

import java.time.LocalDateTime

// PG 결제 승인 / 토스페이먼츠 승인 API 모양
interface PaymentGateway {

    val provider: String

    // 실패 시 PgApprovalException
    fun confirm(paymentKey: String, orderNo: String, amount: Long): PgApproval
}

data class PgApproval(
    val paymentKey: String,
    val payMethod: String,
    val approvedAt: LocalDateTime,
)

class PgApprovalException(message: String) : RuntimeException(message)
