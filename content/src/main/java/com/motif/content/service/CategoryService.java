package com.motif.content.service;

import com.motif.common.exception.BusinessException;
import com.motif.content.domain.Category;
import com.motif.content.dto.CategoryCreateRequest;
import com.motif.content.dto.CategoryResponse;
import com.motif.content.exception.ContentErrorCode;
import com.motif.content.repository.CategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByName(name)) {
            throw new BusinessException(ContentErrorCode.DUPLICATE_CATEGORY);
        }
        return CategoryResponse.from(categoryRepository.save(Category.create(name, request.sortOrder())));
    }

    void validateExists(Long categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new BusinessException(ContentErrorCode.CATEGORY_NOT_FOUND);
        }
    }
}
