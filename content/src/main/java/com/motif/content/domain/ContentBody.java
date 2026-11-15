package com.motif.content.domain;

import com.motif.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// content와 1:1 / PK = content.id
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentBody extends BaseTimeEntity {

    @Id
    private Long contentId;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String body;

    public static ContentBody of(Long contentId, String body) {
        ContentBody contentBody = new ContentBody();
        contentBody.contentId = contentId;
        contentBody.body = body;
        return contentBody;
    }

    public void change(String body) {
        this.body = body;
    }
}
