package com.chat.service;

import com.chat.entity.ChatRoom;
import com.chat.entity.Member;
import com.chat.fixture.MemberFixture;
import com.chat.fixture.SocketFixture;
import com.chat.fixture.TestDataFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.chat.service.dtos.chat.SendChat;
import com.chat.socket.manager.ChatRoomManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.chat.utils.consts.SessionConst;
import com.chat.utils.message.MessageType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SimpleChatRoomServiceSocketTest {

    @Autowired
    private TestDataFixture fixture;
    @Autowired
    private MemberFixture memberFixture;
    @Autowired
    private SocketFixture socketFixture;
    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private ChatRoomManager chatRoomManager;
    @Autowired
    private WebsocketSessionManager websocketSessionManager;

    @LocalServerPort
    private int port;
    private WebSocketClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        fixture.deleteAllData();
        chatRoomManager.clearAll();
        websocketSessionManager.clearAll();
    }

    @Test
    @DisplayName("채팅 내역이 없는 채팅방에 연결한다.")
    void connectChatRoomSocketTest() throws Exception {
        // given
        String username = "username";
        Member encryptMember = memberFixture.saveEncryptPasswordBy(username);
        Long encryptMemberId = encryptMember.getId();

        List<Member> participants = new ArrayList<>();
        participants.add(encryptMember);
        ChatRoom chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        String JSessionId = memberFixture.loginRequestBy(username, port);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Cookie", "JSESSIONID=" + JSessionId);

        TextWebSocketHandler handler = new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                session.getAttributes().put(SessionConst.SESSION_ID, encryptMemberId);
            }

            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                receivedMessages.add(message.getPayload());
                latch.countDown();
            }
        };

        client = new StandardWebSocketClient();
        client.execute(handler,
                        headers,
                        URI.create("ws://localhost:" + port + "/ws/chat"))
                .get();

        // when
        WebSocketSession serverSession = websocketSessionManager.getSessionBy(encryptMemberId).iterator().next();
        chatRoomService.connectChatRoomSocket(serverSession, encryptMemberId, chatRoomId);

        // then
        boolean messageReceived = latch.await(3, TimeUnit.SECONDS);
        assertTrue(messageReceived, "EnterChatRoom 메시지를 수신해야 합니다.");
        String payload = receivedMessages.get(0);
        assertTrue(payload.contains("\"messageType\":\"CHAT_ENTER\""));
    }

    @Test
    @DisplayName("채팅방에 메시지를 전송한다.")
    void simpleBroadCastMessageTest() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String first = "first";
        Member firstMember = memberFixture.saveEncryptPasswordBy(first);
        String firstJSessionId = memberFixture.loginRequestBy(first, port);
        Long firstMemberId = firstMember.getId();

        String second = "second";
        Member secondMember = memberFixture.saveEncryptPasswordBy(second);
        String secondJSessionId = memberFixture.loginRequestBy(second, port);
        Long secondMemberId = secondMember.getId();

        CountDownLatch latch = new CountDownLatch(2);
        List<String> firstMessages = new ArrayList<>();
        socketFixture.connectSocket(firstJSessionId, firstMemberId, port, firstMessages, latch);
        List<String> secondMessages = new ArrayList<>();
        socketFixture.connectSocket(secondJSessionId, secondMemberId, port, secondMessages, latch);

        List<Member> participants = new ArrayList<>();
        participants.add(firstMember);
        participants.add(secondMember);
        ChatRoom chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstMemberId).iterator().next();
        chatRoomService.connectChatRoomSocket(firstServerSession, firstMemberId, chatRoomId);
        WebSocketSession secondServerSession = websocketSessionManager.getSessionBy(secondMemberId).iterator().next();
        chatRoomService.connectChatRoomSocket(secondServerSession, secondMemberId, chatRoomId);

        String message = "message";
        fixture.savedSimpleChat(message, firstMember, chatRoom);

        SendChat sendChat = SendChat
                .builder()
                .messageType(MessageType.CHAT_MESSAGE)
                .senderId(firstMemberId)
                .senderNickname(firstMember.getNickname())
                .chatRoomId(chatRoomId)
                .message(message)
                .build();

        // when
        chatRoomService.broadCastMessage(sendChat);

        // then: CHAT_MESSAGE가 second에 도착할 때까지 대기 (latch는 CHAT_ENTER 단계에서 이미 소진됨)
        long deadline = System.currentTimeMillis() + 3000;
        while (secondMessages.size() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(secondMessages).hasSizeGreaterThanOrEqualTo(2);

        // 서버는 BroadcastChat DTO를 직렬화해서 전송 (Phase 3)
        String payload = secondMessages.get(1);
        JsonNode node = objectMapper.readTree(payload);
        assertThat(payload).isNotEmpty();
        assertThat(node.get("chatId").asLong()).isGreaterThan(0);
        assertThat(node.get("message").asText()).isEqualTo(message);
    }

    @Test
    @DisplayName("채팅방 참여자들에게 메시지를 전송한다.")
    void simpleBroadcastToChatRoomMembersTest() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String first = "first";
        Member firstMember = memberFixture.saveEncryptPasswordBy(first);
        String firstJSessionId = memberFixture.loginRequestBy(first, port);
        Long firstMemberId = firstMember.getId();

        String second = "second";
        Member secondMember = memberFixture.saveEncryptPasswordBy(second);
        String secondJSessionId = memberFixture.loginRequestBy(second, port);
        Long secondMemberId = secondMember.getId();

        CountDownLatch latch = new CountDownLatch(2);
        List<String> firstMessages = new ArrayList<>();
        socketFixture.connectSocket(firstJSessionId, firstMemberId, port, firstMessages, latch);
        List<String> secondMessages = new ArrayList<>();
        socketFixture.connectSocket(secondJSessionId, secondMemberId, port, secondMessages, latch);

        List<Member> participants = new ArrayList<>();
        participants.add(firstMember);
        participants.add(secondMember);
        ChatRoom chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        // when
        chatRoomService.broadcastToChatRoomMembers(chatRoomId);

        // then: 서버는 UpdateChatRoom DTO를 직렬화해서 전송 (Phase 3)
        boolean messageReceived = latch.await(3, TimeUnit.SECONDS);
        String payload = secondMessages.get(0);
        JsonNode node = objectMapper.readTree(payload);
        assertThat(payload).isNotEmpty();
        assertThat(node.get("chatRoomId").asLong()).isEqualTo(chatRoomId);
    }
}