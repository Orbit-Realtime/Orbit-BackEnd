package com.chat.socket.event;

import com.chat.service.dtos.chat.SendChat;
import lombok.Getter;

@Getter
public class PublishMessageEvent {

    private SendChat sendChat;
    private Long chatRoomId;

    public PublishMessageEvent(SendChat sendChat, Long chatRoomId) {
        this.sendChat = sendChat;
        this.chatRoomId = chatRoomId;
    }
}
