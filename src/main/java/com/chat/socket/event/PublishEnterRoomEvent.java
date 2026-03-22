package com.chat.socket.event;

import lombok.Getter;
import org.springframework.web.socket.WebSocketSession;

@Getter
public class PublishEnterRoomEvent {

    private final WebSocketSession session;
    private final Long chatRoomId;

    public PublishEnterRoomEvent(WebSocketSession session, Long chatRoomId) {
        this.session = session;
        this.chatRoomId = chatRoomId;
    }
}
