package com.chat.socket.handler;

import com.chat.entity.Space;
import com.chat.entity.Member;
import com.chat.fixture.MemberFixture;
import com.chat.fixture.TestDataFixture;
import com.chat.service.dtos.chat.SendChat;
import com.chat.socket.manager.SpaceManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.chat.utils.message.MessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntegrationTextSocketHandlerTest {

    @Autowired
    private TestDataFixture fixture;
    @Autowired
    private MemberFixture memberFixture;

    @Autowired
    private WebsocketSessionManager websocketSessionManager;
    @Autowired
    private SpaceManager spaceManager;

    @LocalServerPort
    private int port;

    @BeforeEach
    void beforeTearDown() {
        fixture.deleteAllData();
        websocketSessionManager.clearAll();
        spaceManager.clearAll();
    }

    @AfterEach
    void afterTearDown() {
        fixture.deleteAllData();
        websocketSessionManager.clearAll();
        spaceManager.clearAll();
    }

    @Test
    @DisplayName("소켓 연결 시 사용자 세션이 저장된다.")
    void afterConnectionEstablishedTest() throws ExecutionException, InterruptedException {
        // given
        String username = "username";
        Member member = memberFixture.saveEncryptPasswordBy(username);
        Long memberId = member.getId();

        String JSessionId = memberFixture.loginRequestBy(username, port);

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + JSessionId);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        TestWebSocketHandler handler = new TestWebSocketHandler(memberId, receivedMessages, latch);

        // when
        WebSocketClient client = new StandardWebSocketClient();
        client.execute(handler,
                        headers,
                        URI.create("ws://localhost:" + port + "/ws/chat"))
                .get();

        // then
        Collection<WebSocketSession> sessions = websocketSessionManager.getSessionBy(memberId);
        assertThat(sessions).isNotEmpty();
    }

    @Test
    @DisplayName("클라이언트가 소켓을 이용해 메시지를 전송한다.")
    void handleTextMessageTest() throws ExecutionException, InterruptedException, IOException {
        // given
        String username = "username";
        Member member = memberFixture.saveEncryptPasswordBy(username);
        Long memberId = member.getId();

        List<Member> participants = new ArrayList<>();
        participants.add(member);
        Space chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        String JSessionId = memberFixture.loginRequestBy(username, port);

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + JSessionId);

        CountDownLatch latch = new CountDownLatch(2);
        List<String> receivedMessages = new ArrayList<>();
        TestWebSocketHandler handler = new TestWebSocketHandler(memberId, receivedMessages, latch);

        WebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(handler,
                        headers,
                        URI.create("ws://localhost:" + port + "/ws/chat"))
                .get();

        WebSocketSession serverSession = websocketSessionManager.getSessionBy(memberId).iterator().next();
        spaceManager.addSessionToSpace(serverSession, chatRoomId);

        ObjectMapper objectMapper = new ObjectMapper();
        SendChat sendChat = SendChat
                .builder()
                .messageType(MessageType.CHAT_MESSAGE)
                .chatRoomId(chatRoomId)
                .message("message")
                .build();
        String chat = objectMapper.writeValueAsString(sendChat);

        // when
        session.sendMessage(new TextMessage(chat));

        // then: CHAT_ENTER 제거 → CHAT_MESSAGE + UPDATE_CHAT_ROOM = 2개
        latch.await(2, TimeUnit.SECONDS);
        assertThat(receivedMessages).hasSize(2);
    }

    @Test
    @DisplayName("채팅방에 세션이 등록되지 않은 상태에서 CHAT_MESSAGE를 전송하면 DB에 저장되지 않는다.")
    void chatMessageBlockedWhenSessionNotInRoomTest() throws ExecutionException, InterruptedException, IOException {
        // given
        String username = "username";
        Member member = memberFixture.saveEncryptPasswordBy(username);
        Long memberId = member.getId();

        List<Member> participants = new ArrayList<>();
        participants.add(member);
        Space chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        String JSessionId = memberFixture.loginRequestBy(username, port);

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + JSessionId);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        TestWebSocketHandler handler = new TestWebSocketHandler(memberId, receivedMessages, latch);

        WebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(handler,
                        headers,
                        URI.create("ws://localhost:" + port + "/ws/chat"))
                .get();

        // ENTER_ROOM을 보내지 않아 chatRoomManager에 세션이 없는 상태

        ObjectMapper objectMapper = new ObjectMapper();
        SendChat sendChat = SendChat
                .builder()
                .messageType(MessageType.CHAT_MESSAGE)
                .chatRoomId(chatRoomId)
                .message("blocked message")
                .build();

        // when: 세션이 방에 없는 상태에서 CHAT_MESSAGE 전송
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(sendChat)));

        // then: 메시지가 차단되어 아무 응답도 오지 않아야 함
        boolean anyMessageReceived = latch.await(1, TimeUnit.SECONDS);
        assertThat(anyMessageReceived).isFalse();
        assertThat(receivedMessages).isEmpty();
        assertThat(spaceManager.getWebSocketSessionBy(chatRoomId)).isEmpty();
    }

    @Test
    @DisplayName("웹 소켓 연결 종료 시 세션 제거")
    void afterConnectionClosedTest() throws ExecutionException, InterruptedException, IOException {
        // given
        String username = "username";
        Member member = memberFixture.saveEncryptPasswordBy(username);
        Long memberId = member.getId();

        List<Member> participants = new ArrayList<>();
        participants.add(member);
        Space chatRoom = fixture.savedChatRoomBy("title", participants);

        String JSessionId = memberFixture.loginRequestBy(username, port);

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + JSessionId);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        TestWebSocketHandler handler = new TestWebSocketHandler(memberId, receivedMessages, latch);

        WebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(handler,
                        headers,
                        URI.create("ws://localhost:" + port + "/ws/chat"))
                .get();

        // when: 세션 종료
        session.close(CloseStatus.NORMAL);

        // then: afterConnectionClosed 호출을 기다림
        latch.await(2, TimeUnit.SECONDS);

        // WebsocketSessionManager에서 세션이 제거되었는지 확인
        Collection<WebSocketSession> removedSessions = websocketSessionManager.getSessionBy(memberId);
        assertThat(removedSessions).isEmpty();
    }
}