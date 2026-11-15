package com.motif.settlement.domain

// 대사 비교 / 키 = (구매 ID, 판매·환불), 금액은 원장 부호 그대로 (환불 음수)
object ReconciliationComparator {

    fun compare(payment: Collection<ReconTx>, settlement: Collection<ReconTx>): List<MismatchItem> {
        val paymentByKey = payment.associateBy { it.key }
        val settlementByKey = settlement.associateBy { it.key }
        return (paymentByKey.keys + settlementByKey.keys)
            .sortedWith(compareBy({ it.purchaseId }, { it.type }))
            .mapNotNull { key ->
                val p = paymentByKey[key]
                val s = settlementByKey[key]
                val type = when {
                    p != null && s != null -> if (p.amount == s.amount) null else MismatchType.AMOUNT_DIFF
                    // 환불 한쪽만 = 환불 반영 차이
                    key.type == SalesType.REFUND -> MismatchType.STATUS_DIFF
                    s == null -> MismatchType.MISSING_IN_SETTLEMENT
                    else -> MismatchType.MISSING_IN_PAYMENT
                } ?: return@mapNotNull null
                MismatchItem(key, (p ?: s)!!.creatorId, type, p?.amount, s?.amount)
            }
    }
}

data class ReconKey(val purchaseId: Long, val type: SalesType)

data class ReconTx(
    val purchaseId: Long,
    val type: SalesType,
    val creatorId: Long,
    val amount: Long,
) {
    val key get() = ReconKey(purchaseId, type)
}

data class MismatchItem(
    val key: ReconKey,
    val creatorId: Long,
    val type: MismatchType,
    val paymentAmount: Long?,
    val settlementAmount: Long?,
)
