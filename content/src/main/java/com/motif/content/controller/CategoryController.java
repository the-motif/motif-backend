package com.motif.content.controller;

import com.motif.content.dto.CategoryCreateRequest;
import com.motif.content.dto.CategoryResponse;
import com.motif.content.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "카테고리")
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "카테고리 목록", description = "로그인 불필요")
    @GetMapping("/api/categories")
    public List<CategoryResponse> categories() {
        return categoryService.getCategories();
    }

    @Operation(summary = "관리자: 카테고리 추가")
    @PostMapping("/api/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse create(@Valid @RequestBody CategoryCreateRequest request) {
        return categoryService.create(request);
    }
}
