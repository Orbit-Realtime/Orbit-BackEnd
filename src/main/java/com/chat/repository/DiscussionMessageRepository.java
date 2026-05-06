package com.chat.repository;

import com.chat.entity.DiscussionMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscussionMessageRepository extends JpaRepository<DiscussionMessage, Long> {
}
