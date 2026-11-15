package com.motif.common.event;

import java.time.LocalDateTime;

// payment → settlement / 환불은 정산에서 음수 행으로 반영
public record PurchaseRefundedEvent(
        String eventId,
        long purchaseId,
        long buyerId,
        long sellerId,
        String targetType,
        long targetId,
        long listPrice,
        LocalDateTime refundedAt) {

    public static final String TYPE = "PURCHASE_REFUNDED";
}
