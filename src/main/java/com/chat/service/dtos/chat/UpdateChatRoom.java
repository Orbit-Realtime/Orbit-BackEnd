package com.chat.service.dtos.chat;

import com.chat.utils.message.MessageType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UpdateChatRoom {

    private final MessageType messageType;
    private final Long chatRoomId;
    private final String title;
    private final String lastMessage;
    private final Long unreadMessageCount;
    private final LocalDateTime createdDate;
}
