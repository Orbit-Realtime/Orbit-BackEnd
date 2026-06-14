package com.chat.service.dtos.chat;

import com.chat.utils.message.MessageType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BroadcastChat {
    private final MessageType messageType;
    private final Long senderId;
    private final String senderNickname;
    private final Long chatRoomId;
    private final String message;
    private final Long chatId;
    private final Long unreadMemberCount;
    private final LocalDateTime createdDate;
    private final String clientMessageId;
}
