package com.chat.socket.listener;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.service.ChatRoomService;
import com.chat.socket.event.PublishMessageEvent;
import com.chat.socket.manager.ChatRoomManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatBroadcastListener {

    private final ChatRoomManager chatRoomManager;
    private final ChatRoomService chatRoomService;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishMessageToSessions(PublishMessageEvent event) {

        Set<WebSocketSession> sessions = chatRoomManager.getWebSocketSessionBy(event.getChatRoomId());
        if (sessions.isEmpty()) {
            return;
        }

        String sendChatMessage;
        try {
            sendChatMessage = objectMapper.writeValueAsString(event.getBroadcastChat());
        } catch (IOException e) {
            throw new CustomException(ErrorCode.CHAT_ROOM_BROADCAST_IO_EXCEPTION);
        }

        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(new TextMessage(sendChatMessage));
            } catch (IOException e) {
                log.warn("메시지 전송 실패: session={}", session.getId(), e);
            }
        }

        chatRoomService.broadcastToChatRoomMembers(event.getChatRoomId());
    }
}
