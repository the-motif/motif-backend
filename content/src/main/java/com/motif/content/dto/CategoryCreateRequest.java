package com.motif.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryCreateRequest(
        @Schema(example = "에세이") @NotBlank @Size(max = 30) String name,
        @Schema(example = "1") @Min(0) int sortOrder) {
}
