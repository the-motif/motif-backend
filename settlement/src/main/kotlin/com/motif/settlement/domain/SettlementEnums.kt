package com.motif.settlement.domain

enum class SalesType { SALE, REFUND }

enum class RevenueType { CONTENT, MEMBERSHIP, DONATION }

enum class DailySettlementStatus { CALCULATED, CONFIRMED }

// AVAILABLE 출금 가능 / HELD 보류 중
enum class LedgerBucket { AVAILABLE, HELD }

enum class LedgerType {
    DAILY_SETTLEMENT,
    HOLD,
    HOLD_RELEASE,
    WITHDRAWAL,
    WITHDRAWAL_REJECT,
    PAYOUT_FAIL,
    ADJUSTMENT,
}
