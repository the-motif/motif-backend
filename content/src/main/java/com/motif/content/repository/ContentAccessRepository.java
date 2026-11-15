package com.motif.content.repository;

import com.motif.content.domain.ContentAccess;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentAccessRepository extends JpaRepository<ContentAccess, Long> {

    Optional<ContentAccess> findByMemberIdAndContentId(Long memberId, Long contentId);
}
