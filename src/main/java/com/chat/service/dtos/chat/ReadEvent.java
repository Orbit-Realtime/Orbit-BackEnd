package com.chat.service.dtos.chat;

import com.chat.utils.message.MessageType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ReadEvent {

    private final MessageType messageType = MessageType.READ_EVENT;
    private final Long memberId;
    private final Long chatRoomId;
    private final Long lastReadChatId;
}
