package com.motif.content.service;

import com.motif.common.exception.BusinessException;
import com.motif.common.response.SliceResponse;
import com.motif.content.domain.Content;
import com.motif.content.domain.ContentAccess;
import com.motif.content.domain.ContentBody;
import com.motif.content.domain.ContentStatus;
import com.motif.content.dto.ContentDetailResponse;
import com.motif.content.dto.ContentSummaryResponse;
import com.motif.content.exception.ContentErrorCode;
import com.motif.content.repository.ContentAccessRepository;
import com.motif.content.repository.ContentBodyRepository;
import com.motif.content.repository.ContentRepository;
import com.motif.member.api.MemberQueryApi;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 독자용 조회 / 비로그인이면 viewerId null
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentQueryService {

    private final ContentRepository contentRepository;
    private final ContentBodyRepository contentBodyRepository;
    private final ContentAccessRepository contentAccessRepository;
    private final MemberQueryApi memberQueryApi;
    private final Clock clock;

    public SliceResponse<ContentSummaryResponse> getPublished(Long categoryId, Pageable pageable) {
        Slice<Content> slice = categoryId == null
                ? contentRepository.findAllByStatusOrderByPublishedAtDescIdDesc(ContentStatus.PUBLISHED, pageable)
                : contentRepository.findAllByStatusAndCategoryIdOrderByPublishedAtDescIdDesc(
                        ContentStatus.PUBLISHED, categoryId, pageable);
        return toSummaries(slice);
    }

    public SliceResponse<ContentSummaryResponse> getLibrary(Long memberId, Pageable pageable) {
        return toSummaries(contentRepository.findLibrary(memberId, pageable));
    }

    // 본문 열람 시 첫 열람 시각 기록 → 쓰기 트랜잭션
    @Transactional
    public ContentDetailResponse getDetail(Long contentId, Long viewerId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND));
        boolean owner = viewerId != null && content.isOwnedBy(viewerId);
        // 발행 전·판매 중지 글은 작성자만 / 다른 사람에겐 없는 글
        if (!content.isPublished() && !owner) {
            throw new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND);
        }

        boolean accessible = owner || content.isFree() || hasActiveAccess(content, viewerId);
        String body = accessible
                ? contentBodyRepository.findById(contentId).map(ContentBody::getBody).orElse(null)
                : null;
        String nickname = memberQueryApi.getNicknames(List.of(content.getCreatorId())).get(content.getCreatorId());
        return ContentDetailResponse.of(content, nickname, accessible, body);
    }

    private boolean hasActiveAccess(Content content, Long viewerId) {
        if (viewerId == null) {
            return false;
        }
        Optional<ContentAccess> access = contentAccessRepository
                .findByMemberIdAndContentId(viewerId, content.getId())
                .filter(ContentAccess::isActive);
        access.ifPresent(a -> a.markViewed(LocalDateTime.now(clock)));
        return access.isPresent();
    }

    // 닉네임은 member 모듈에서 ID 묶음으로 한 번에 조회 (모듈 간 JOIN 대신)
    private SliceResponse<ContentSummaryResponse> toSummaries(Slice<Content> slice) {
        List<Long> creatorIds = slice.getContent().stream().map(Content::getCreatorId).distinct().toList();
        Map<Long, String> nicknames = memberQueryApi.getNicknames(creatorIds);
        return SliceResponse.of(slice, content -> ContentSummaryResponse.of(content, nicknames.get(content.getCreatorId())));
    }
}
