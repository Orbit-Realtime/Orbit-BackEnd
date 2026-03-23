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
import java.util.stream.Collectors;

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
    @DisplayName("채팅 데이터가 없는 채팅방 소켓에 연결하면 세션이 방에 등록된다.")
    void connectChatRoomSocketWithoutChatDataTest() throws ExecutionException, InterruptedException {
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
        List<String> receivedMessages = new ArrayList<>();
        socketFixture.connectSocket(JSESSIONID, memberId, port, receivedMessages, latch);

        // when
        WebSocketSession serverSession = websocketSessionManager.getSessionBy(memberId).iterator().next();
        chatRoomService.connectChatRoomSocket(serverSession, memberId, chatRoomId);

        // then: CHAT_ENTER를 전송하지 않으므로 메시지는 없고 세션만 등록됨
        Thread.sleep(500);
        assertThat(receivedMessages).isEmpty();
        assertThat(chatRoomManager.getWebSocketSessionBy(chatRoomId)).contains(serverSession);
    }

    @Test
    @DisplayName("채팅 데이터가 존재하는 채팅방 소켓에 연결하면 세션이 방에 등록된다.")
    void connectChatRoomSocketWithChatDataTest() throws ExecutionException, InterruptedException {
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

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        socketFixture.connectSocket(JSESSIONID, firstId, port, receivedMessages, latch);

        // when
        WebSocketSession serverSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        chatRoomService.connectChatRoomSocket(serverSession, firstId, chatRoomId);

        // then: CHAT_ENTER 미전송, 세션 등록 여부만 확인
        Thread.sleep(500);
        assertThat(receivedMessages).isEmpty();
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
                .chatRoomId(chatRoomId)
                .message(message)
                .build();

        // when
        chatRoomService.broadCastMessage(firstId, sendChat);

        // then: CHAT_MESSAGE가 second에 도착할 때까지 대기
        long deadline = System.currentTimeMillis() + 3000;
        while (secondMessages.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(secondMessages).isNotEmpty();

        String payload = secondMessages.get(0);
        JsonNode node = objectMapper.readTree(payload);
        assertThat(node.get("messageType").asText()).isEqualTo("CHAT_MESSAGE");
        assertThat(node.get("message").asText()).isEqualTo(message);
        assertThat(node.get("senderId").asLong()).isEqualTo(firstId);
        assertThat(node.get("senderNickname").asText()).isEqualTo(first.getNickname());
        assertThat(node.get("chatId").isNull()).isFalse();
        assertThat(node.has("unreadMemberCount")).isTrue();
        Long chatId = node.get("chatId").asLong();
        Chat findChat = chatRepository.findById(chatId).get();
        assertThat(findChat.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("broadCastMessage는 senderId와 senderNickname을 서버 세션과 DB 기준으로 설정한다.")
    void broadCastMessage_senderIsFromSessionAndDB() throws ExecutionException, InterruptedException, JsonProcessingException {
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
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, latch);

        List<String> secondMessages = new ArrayList<>();
        String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);

        WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        chatRoomService.connectChatRoomSocket(firstServerSession, firstId, chatRoomId);
        WebSocketSession secondServerSession = websocketSessionManager.getSessionBy(secondId).iterator().next();
        chatRoomService.connectChatRoomSocket(secondServerSession, secondId, chatRoomId);

        // SendChat에 chatRoomId, message만 포함 — senderId, senderNickname 없음
        SendChat sendChat = SendChat
                .builder()
                .messageType(MessageType.CHAT_MESSAGE)
                .chatRoomId(chatRoomId)
                .message("hello")
                .build();

        // when: loginMemberId = firstId (세션 기준값)
        chatRoomService.broadCastMessage(firstId, sendChat);

        // then
        long deadline = System.currentTimeMillis() + 3000;
        while (secondMessages.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        JsonNode node = objectMapper.readTree(secondMessages.get(0));

        // senderId는 파라미터로 전달된 firstId (세션 값)
        assertThat(node.get("senderId").asLong()).isEqualTo(firstId);
        // senderNickname은 DB에서 조회된 값
        assertThat(node.get("senderNickname").asText()).isEqualTo(first.getNickname());
        // Chat이 DB에 firstId 기준으로 저장되었는지 확인
        Long savedChatId = node.get("chatId").asLong();
        Chat savedChat = chatRepository.findById(savedChatId).orElseThrow();
        assertThat(savedChat.getMember().getId()).isEqualTo(firstId);
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
        assertThat(node.get("unreadMessageCount").asLong()).isEqualTo(1L);
    }

    @Test
    @DisplayName("broadcastAfterRead는 방에 접속 중인 세션에 UPDATE_CHAT_ROOM과 READ_EVENT를 전송한다.")
    void broadcastAfterReadTest() throws ExecutionException, InterruptedException, JsonProcessingException {
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

        // second가 아직 방에 없는 상태에서 first가 메시지 전송 → second.isRead=false
        chatService.saveChat(firstId, chatRoomId, "hello");

        // second가 WS 연결 및 방 입장
        String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
        CountDownLatch latch = new CountDownLatch(2); // UPDATE_CHAT_ROOM + READ_EVENT
        List<String> secondMessages = new ArrayList<>();
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);

        WebSocketSession secondServerSession = websocketSessionManager.getSessionBy(secondId).iterator().next();
        chatRoomService.connectChatRoomSocket(secondServerSession, secondId, chatRoomId);
        Thread.sleep(500); // AFTER_COMMIT 대기

        // when: getChatHistory 호출 후 broadcastAfterRead 호출하는 상황 시뮬레이션
        chatRoomService.broadcastAfterRead(secondId, chatRoomId);

        // then: UPDATE_CHAT_ROOM + READ_EVENT 수신 대기
        boolean received = latch.await(3, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(secondMessages).hasSize(2);

        List<String> messageTypes = secondMessages.stream()
                .map(msg -> {
                    try {
                        return objectMapper.readTree(msg).get("messageType").asText();
                    } catch (Exception e) {
                        return "";
                    }
                })
                .collect(Collectors.toList());
        assertThat(messageTypes).containsExactlyInAnyOrder("UPDATE_CHAT_ROOM", "READ_EVENT");

        // READ_EVENT의 memberId, chatRoomId 검증
        String readEventPayload = secondMessages.stream()
                .filter(msg -> msg.contains("READ_EVENT"))
                .findFirst()
                .orElseThrow();
        JsonNode readEventNode = objectMapper.readTree(readEventPayload);
        assertThat(readEventNode.get("memberId").asLong()).isEqualTo(secondId);
        assertThat(readEventNode.get("chatRoomId").asLong()).isEqualTo(chatRoomId);
    }
}
