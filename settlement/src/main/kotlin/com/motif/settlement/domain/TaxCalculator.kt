package com.motif.settlement.domain

import java.math.BigDecimal
import java.math.RoundingMode

// 원천징수 계산 / 소득세 = 지급액 × 세율, 지방소득세 = 소득세 × 비율, 각각 10원 미만 절사
object TaxCalculator {

    private val TEN = BigDecimal.TEN

    fun calculate(grossAmount: Long, incomeTaxRate: BigDecimal, localTaxRate: BigDecimal): TaxResult {
        require(grossAmount > 0) { "지급액은 양수" }
        val incomeTax = truncateToTen(BigDecimal.valueOf(grossAmount).multiply(incomeTaxRate))
        val localTax = truncateToTen(BigDecimal.valueOf(incomeTax).multiply(localTaxRate))
        return TaxResult(grossAmount, incomeTax, localTax, grossAmount - incomeTax - localTax)
    }

    private fun truncateToTen(value: BigDecimal): Long =
        value.divide(TEN, 0, RoundingMode.DOWN).multiply(TEN).longValueExact()
}

data class TaxResult(
    val grossAmount: Long,
    val incomeTax: Long,
    val localTax: Long,
    val netAmount: Long,
)
