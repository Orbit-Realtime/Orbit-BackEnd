package com.chat.repository;

import com.chat.entity.DiscussionMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DiscussionMessageRepository extends JpaRepository<DiscussionMessage, Long> {

    @Query("SELECT dm" +
            " FROM DiscussionMessage dm" +
            " JOIN FETCH dm.member" +
            " WHERE dm.discussion.id = :discussionId" +
            " ORDER BY dm.id ASC")
    List<DiscussionMessage> findByDiscussionIdFetchMember(@Param("discussionId") Long discussionId);
}
