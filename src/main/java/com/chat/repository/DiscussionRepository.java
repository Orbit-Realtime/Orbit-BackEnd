package com.chat.repository;

import com.chat.entity.Discussion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DiscussionRepository extends JpaRepository<Discussion, Long> {
    Optional<Discussion> findByRootMessageId(Long rootMessageId);
    boolean existsByRootMessageId(Long rootMessageId);
}
