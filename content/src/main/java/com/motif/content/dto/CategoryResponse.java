package com.motif.content.dto;

import com.motif.content.domain.Category;

public record CategoryResponse(Long id, String name, int sortOrder) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSortOrder());
    }
}
