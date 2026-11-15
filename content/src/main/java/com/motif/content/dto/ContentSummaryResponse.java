package com.motif.content.dto;

import com.motif.content.domain.AccessType;
import com.motif.content.domain.Content;
import com.motif.content.domain.ContentStatus;
import java.time.LocalDateTime;

// 목록용 / 본문 제외
public record ContentSummaryResponse(
        Long id,
        Long creatorId,
        String creatorNickname,
        Long categoryId,
        String title,
        String preview,
        String thumbnailUrl,
        AccessType accessType,
        long price,
        ContentStatus status,
        int likeCount,
        LocalDateTime publishedAt) {

    public static ContentSummaryResponse of(Content content, String creatorNickname) {
        return new ContentSummaryResponse(
                content.getId(),
                content.getCreatorId(),
                creatorNickname,
                content.getCategoryId(),
                content.getTitle(),
                content.getPreview(),
                content.getThumbnailUrl(),
                content.getAccessType(),
                content.getPrice(),
                content.getStatus(),
                content.getLikeCount(),
                content.getPublishedAt());
    }
}
