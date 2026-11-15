package com.motif.settlement.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class TaxCalculatorTest {

    private val individual = BigDecimal("0.0300") to BigDecimal("0.1000")

    @Test
    fun `개인 100,000원 - 소득세 3,000 지방세 300 실지급 96,700`() {
        val tax = calc(100_000)

        assertThat(tax.incomeTax).isEqualTo(3_000)
        assertThat(tax.localTax).isEqualTo(300)
        assertThat(tax.netAmount).isEqualTo(96_700)
    }

    @Test
    fun `10원 미만 절사 - 33,333원은 소득세 999 → 990, 지방세 99 → 90`() {
        val tax = calc(33_333)

        assertThat(tax.incomeTax).isEqualTo(990)
        assertThat(tax.localTax).isEqualTo(90)
        assertThat(tax.netAmount).isEqualTo(32_253)
    }

    @Test
    fun `사업자는 원천징수 없음`() {
        val tax = TaxCalculator.calculate(50_000, BigDecimal.ZERO, BigDecimal.ZERO)

        assertThat(tax.incomeTax + tax.localTax).isZero()
        assertThat(tax.netAmount).isEqualTo(50_000)
    }

    @Test
    fun `세전 = 소득세 + 지방세 + 실지급`() {
        listOf(10_000L, 12_345L, 999_999L).forEach { gross ->
            val tax = calc(gross)
            assertThat(tax.incomeTax + tax.localTax + tax.netAmount).isEqualTo(gross)
        }
    }

    private fun calc(gross: Long) = TaxCalculator.calculate(gross, individual.first, individual.second)
}
