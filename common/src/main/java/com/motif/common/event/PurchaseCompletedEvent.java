package com.motif.common.event;

import java.time.LocalDateTime;

// payment → settlement / eventId로 중복 수신 걸러냄
public record PurchaseCompletedEvent(
        String eventId,
        long purchaseId,
        long buyerId,
        long sellerId,
        String targetType,
        long targetId,
        long listPrice,
        LocalDateTime purchasedAt) {

    public static final String TYPE = "PURCHASE_COMPLETED";
}
