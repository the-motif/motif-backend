package com.motif.content.dto;

import com.motif.content.domain.AccessType;
import com.motif.content.domain.Content;
import com.motif.content.domain.ContentStatus;
import java.time.LocalDateTime;

// accessible이 false면 body는 null
public record ContentDetailResponse(
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
        LocalDateTime publishedAt,
        boolean accessible,
        String body) {

    public static ContentDetailResponse of(Content content, String creatorNickname, boolean accessible, String body) {
        return new ContentDetailResponse(
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
                content.getPublishedAt(),
                accessible,
                accessible ? body : null);
    }
}
