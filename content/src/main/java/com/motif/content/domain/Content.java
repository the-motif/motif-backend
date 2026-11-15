package com.motif.content.domain;

import com.motif.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 본문은 ContentBody (목록 조회 시 큰 본문 안 읽음)
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // member.id
    @Column(nullable = false, updatable = false)
    private Long creatorId;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String preview;

    @Column(length = 500)
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccessType accessType;

    // 포인트 단위 / FREE면 0
    @Column(nullable = false)
    private long price;

    private Long membershipPlanId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentStatus status;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private int likeCount;

    private LocalDateTime publishedAt;

    public static Content createDraft(Long creatorId, Long categoryId, String title, String preview,
                                      String thumbnailUrl, AccessType accessType, long price) {
        Content content = new Content();
        content.creatorId = creatorId;
        content.status = ContentStatus.DRAFT;
        content.change(categoryId, title, preview, thumbnailUrl, accessType, price);
        return content;
    }

    // 가격 바꿔도 이미 판매된 건은 purchase에 판매 시점 가격이 남음
    public void change(Long categoryId, String title, String preview, String thumbnailUrl,
                       AccessType accessType, long price) {
        this.categoryId = categoryId;
        this.title = title;
        this.preview = preview;
        this.thumbnailUrl = thumbnailUrl;
        this.accessType = accessType;
        this.price = accessType == AccessType.FREE ? 0 : price;
    }

    public void publish(LocalDateTime now) {
        if (status != ContentStatus.DRAFT) {
            throw new IllegalStateException("DRAFT만 발행 가능 id=" + id);
        }
        status = ContentStatus.PUBLISHED;
        publishedAt = now;
    }

    public boolean isOwnedBy(Long memberId) {
        return creatorId.equals(memberId);
    }

    public boolean isPublished() {
        return status == ContentStatus.PUBLISHED;
    }

    public boolean isFree() {
        return accessType == AccessType.FREE;
    }
}
