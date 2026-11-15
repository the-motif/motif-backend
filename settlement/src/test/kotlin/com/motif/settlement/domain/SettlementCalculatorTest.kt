package com.motif.settlement.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class SettlementCalculatorTest {

    private val holdRate = BigDecimal("0.1000")

    @Test
    fun `수수료는 원 미만 내림`() {
        assertThat(SettlementCalculator.fee(3000, BigDecimal("0.1500"))).isEqualTo(450)
        assertThat(SettlementCalculator.fee(999, BigDecimal("0.1500"))).isEqualTo(149)
        assertThat(SettlementCalculator.fee(0, BigDecimal("0.1500"))).isEqualTo(0)
    }

    @Test
    fun `판매만 있는 날 - 순매출의 10% 보류, 나머지 지급 가능`() {
        val result = calc(gross = 10_000, fee = 1_500)

        assertThat(result.netAmount).isEqualTo(8_500)
        assertThat(result.holdAmount).isEqualTo(850)
        assertThat(result.payableAmount).isEqualTo(7_650)
        assertThat(result.availableIncrease).isEqualTo(7_650)
    }

    @Test
    fun `보류금 내림 - 8,505원의 10%는 850`() {
        assertThat(calc(gross = 8_505).holdAmount).isEqualTo(850)
    }

    @Test
    fun `환불이 더 많은 날 - 보류 없음, 음수는 이월`() {
        val result = calc(gross = 1_000, refund = 3_000, fee = 150 - 450)

        assertThat(result.netAmount).isEqualTo(-1_700)
        assertThat(result.holdAmount).isZero()
        assertThat(result.payableAmount).isEqualTo(-1_700)
        assertThat(result.availableIncrease).isZero()
    }

    @Test
    fun `보류금 해제와 전날 이월이 같이 반영`() {
        val result = calc(gross = 5_000, fee = 750, release = 850, carry = -1_700)

        // 4,250 - 425 + 850 - 1,700
        assertThat(result.payableAmount).isEqualTo(2_975)
    }

    @Test
    fun `판매 없이 보류금만 풀리는 날`() {
        assertThat(calc(release = 850).payableAmount).isEqualTo(850)
    }

    @Test
    fun `잘못된 입력 - 이월이 양수면 거부`() {
        assertThrows<IllegalArgumentException> { calc(carry = 100) }
    }

    private fun calc(gross: Long = 0, refund: Long = 0, fee: Long = 0, release: Long = 0, carry: Long = 0) =
        SettlementCalculator.calculate(SettlementInput(gross, refund, fee, holdRate, release, carry))
}
