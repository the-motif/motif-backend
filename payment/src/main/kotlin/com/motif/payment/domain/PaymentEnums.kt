package com.motif.payment.domain

enum class ChargeStatus { PENDING, PAID, FAILED, PARTIAL_CANCELED, CANCELED }

// PAID 유상(현금 충전, 환불 가능) / FREE 무상(이벤트 지급)
enum class PointKind { PAID, FREE }

enum class LotSource { CHARGE, GRANT }

enum class PurchaseTarget { CONTENT, MEMBERSHIP, DONATION }

enum class PurchaseStatus { COMPLETED, REFUNDED }

enum class PointTxType { CHARGE, GRANT, USE, REFUND, CHARGE_CANCEL, EXPIRE }

// point_transaction.ref_type
enum class PointRefType { CHARGE_ORDER, CHARGE_CANCEL, PURCHASE, POINT_GRANT, POINT_LOT }

enum class OutboxStatus { PENDING, PUBLISHED, FAILED }
