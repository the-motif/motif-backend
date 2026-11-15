package com.motif.content.api;

import com.motif.content.domain.AccessType;
import org.jspecify.annotations.Nullable;

// 구매 시점 스냅샷용 판매 정보
public record ContentSaleInfo(
        long contentId,
        long creatorId,
        String title,
        AccessType accessType,
        long price,
        @Nullable Long membershipPlanId) {
}
