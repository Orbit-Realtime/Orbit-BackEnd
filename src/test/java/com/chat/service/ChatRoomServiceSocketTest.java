package com.chat.service;

import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.entity.Member;
import com.chat.fixture.MemberFixture;
import com.chat.fixture.SocketFixture;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.MessageRepository;
import com.chat.service.dtos.chat.SendChat;
import com.chat.socket.manager.SpaceManager;
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
    private SpaceService spaceService;
    @Autowired
    private MessageService messageService;

    @Autowired
    private TestDataFixture fixture;
    @Autowired
    private MemberFixture memberFixture;
    @Autowired
    private SocketFixture socketFixture;

    @Autowired
    private SpaceManager spaceManager;
    @Autowired
    private WebsocketSessionManager websocketSessionManager;
    @Autowired
    private MessageRepository messageRepository;

    @LocalServerPort
    private int port;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        fixture.deleteAllData();
        spaceManager.clearAll();
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
        Space chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        String JSESSIONID = memberFixture.loginRequestBy(username, port);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        socketFixture.connectSocket(JSESSIONID, memberId, port, receivedMessages, latch);
        Thread.sleep(300);

        // when
        WebSocketSession serverSession = websocketSessionManager.getSessionBy(memberId).iterator().next();
        spaceManager.addSessionToSpace(serverSession, chatRoomId);

        // then: 세션만 등록됨, 클라이언트로 전송되는 메시지 없음
        Thread.sleep(500);
        assertThat(receivedMessages).isEmpty();
        assertThat(spaceManager.getWebSocketSessionBy(chatRoomId)).contains(serverSession);
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

        Space chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        messageService.saveChat(firstId, chatRoomId, "firstChat");
        messageService.saveChat(secondId, chatRoomId, "secondChat");

        String JSESSIONID = memberFixture.loginRequestBy("first", port);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        socketFixture.connectSocket(JSESSIONID, firstId, port, receivedMessages, latch);
        Thread.sleep(300);

        // when
        WebSocketSession serverSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        spaceManager.addSessionToSpace(serverSession, chatRoomId);

        // then: CHAT_ENTER 미전송, 세션 등록 여부만 확인
        Thread.sleep(500);
        assertThat(receivedMessages).isEmpty();
        Set<WebSocketSession> webSocketSessions = spaceManager.getWebSocketSessionBy(chatRoomId);
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

        Space chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        CountDownLatch latch = new CountDownLatch(2);

        List<String> firstMessages = new ArrayList<>();
        String firstJSessionId = memberFixture.loginRequestBy("first", port);
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, latch);

        List<String> secondMessages = new ArrayList<>();
        String secondJSessionId = memberFixture.loginRequestBy("second", port);
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
        Thread.sleep(300);

        WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        spaceManager.addSessionToSpace(firstServerSession, chatRoomId);
        WebSocketSession secondServerSession = websocketSessionManager.getSessionBy(secondId).iterator().next();
        spaceManager.addSessionToSpace(secondServerSession, chatRoomId);

        String message = "message";
        SendChat sendChat = SendChat
                .builder()
                .messageType(MessageType.CHAT_MESSAGE)
                .chatRoomId(chatRoomId)
                .message(message)
                .build();

        // when
        spaceService.broadCastMessage(firstId, sendChat);

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
        Message findChat = messageRepository.findById(chatId).get();
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
        Space chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        CountDownLatch latch = new CountDownLatch(2);
        List<String> firstMessages = new ArrayList<>();
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, latch);

        List<String> secondMessages = new ArrayList<>();
        String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
        Thread.sleep(300);

        WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        spaceManager.addSessionToSpace(firstServerSession, chatRoomId);
        WebSocketSession secondServerSession = websocketSessionManager.getSessionBy(secondId).iterator().next();
        spaceManager.addSessionToSpace(secondServerSession, chatRoomId);

        // SendChat에 chatRoomId, message만 포함 — senderId, senderNickname 없음
        SendChat sendChat = SendChat
                .builder()
                .messageType(MessageType.CHAT_MESSAGE)
                .chatRoomId(chatRoomId)
                .message("hello")
                .build();

        // when: loginMemberId = firstId (세션 기준값)
        spaceService.broadCastMessage(firstId, sendChat);

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
        Message savedChat = messageRepository.findById(savedChatId).orElseThrow();
        assertThat(savedChat.getMember().getId()).isEqualTo(firstId);
    }

    @Test
    @DisplayName("findChatHistory는 방에 접속 중인 세션에 UPDATE_CHAT_ROOM과 READ_EVENT를 전송한다.")
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

        Space chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        // second가 아직 방에 없는 상태에서 first가 메시지 전송 → second.isRead=false
        messageService.saveChat(firstId, chatRoomId, "hello");

        // second가 WS 연결 및 방 입장
        String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
        CountDownLatch latch = new CountDownLatch(2); // UPDATE_CHAT_ROOM + READ_EVENT
        List<String> secondMessages = new ArrayList<>();
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
        Thread.sleep(300);

        WebSocketSession secondServerSession = websocketSessionManager.getSessionBy(secondId).iterator().next();
        spaceManager.addSessionToSpace(secondServerSession, chatRoomId);
        Thread.sleep(500);

        // when: second가 채팅 내역 조회 → updatedCount > 0 이면 READ_EVENT + UPDATE_CHAT_ROOM 발행
        messageService.findChatHistory(chatRoomId, secondId, null);

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

        // READ_EVENT의 memberId, chatRoomId, previousLastReadChatId, currentLastReadChatId 검증
        String readEventPayload = secondMessages.stream()
                .filter(msg -> msg.contains("READ_EVENT"))
                .findFirst()
                .orElseThrow();
        JsonNode readEventNode = objectMapper.readTree(readEventPayload);
        assertThat(readEventNode.get("memberId").asLong()).isEqualTo(secondId);
        assertThat(readEventNode.get("chatRoomId").asLong()).isEqualTo(chatRoomId);
        assertThat(readEventNode.get("previousLastReadChatId").isNull()).isTrue();
        assertThat(readEventNode.get("currentLastReadChatId").isNull()).isFalse();
    }

    @Test
    @DisplayName("findChatHistory는 READ_EVENT에 이전 방문 시 마지막으로 읽은 chatId를 포함한다.")
    void findChatHistory_READ_EVENT에_lastReadChatId를_포함한다() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        Space chatRoom = fixture.savedChatRoomBy("title", List.of(first, second));
        Long chatRoomId = chatRoom.getId();

        // first가 첫 번째 메시지 전송 → second.isRead=false
        Long firstChatId = messageService.saveChat(firstId, chatRoomId, "first message");

        // second 첫 번째 입장: firstChat 읽음 처리 (lastReadChatId=null, updatedCount=1)
        messageService.findChatHistory(chatRoomId, secondId, null);

        // first가 두 번째 메시지 전송 → second.isRead=false
        messageService.saveChat(firstId, chatRoomId, "second message");

        // second WS 연결 및 방 입장
        String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
        CountDownLatch latch = new CountDownLatch(2);
        List<String> secondMessages = new ArrayList<>();
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
        Thread.sleep(300);

        WebSocketSession secondServerSession = websocketSessionManager.getSessionBy(secondId).iterator().next();
        spaceManager.addSessionToSpace(secondServerSession, chatRoomId);
        Thread.sleep(500);

        // when: second 두 번째 채팅 내역 조회 → lastReadChatId = firstChatId (이전 방문 시 firstChat까지 읽었음)
        messageService.findChatHistory(chatRoomId, secondId, null);

        // then: READ_EVENT에 lastReadChatId 포함 검증
        boolean received = latch.await(3, TimeUnit.SECONDS);
        assertThat(received).isTrue();

        String readEventPayload = secondMessages.stream()
                .filter(msg -> msg.contains("READ_EVENT"))
                .findFirst()
                .orElseThrow();
        JsonNode readEventNode = objectMapper.readTree(readEventPayload);
        assertThat(readEventNode.get("memberId").asLong()).isEqualTo(secondId);
        assertThat(readEventNode.get("previousLastReadChatId").asLong()).isEqualTo(firstChatId);
    }

    @Test
    @DisplayName("leaveChatRoom은 남은 참여자에게 UPDATE_CHAT_ROOM을 전송한다.")
    void leaveChatRoom_UPDATE_CHAT_ROOM_전송() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        Space chatRoom = fixture.savedChatRoomBy("title", List.of(first, second));
        Long chatRoomId = chatRoom.getId();

        // first가 WS 연결 및 방 입장
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        CountDownLatch latch = new CountDownLatch(1);
        List<String> firstMessages = new ArrayList<>();
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, latch);
        Thread.sleep(300);

        WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        spaceManager.addSessionToSpace(firstServerSession, chatRoomId);

        // when: second가 채팅방 퇴장
        spaceService.leaveSpace(secondId, chatRoomId);

        // then: 남은 first에게 UPDATE_CHAT_ROOM 전송
        boolean received = latch.await(3, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(firstMessages).isNotEmpty();

        JsonNode node = objectMapper.readTree(firstMessages.get(0));
        assertThat(node.get("messageType").asText()).isEqualTo("UPDATE_CHAT_ROOM");
        assertThat(node.get("chatRoomId").asLong()).isEqualTo(chatRoomId);
    }

    @Test
    @DisplayName("renameChatRoom은 참여자에게 변경된 제목의 UPDATE_CHAT_ROOM을 전송한다.")
    void renameChatRoom_UPDATE_CHAT_ROOM_전송() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        Space chatRoom = fixture.savedChatRoomBy("oldTitle", List.of(first));
        Long chatRoomId = chatRoom.getId();

        // first가 WS 연결 및 방 입장
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        CountDownLatch latch = new CountDownLatch(1);
        List<String> firstMessages = new ArrayList<>();
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, latch);
        Thread.sleep(300);

        WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        spaceManager.addSessionToSpace(firstServerSession, chatRoomId);

        // when: 채팅방 이름 변경
        spaceService.renameSpace(firstId, chatRoomId, "newTitle");

        // then: UPDATE_CHAT_ROOM에 변경된 title 포함
        boolean received = latch.await(3, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(firstMessages).isNotEmpty();

        JsonNode node = objectMapper.readTree(firstMessages.get(0));
        assertThat(node.get("messageType").asText()).isEqualTo("UPDATE_CHAT_ROOM");
        assertThat(node.get("chatRoomId").asLong()).isEqualTo(chatRoomId);
        assertThat(node.get("title").asText()).isEqualTo("newTitle");
    }

    @Test
    @DisplayName("inviteMembers는 기존 참여자에게 UPDATE_CHAT_ROOM을 전송한다.")
    void inviteMembers_UPDATE_CHAT_ROOM_전송() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        // 초기 방: first만 참여
        Space chatRoom = fixture.savedChatRoomBy("title", List.of(first));
        Long chatRoomId = chatRoom.getId();

        // first가 WS 연결 및 방 입장
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        CountDownLatch latch = new CountDownLatch(1);
        List<String> firstMessages = new ArrayList<>();
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, latch);
        Thread.sleep(300);

        WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        spaceManager.addSessionToSpace(firstServerSession, chatRoomId);

        // when: second를 초대
        spaceService.inviteMembers(firstId, chatRoomId, Set.of(secondId));

        // then: 기존 참여자 first에게 UPDATE_CHAT_ROOM 전송
        boolean received = latch.await(3, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(firstMessages).isNotEmpty();

        JsonNode node = objectMapper.readTree(firstMessages.get(0));
        assertThat(node.get("messageType").asText()).isEqualTo("UPDATE_CHAT_ROOM");
        assertThat(node.get("chatRoomId").asLong()).isEqualTo(chatRoomId);
    }
}
