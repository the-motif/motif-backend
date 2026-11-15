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

// 회원·콘텐츠당 1행 / 재구매 시 같은 행 갱신
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentAccess extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long memberId;

    @Column(nullable = false, updatable = false)
    private Long contentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccessSource source;

    // 가장 최근 purchase.id
    @Column(nullable = false)
    private Long purchaseId;

    // NULL이면 열람 전 → 환불 가능
    private LocalDateTime firstViewedAt;

    private LocalDateTime revokedAt;

    public static ContentAccess grant(Long memberId, Long contentId, AccessSource source, Long purchaseId) {
        ContentAccess access = new ContentAccess();
        access.memberId = memberId;
        access.contentId = contentId;
        access.source = source;
        access.purchaseId = purchaseId;
        return access;
    }

    // 환불 후 재구매
    public void regrant(AccessSource source, Long purchaseId) {
        if (isActive()) {
            throw new IllegalStateException("이미 열람 권한 있음 id=" + id);
        }
        this.source = source;
        this.purchaseId = purchaseId;
        this.firstViewedAt = null;
        this.revokedAt = null;
    }

    public void revoke(LocalDateTime now) {
        if (!isActive()) {
            throw new IllegalStateException("이미 회수된 권한 id=" + id);
        }
        this.revokedAt = now;
    }

    // 처음 본문 열 때만 기록
    public void markViewed(LocalDateTime now) {
        if (firstViewedAt == null) {
            firstViewedAt = now;
        }
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public boolean isViewed() {
        return firstViewedAt != null;
    }
}
