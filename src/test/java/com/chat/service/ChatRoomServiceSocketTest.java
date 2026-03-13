package com.chat.service;

import com.chat.entity.Chat;
import com.chat.entity.ChatRoom;
import com.chat.entity.Member;
import com.chat.fixture.MemberFixture;
import com.chat.fixture.SocketFixture;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.ChatRepository;
import com.chat.service.dtos.chat.SendChat;
import com.chat.socket.manager.ChatRoomManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.chat.utils.message.MessageType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ChatRoomServiceSocketTest {

    @Autowired
    private ChatRoomService chatRoomService;
    @Autowired
    private ChatService chatService;

    @Autowired
    private TestDataFixture fixture;
    @Autowired
    private MemberFixture memberFixture;
    @Autowired
    private SocketFixture socketFixture;

    @Autowired
    private ChatRoomManager chatRoomManager;
    @Autowired
    private WebsocketSessionManager websocketSessionManager;
    @Autowired
    private ChatRepository chatRepository;

    @LocalServerPort
    private int port;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        fixture.deleteAllData();
        chatRoomManager.clearAll();
        websocketSessionManager.clearAll();
    }

    @Test
    @DisplayName("채팅 데이터가 없는 채팅방 소켓에 연결한다.")
    void connectChatRoomSocketWithoutChatDataTest() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String username = "username";
        Member member = memberFixture.saveEncryptPasswordBy(username);
        Long memberId = member.getId();

        List<Member> participants = new ArrayList<>();
        participants.add(member);
        ChatRoom chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        String JSESSIONID = memberFixture.loginRequestBy(username, port);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> recievedMessgaes = new ArrayList<>();
        socketFixture.connectSocket(JSESSIONID, memberId, port, recievedMessgaes, latch);

        // when
        WebSocketSession serverSession = websocketSessionManager.getSessionBy(memberId).iterator().next();
        chatRoomService.connectChatRoomSocket(serverSession, memberId, chatRoomId);

        // then
        latch.await(3, TimeUnit.SECONDS);
        String payload = recievedMessgaes.get(0);
        JsonNode node = objectMapper.readTree(payload);
        assertThat(node.get("messageType").asText()).isEqualTo("CHAT_ENTER");
        // Phase 3: memberId는 항상 파라미터에서 직접 설정되므로 null이 아니라 실제 memberId 값
        assertThat(node.get("memberId").asLong()).isEqualTo(memberId);
        // 채팅 데이터가 없으므로 lastReadChatId는 null
        assertThat(node.get("lastReadChatId").isNull()).isTrue();
    }

    @Test
    @DisplayName("채팅 데이터가 존재하는 채팅방 소켓에 연결한다.")
    void connectChatRoomSocketWithChatDataTest() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        List<Member> participants = new ArrayList<>();
        participants.add(first);
        participants.add(second);

        ChatRoom chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        Long firstChatId = chatService.saveChat(firstId, chatRoomId, "firstChat");
        chatService.saveChat(secondId, chatRoomId, "secondChat");

        String JSESSIONID = memberFixture.loginRequestBy("first", port);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> recievedMessgaes = new ArrayList<>();
        socketFixture.connectSocket(JSESSIONID, firstId, port, recievedMessgaes, latch);

        // when
        WebSocketSession serverSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        chatRoomService.connectChatRoomSocket(serverSession, firstId, chatRoomId);

        // then
        latch.await(3, TimeUnit.SECONDS);
        String payload = recievedMessgaes.get(0);
        JsonNode node = objectMapper.readTree(payload);
        assertThat(node.get("messageType").asText()).isEqualTo("CHAT_ENTER");
        assertThat(node.get("memberId").asLong()).isEqualTo(firstId);
        assertThat(node.get("lastReadChatId").asLong()).isEqualTo(firstChatId);

        Set<WebSocketSession> webSocketSessions = chatRoomManager.getWebSocketSessionBy(chatRoomId);
        assertThat(webSocketSessions).hasSize(1);
        Collection<WebSocketSession> memberSessions = websocketSessionManager.getSessionBy(firstId);
        assertThat(memberSessions).isNotEmpty();
        assertThat(webSocketSessions.containsAll(memberSessions)).isTrue();
    }

    @Test
    @DisplayName("채팅방에 참여한 회원들에게 메시지를 전송한다.")
    void broadCastMessageTest() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        List<Member> participants = new ArrayList<>();
        participants.add(first);
        participants.add(second);

        ChatRoom chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        CountDownLatch latch = new CountDownLatch(2);

        List<String> firstMessages = new ArrayList<>();
        String firstJSessionId = memberFixture.loginRequestBy("first", port);
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, latch);

        List<String> secondMessages = new ArrayList<>();
        String secondJSessionId = memberFixture.loginRequestBy("second", port);
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);

        WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        chatRoomService.connectChatRoomSocket(firstServerSession, firstId, chatRoomId);
        WebSocketSession secondServerSession = websocketSessionManager.getSessionBy(secondId).iterator().next();
        chatRoomService.connectChatRoomSocket(secondServerSession, secondId, chatRoomId);

        String message = "message";
        SendChat sendChat = SendChat
                .builder()
                .messageType(MessageType.CHAT_MESSAGE)
                .senderId(firstId)
                .senderNickname(first.getNickname())
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
        assertThat(node.get("messageType").asText()).isEqualTo("CHAT_MESSAGE");
        assertThat(node.get("message").asText()).isEqualTo(message);
        assertThat(node.get("senderId").asLong()).isEqualTo(firstId);
        assertThat(node.get("chatId").isNull()).isFalse();
        assertThat(node.get("unReadCount").isNull()).isFalse();
        Long chatId = node.get("chatId").asLong();
        Chat findChat = chatRepository.findById(chatId).get();
        assertThat(findChat.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("채팅방 참여자들에게 마지막 읽음 메시지 정보를 전송한다.")
    void broadcastToChatRoomMembersTest() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        List<Member> participants = new ArrayList<>();
        participants.add(first);
        participants.add(second);

        ChatRoom chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        chatService.saveChat(firstId, chatRoomId, "firstChat");
        chatService.saveChat(secondId, chatRoomId, "secondChat");

        String JSESSIONID = memberFixture.loginRequestBy("first", port);

        CountDownLatch latch = new CountDownLatch(2);
        List<String> recievedMessgaes = new ArrayList<>();
        socketFixture.connectSocket(JSESSIONID, firstId, port, recievedMessgaes, latch);

        // when
        chatRoomService.broadcastToChatRoomMembers(chatRoomId);

        // then: 서버는 UpdateChatRoom DTO를 직렬화해서 전송 (Phase 3)
        boolean messageReceived = latch.await(3, TimeUnit.SECONDS);
        String payload = recievedMessgaes.get(0);
        JsonNode node = objectMapper.readTree(payload);
        assertThat(payload).isNotEmpty();
        assertThat(node.get("messageType").asText()).isEqualTo("UPDATE_CHAT_ROOM");
        assertThat(node.get("chatRoomId").asLong()).isEqualTo(chatRoomId);
        assertThat(node.get("lastMessage").asText()).isEqualTo("secondChat");
        assertThat(node.get("unReadCount").asLong()).isEqualTo(1L);
    }
}
