package com.motif.settlement.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReconciliationComparatorTest {

    @Test
    fun `양쪽 같으면 불일치 없음`() {
        val txs = listOf(sale(1, 3000), refund(1, -3000), sale(2, 1000))

        assertThat(ReconciliationComparator.compare(txs, txs)).isEmpty()
    }

    @Test
    fun `판매가 원장에 없으면 MISSING_IN_SETTLEMENT, 결제에 없으면 MISSING_IN_PAYMENT`() {
        val result = ReconciliationComparator.compare(listOf(sale(1, 3000)), listOf(sale(2, 500)))

        assertThat(result.map { it.key.purchaseId to it.type }).containsExactly(
            1L to MismatchType.MISSING_IN_SETTLEMENT,
            2L to MismatchType.MISSING_IN_PAYMENT,
        )
        assertThat(result[0].paymentAmount).isEqualTo(3000)
        assertThat(result[0].settlementAmount).isNull()
    }

    @Test
    fun `환불이 한쪽에만 있으면 STATUS_DIFF`() {
        val result = ReconciliationComparator.compare(
            listOf(sale(1, 3000), refund(1, -3000)),
            listOf(sale(1, 3000)),
        )

        assertThat(result).singleElement().satisfies({
            assertThat(it.type).isEqualTo(MismatchType.STATUS_DIFF)
            assertThat(it.key.type).isEqualTo(SalesType.REFUND)
        })
    }

    @Test
    fun `금액이 다르면 AMOUNT_DIFF, 창작자는 결제 쪽 기준`() {
        val result = ReconciliationComparator.compare(listOf(sale(1, 3000, creator = 7)), listOf(sale(1, 2000, creator = 8)))

        assertThat(result).singleElement().satisfies({
            assertThat(it.type).isEqualTo(MismatchType.AMOUNT_DIFF)
            assertThat(it.creatorId).isEqualTo(7)
            assertThat(it.paymentAmount to it.settlementAmount).isEqualTo(3000L to 2000L)
        })
    }

    private fun sale(purchaseId: Long, amount: Long, creator: Long = 7) = ReconTx(purchaseId, SalesType.SALE, creator, amount)

    private fun refund(purchaseId: Long, amount: Long) = ReconTx(purchaseId, SalesType.REFUND, 7, amount)
}
