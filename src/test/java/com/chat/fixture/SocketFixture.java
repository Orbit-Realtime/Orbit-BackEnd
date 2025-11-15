package com.chat.fixture;

import com.chat.socket.handler.TestWebSocketHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

@Component
public class SocketFixture {

    public WebSocketSession connectSocket(String JSessionId, Long memberId, int port, List<String> receivedMessages, CountDownLatch latch) throws ExecutionException, InterruptedException {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + JSessionId);

        TestWebSocketHandler handler = new TestWebSocketHandler(memberId, receivedMessages, latch);

        WebSocketClient client = new StandardWebSocketClient();
        return client.execute(handler,
                        headers,
                        URI.create("ws://localhost:" + port + "/ws/chat"))
                .get();
    }
}
