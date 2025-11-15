package com.chat.socket.handler;

import com.chat.utils.consts.SessionConst;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public class TestWebSocketHandler extends TextWebSocketHandler {

    private final Long memberId;
    private final List<String> receivedMessages;
    private final CountDownLatch latch;

    public TestWebSocketHandler(Long memberId, List<String> receivedMessages, CountDownLatch latch) {
        this.memberId = memberId;
        this.receivedMessages = receivedMessages;
        this.latch = latch;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.getAttributes().put(SessionConst.SESSION_ID, memberId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        receivedMessages.add(message.getPayload());
        latch.countDown();
    }
}
