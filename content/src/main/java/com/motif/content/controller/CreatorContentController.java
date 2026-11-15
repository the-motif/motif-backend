package com.motif.content.controller;

import com.motif.common.response.SliceResponse;
import com.motif.content.dto.ContentDetailResponse;
import com.motif.content.dto.ContentSaveRequest;
import com.motif.content.dto.ContentSummaryResponse;
import com.motif.content.service.CreatorContentService;
import com.motif.member.auth.CurrentMemberId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "창작자: 콘텐츠 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/creator/contents")
@PreAuthorize("hasRole('CREATOR')")
public class CreatorContentController {

    private final CreatorContentService creatorContentService;

    @Operation(summary = "작성 (임시저장)", description = "DRAFT로 생성 / PAID는 100~1,000,000 포인트")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContentDetailResponse create(@CurrentMemberId Long creatorId, @Valid @RequestBody ContentSaveRequest request) {
        return creatorContentService.create(creatorId, request);
    }

    @Operation(summary = "수정", description = "발행 후에도 가능 / 이미 팔린 건은 판매 시점 가격 유지")
    @PutMapping("/{id}")
    public ContentDetailResponse update(@CurrentMemberId Long creatorId, @PathVariable Long id,
                                        @Valid @RequestBody ContentSaveRequest request) {
        return creatorContentService.update(creatorId, id, request);
    }

    @Operation(summary = "발행")
    @PostMapping("/{id}/publish")
    public ContentDetailResponse publish(@CurrentMemberId Long creatorId, @PathVariable Long id) {
        return creatorContentService.publish(creatorId, id);
    }

    @Operation(summary = "내 콘텐츠 목록", description = "모든 상태 / 최신 작성순")
    @GetMapping
    public SliceResponse<ContentSummaryResponse> myContents(
            @CurrentMemberId Long creatorId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return creatorContentService.getMyContents(creatorId, PageRequest.of(page, size));
    }
}
