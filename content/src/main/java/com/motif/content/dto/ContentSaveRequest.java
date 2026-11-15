package com.motif.content.dto;

import com.motif.content.domain.AccessType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// 생성·수정 공용 / 가격 범위는 서비스에서 accessType과 같이 검사
public record ContentSaveRequest(
        @Schema(example = "1") @NotNull Long categoryId,
        @Schema(example = "제주 한 달 살기 기록") @NotBlank @Size(max = 100) String title,
        @Schema(example = "구매 전 보이는 앞부분") @Size(max = 5000) String preview,
        @Size(max = 500) String thumbnailUrl,
        @Schema(example = "PAID") @NotNull AccessType accessType,
        @Schema(example = "1000") @PositiveOrZero long price,
        @Schema(example = "구매자만 보는 본문") @NotBlank String body) {
}
