package com.chat.repository;

import com.chat.entity.Discussion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DiscussionRepository extends JpaRepository<Discussion, Long> {
    Optional<Discussion> findByRootMessageId(Long rootMessageId);
    boolean existsByRootMessageId(Long rootMessageId);

    @Query("SELECT d" +
            " FROM Discussion d" +
            " JOIN FETCH d.rootMessage m" +
            " JOIN FETCH m.space" +
            " WHERE d.id = :id")
    Optional<Discussion> findByIdFetchRootMessageAndSpace(@Param("id") Long id);
}
