package com.motif.content.service;

import com.motif.common.exception.BusinessException;
import com.motif.common.response.SliceResponse;
import com.motif.content.domain.AccessType;
import com.motif.content.domain.Content;
import com.motif.content.domain.ContentBody;
import com.motif.content.domain.ContentStatus;
import com.motif.content.dto.ContentDetailResponse;
import com.motif.content.dto.ContentSaveRequest;
import com.motif.content.dto.ContentSummaryResponse;
import com.motif.content.exception.ContentErrorCode;
import com.motif.content.repository.ContentBodyRepository;
import com.motif.content.repository.ContentRepository;
import com.motif.member.api.MemberQueryApi;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 창작자 본인 콘텐츠 작성·수정·발행
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorContentService {

    static final long MIN_PRICE = 100;
    static final long MAX_PRICE = 1_000_000;

    private final ContentRepository contentRepository;
    private final ContentBodyRepository contentBodyRepository;
    private final CategoryService categoryService;
    private final MemberQueryApi memberQueryApi;
    private final Clock clock;

    @Transactional
    public ContentDetailResponse create(Long creatorId, ContentSaveRequest request) {
        validate(request);
        Content content = contentRepository.save(Content.createDraft(
                creatorId, request.categoryId(), request.title(), request.preview(),
                request.thumbnailUrl(), request.accessType(), request.price()));
        contentBodyRepository.save(ContentBody.of(content.getId(), request.body()));
        return toDetail(content, request.body());
    }

    @Transactional
    public ContentDetailResponse update(Long creatorId, Long contentId, ContentSaveRequest request) {
        validate(request);
        Content content = findOwned(creatorId, contentId);
        content.change(request.categoryId(), request.title(), request.preview(),
                request.thumbnailUrl(), request.accessType(), request.price());
        ContentBody body = contentBodyRepository.findById(contentId)
                .orElseGet(() -> contentBodyRepository.save(ContentBody.of(contentId, request.body())));
        body.change(request.body());
        return toDetail(content, request.body());
    }

    @Transactional
    public ContentDetailResponse publish(Long creatorId, Long contentId) {
        Content content = findOwned(creatorId, contentId);
        if (content.getStatus() != ContentStatus.DRAFT) {
            throw new BusinessException(ContentErrorCode.ALREADY_PUBLISHED);
        }
        content.publish(LocalDateTime.now(clock));
        String body = contentBodyRepository.findById(contentId).map(ContentBody::getBody).orElse(null);
        return toDetail(content, body);
    }

    public SliceResponse<ContentSummaryResponse> getMyContents(Long creatorId, Pageable pageable) {
        String nickname = memberQueryApi.getNicknames(List.of(creatorId)).get(creatorId);
        return SliceResponse.of(contentRepository.findAllByCreatorIdOrderByIdDesc(creatorId, pageable),
                content -> ContentSummaryResponse.of(content, nickname));
    }

    private void validate(ContentSaveRequest request) {
        categoryService.validateExists(request.categoryId());
        if (request.accessType() == AccessType.MEMBERSHIP) {
            throw new BusinessException(ContentErrorCode.MEMBERSHIP_NOT_SUPPORTED);
        }
        if (request.accessType() == AccessType.PAID
                && (request.price() < MIN_PRICE || request.price() > MAX_PRICE)) {
            throw new BusinessException(ContentErrorCode.INVALID_PRICE);
        }
    }

    private Content findOwned(Long creatorId, Long contentId) {
        Content content = contentRepository.findById(contentId)
                .orElseThrow(() -> new BusinessException(ContentErrorCode.CONTENT_NOT_FOUND));
        if (!content.isOwnedBy(creatorId)) {
            throw new BusinessException(ContentErrorCode.NOT_OWNER);
        }
        return content;
    }

    private ContentDetailResponse toDetail(Content content, String body) {
        String nickname = memberQueryApi.getNicknames(List.of(content.getCreatorId())).get(content.getCreatorId());
        return ContentDetailResponse.of(content, nickname, true, body);
    }
}
