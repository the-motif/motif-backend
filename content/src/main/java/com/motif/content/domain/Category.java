package com.motif.content.domain;

import com.motif.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String name;

    // 작은 값이 먼저
    @Column(nullable = false)
    private int sortOrder;

    public static Category create(String name, int sortOrder) {
        Category category = new Category();
        category.name = name;
        category.sortOrder = sortOrder;
        return category;
    }
}
