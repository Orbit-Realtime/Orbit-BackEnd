package com.chat.service.dtos.chat;

import com.chat.utils.message.MessageType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DiscussionMessageEvent {

    private final MessageType messageType;
    private final Long chatId;
    private final Long discussionMessageId;
    private final Long discussionId;
    private final Long senderId;
    private final String senderNickname;
    private final String content;
    private final LocalDateTime createdDate;
}
