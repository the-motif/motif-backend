package com.motif.content.controller;

import com.motif.common.response.SliceResponse;
import com.motif.content.dto.ContentDetailResponse;
import com.motif.content.dto.ContentSummaryResponse;
import com.motif.content.service.ContentQueryService;
import com.motif.member.auth.CurrentMemberId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "콘텐츠")
@RestController
@RequiredArgsConstructor
public class ContentController {

    private final ContentQueryService contentQueryService;

    @Operation(summary = "발행된 콘텐츠 목록", description = "최신 발행순 / 로그인 불필요")
    @GetMapping("/api/contents")
    public SliceResponse<ContentSummaryResponse> contents(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return contentQueryService.getPublished(categoryId, PageRequest.of(page, size));
    }

    @Operation(summary = "콘텐츠 상세", description = "무료·구매·작성자면 본문 포함, 아니면 body null")
    @GetMapping("/api/contents/{id}")
    public ContentDetailResponse detail(@PathVariable Long id, @CurrentMemberId(required = false) Long viewerId) {
        return contentQueryService.getDetail(id, viewerId);
    }

    @Operation(summary = "내 서재", description = "열람 권한 있는 콘텐츠 / 최근 구매순")
    @GetMapping("/api/library")
    public SliceResponse<ContentSummaryResponse> library(
            @CurrentMemberId Long memberId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return contentQueryService.getLibrary(memberId, PageRequest.of(page, size));
    }
}
