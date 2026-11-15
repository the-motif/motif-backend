package com.motif.content.repository;

import com.motif.content.domain.Content;
import com.motif.content.domain.ContentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentRepository extends JpaRepository<Content, Long> {

    Slice<Content> findAllByStatusOrderByPublishedAtDescIdDesc(ContentStatus status, Pageable pageable);

    Slice<Content> findAllByStatusAndCategoryIdOrderByPublishedAtDescIdDesc(
            ContentStatus status, Long categoryId, Pageable pageable);

    Slice<Content> findAllByCreatorIdOrderByIdDesc(Long creatorId, Pageable pageable);

    // 내 서재 / 같은 모듈 테이블 JOIN
    @Query("""
            select c from Content c, ContentAccess a
            where a.contentId = c.id and a.memberId = :memberId and a.revokedAt is null
            order by a.updatedAt desc, a.id desc
            """)
    Slice<Content> findLibrary(@Param("memberId") Long memberId, Pageable pageable);
}
