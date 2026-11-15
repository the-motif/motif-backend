package com.motif.content.api;

import com.motif.common.exception.BusinessException;
import com.motif.content.domain.AccessSource;
import com.motif.content.domain.AccessType;
import com.motif.content.domain.Content;
import com.motif.content.domain.ContentAccess;
import com.motif.content.exception.ContentErrorCode;
import com.motif.content.repository.ContentAccessRepository;
import com.motif.content.repository.ContentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// payment가 구매·환불 때 호출 / 호출한 쪽 트랜잭션에 함께 묶임
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentPurchaseApi {

    private final ContentRepository contentRepository;
    private final ContentAccessRepository contentAccessRepository;
    private final Clock clock;

    // 발행된 유료 콘텐츠만 판매 가능
    public ContentSaleInfo getSaleInfo(long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND));
        if (!content.isPublished() || content.getAccessType() != AccessType.PAID) {
            throw new BusinessException(ContentErrorCode.NOT_FOR_SALE);
        }
        return new ContentSaleInfo(content.getId(), content.getCreatorId(), content.getTitle(),
                content.getAccessType(), content.getPrice(), content.getMembershipPlanId());
    }

    public boolean hasAccess(long memberId, long contentId) {
        return contentAccessRepository.findByMemberIdAndContentId(memberId, contentId)
                .map(ContentAccess::isActive)
                .orElse(false);
    }

    // 첫 구매는 새 행, 환불 후 재구매는 같은 행 갱신
    @Transactional
    public void grantAccess(long memberId, long contentId, long purchaseId, AccessSource source) {
        contentAccessRepository.findByMemberIdAndContentId(memberId, contentId)
                .ifPresentOrElse(
                        access -> {
                            if (access.isActive()) {
                                throw new BusinessException(ContentErrorCode.ALREADY_OWNED);
                            }
                            access.regrant(source, purchaseId);
                        },
                        () -> contentAccessRepository.save(
                                ContentAccess.grant(memberId, contentId, source, purchaseId)));
    }

    // 환불 가능 여부 판단용 / 본문을 한 번이라도 열었으면 true
    public boolean isViewed(long memberId, long contentId) {
        return contentAccessRepository.findByMemberIdAndContentId(memberId, contentId)
                .map(ContentAccess::isViewed)
                .orElse(false);
    }

    @Transactional
    public void revokeAccess(long memberId, long contentId) {
        ContentAccess access = contentAccessRepository.findByMemberIdAndContentId(memberId, contentId)
                .filter(ContentAccess::isActive)
                .orElseThrow(() -> new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND));
        access.revoke(LocalDateTime.now(clock));
    }
}
