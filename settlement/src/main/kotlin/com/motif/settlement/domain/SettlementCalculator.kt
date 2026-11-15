package com.motif.settlement.domain

import java.math.BigDecimal
import java.math.RoundingMode

// 정산 계산 / DB 없이 숫자만 → 단위 테스트 대상
object SettlementCalculator {

    // 수수료 = 정가 × 요율, 원 미만 내림
    fun fee(listPrice: Long, feeRate: BigDecimal): Long {
        require(listPrice >= 0) { "정가는 0 이상" }
        return BigDecimal.valueOf(listPrice).multiply(feeRate).setScale(0, RoundingMode.DOWN).longValueExact()
    }

    fun calculate(input: SettlementInput): SettlementResult {
        val net = input.grossSales - input.refundAmount - input.feeAmount
        // 순매출이 양수일 때만 보류 / 원 미만 내림
        val hold = if (net > 0) {
            BigDecimal.valueOf(net).multiply(input.holdRate).setScale(0, RoundingMode.DOWN).longValueExact()
        } else {
            0L
        }
        val payable = net - hold + input.holdReleaseAmount + input.carryOverAmount
        return SettlementResult(net, hold, payable)
    }
}

data class SettlementInput(
    val grossSales: Long,
    val refundAmount: Long,
    val feeAmount: Long,
    val holdRate: BigDecimal,
    val holdReleaseAmount: Long,
    // 전날 마이너스 이월 (0 이하)
    val carryOverAmount: Long,
) {
    init {
        require(grossSales >= 0 && refundAmount >= 0 && holdReleaseAmount >= 0) { "합계는 0 이상" }
        require(carryOverAmount <= 0) { "이월은 0 이하" }
    }
}

data class SettlementResult(
    val netAmount: Long,
    val holdAmount: Long,
    // 양수면 출금 가능 금액에 더함 / 음수면 다음 날 이월
    val payableAmount: Long,
) {
    val availableIncrease: Long get() = maxOf(payableAmount, 0)
}
