package com.motif.payment.dto

import com.motif.payment.domain.ChargeOrder
import com.motif.payment.domain.ChargeStatus
import com.motif.payment.domain.PointTransaction
import com.motif.payment.domain.PointTxType
import com.motif.payment.domain.PointWallet
import com.motif.payment.domain.Purchase
import com.motif.payment.domain.PurchaseStatus
import com.motif.payment.domain.PurchaseTarget
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class WalletResponse(
    val paidBalance: Long,
    val freeBalance: Long,
    val totalBalance: Long,
) {
    companion object {
        val EMPTY = WalletResponse(0, 0, 0)

        fun from(wallet: PointWallet) = WalletResponse(wallet.paidBalance, wallet.freeBalance, wallet.totalBalance)
    }
}

data class ChargeRequest(
    @Schema(example = "10000")
    @field:NotNull
    @field:Positive
    val amount: Long? = null,
)

// 앱은 orderNo·amount로 PG 결제창 호출 → 받은 paymentKey로 confirm
data class ChargeConfirmRequest(
    @Schema(example = "CHG-...")
    @NotBlank
    val orderNo: String = "",

    @Schema(example = "fake-payment-key-1")
    @NotBlank
    @Size(max = 200)
    val paymentKey: String = "",

    @Schema(example = "10000")
    @field:NotNull
    @field:Positive
    val amount: Long? = null,
)

data class ChargeResponse(
    val orderNo: String,
    val amount: Long,
    val status: ChargeStatus,
    val paidAt: LocalDateTime?,
) {
    companion object {
        fun from(order: ChargeOrder) = ChargeResponse(order.orderNo, order.amount, order.status, order.paidAt)
    }
}

data class PurchaseContentRequest(
    @Schema(example = "1")
    @field:NotNull
    @field:Positive
    val contentId: Long? = null,
)

data class RefundRequest(
    @Schema(example = "잘못 구매")
    @NotBlank
    @Size(max = 255)
    val reason: String = "",
)

data class PurchaseResponse(
    val purchaseId: Long,
    val targetType: PurchaseTarget,
    val targetId: Long,
    val sellerId: Long,
    val listPrice: Long,
    val paidPointAmount: Long,
    val freePointAmount: Long,
    val status: PurchaseStatus,
    val purchasedAt: LocalDateTime,
    val refundedAt: LocalDateTime?,
) {
    companion object {
        fun from(purchase: Purchase) = PurchaseResponse(
            purchaseId = purchase.id!!,
            targetType = purchase.targetType,
            targetId = purchase.targetId,
            sellerId = purchase.sellerId,
            listPrice = purchase.listPrice,
            paidPointAmount = purchase.paidPointAmount,
            freePointAmount = purchase.freePointAmount,
            status = purchase.status,
            purchasedAt = purchase.createdAt,
            refundedAt = purchase.refundedAt,
        )
    }
}

data class PointHistoryResponse(
    val type: PointTxType,
    val amount: Long,
    val balanceAfter: Long,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(tx: PointTransaction) = PointHistoryResponse(tx.type, tx.amount, tx.balanceAfter, tx.createdAt)
    }
}
