package com.chat.socket;

import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.entity.Member;
import com.chat.fixture.MemberFixture;
import com.chat.fixture.SocketFixture;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.MessageRepository;
import com.chat.service.MessageService;
import com.chat.service.SpaceService;
import com.chat.service.dtos.chat.RoomActiveRequest;
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
import org.springframework.web.socket.TextMessage;
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
public class SpaceServiceSocketTest {

    private static final long CONNECT_SETTLE_MS = 300;
    private static final long ROOM_JOIN_SETTLE_MS = 500;
    private static final long BROADCAST_TIMEOUT_SECONDS = 3;

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
    @DisplayName("메시지가 없는 Space 소켓에 연결하면 세션이 방에 등록된다.")
    void 메시지가_없는_Space_소켓에_연결하면_세션이_방에_등록된다() throws ExecutionException, InterruptedException {
        // given
        String username = "username";
        Member member = memberFixture.saveEncryptPasswordBy(username);
        Long memberId = member.getId();

        List<Member> participants = new ArrayList<>();
        participants.add(member);
        Space space = fixture.savedChatRoomBy("title", participants);
        Long spaceId = space.getId();

        String JSESSIONID = memberFixture.loginRequestBy(username, port);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        socketFixture.connectSocket(JSESSIONID, memberId, port, receivedMessages, latch);
        Thread.sleep(CONNECT_SETTLE_MS);

        // when
        WebSocketSession serverSession = websocketSessionManager.getSessionBy(memberId).iterator().next();
        spaceManager.addSessionToSpace(serverSession, spaceId);

        // then: 세션만 등록됨, 클라이언트로 전송되는 메시지 없음
        Thread.sleep(ROOM_JOIN_SETTLE_MS);
        assertThat(receivedMessages).isEmpty();
        assertThat(spaceManager.getWebSocketSessionBy(spaceId)).contains(serverSession);
    }

    @Test
    @DisplayName("메시지가 있는 Space 소켓에 연결하면 세션이 방에 등록된다.")
    void 메시지가_있는_Space_소켓에_연결하면_세션이_방에_등록된다() throws ExecutionException, InterruptedException {
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

        Space space = fixture.savedChatRoomBy("title", participants);
        Long spaceId = space.getId();

        messageService.saveMessage(firstId, spaceId, "firstChat");
        messageService.saveMessage(secondId, spaceId, "secondChat");

        String JSESSIONID = memberFixture.loginRequestBy("first", port);

        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        socketFixture.connectSocket(JSESSIONID, firstId, port, receivedMessages, latch);
        Thread.sleep(CONNECT_SETTLE_MS);

        // when
        WebSocketSession serverSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        spaceManager.addSessionToSpace(serverSession, spaceId);

        // then: CHAT_ENTER 미전송, 세션 등록 여부만 확인
        Thread.sleep(ROOM_JOIN_SETTLE_MS);
        assertThat(receivedMessages).isEmpty();
        Set<WebSocketSession> webSocketSessions = spaceManager.getWebSocketSessionBy(spaceId);
        assertThat(webSocketSessions).hasSize(1);
        Collection<WebSocketSession> memberSessions = websocketSessionManager.getSessionBy(firstId);
        assertThat(memberSessions).isNotEmpty();
        assertThat(webSocketSessions.containsAll(memberSessions)).isTrue();
    }

    @Test
    @DisplayName("Space에 연결된 모든 참여자에게 메시지가 전송된다.")
    void Space에_연결된_모든_참여자에게_메시지가_전송된다() throws ExecutionException, InterruptedException, JsonProcessingException {
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

        Space space = fixture.savedChatRoomBy("title", participants);
        Long spaceId = space.getId();

        List<String> firstMessages = new ArrayList<>();
        String firstJSessionId = memberFixture.loginRequestBy("first", port);
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, new CountDownLatch(1));

        CountDownLatch latch = new CountDownLatch(1);
        List<String> secondMessages = new ArrayList<>();
        String secondJSessionId = memberFixture.loginRequestBy("second", port);
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
        Thread.sleep(CONNECT_SETTLE_MS);

        WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        spaceManager.addSessionToSpace(firstServerSession, spaceId);
        WebSocketSession secondServerSession = websocketSessionManager.getSessionBy(secondId).iterator().next();
        spaceManager.addSessionToSpace(secondServerSession, spaceId);

        String message = "message";
        SendChat sendChat = SendChat
                .builder()
                .messageType(MessageType.CHAT_MESSAGE)
                .chatRoomId(spaceId)
                .message(message)
                .build();

        // when
        spaceService.broadCastMessage(firstId, sendChat);

        // then: CHAT_MESSAGE가 second에 도착할 때까지 대기
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(secondMessages).isNotEmpty();

        String payload = secondMessages.get(0);
        JsonNode node = objectMapper.readTree(payload);
        assertThat(node.get("messageType").asText()).isEqualTo("CHAT_MESSAGE");
        assertThat(node.get("message").asText()).isEqualTo(message);
        assertThat(node.get("senderId").asLong()).isEqualTo(firstId);
        assertThat(node.get("senderNickname").asText()).isEqualTo(first.getNickname());
        assertThat(node.get("chatId").isNull()).isFalse();
        assertThat(node.has("unreadMemberCount")).isTrue();
        Long messageId = node.get("chatId").asLong();
        Message foundMessage = messageRepository.findById(messageId).get();
        assertThat(foundMessage.getContent()).isEqualTo(message);
    }

    @Test
    @DisplayName("메시지 전송 시 senderId와 senderNickname은 세션과 DB 기준으로 설정된다.")
    void 메시지_전송_시_senderId와_senderNickname은_세션과_DB_기준으로_설정된다() throws ExecutionException, InterruptedException, JsonProcessingException {
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
        Space space = fixture.savedChatRoomBy("title", participants);
        Long spaceId = space.getId();

        List<String> firstMessages = new ArrayList<>();
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, new CountDownLatch(1));

        CountDownLatch latch = new CountDownLatch(1);
        List<String> secondMessages = new ArrayList<>();
        String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
        Thread.sleep(CONNECT_SETTLE_MS);

        WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        spaceManager.addSessionToSpace(firstServerSession, spaceId);
        WebSocketSession secondServerSession = websocketSessionManager.getSessionBy(secondId).iterator().next();
        spaceManager.addSessionToSpace(secondServerSession, spaceId);

        // SendChat에 chatRoomId, message만 포함 — senderId, senderNickname 없음
        SendChat sendChat = SendChat
                .builder()
                .messageType(MessageType.CHAT_MESSAGE)
                .chatRoomId(spaceId)
                .message("hello")
                .build();

        // when: loginMemberId = firstId (세션 기준값)
        spaceService.broadCastMessage(firstId, sendChat);

        // then
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(secondMessages).isNotEmpty();

        JsonNode node = objectMapper.readTree(secondMessages.get(0));

        // senderId는 파라미터로 전달된 firstId (세션 값)
        assertThat(node.get("senderId").asLong()).isEqualTo(firstId);
        // senderNickname은 DB에서 조회된 값
        assertThat(node.get("senderNickname").asText()).isEqualTo(first.getNickname());
        // Chat이 DB에 firstId 기준으로 저장되었는지 확인
        Long savedMessageId = node.get("chatId").asLong();
        Message savedMessage = messageRepository.findById(savedMessageId).orElseThrow();
        assertThat(savedMessage.getMember().getId()).isEqualTo(firstId);
    }

    @Test
    @DisplayName("채팅 내역 조회 시 방에 접속 중인 세션에 UPDATE_CHAT_ROOM과 READ_EVENT가 전송된다.")
    void 채팅_내역_조회_시_방에_접속_중인_세션에_UPDATE_CHAT_ROOM과_READ_EVENT가_전송된다() throws ExecutionException, InterruptedException, JsonProcessingException {
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

        Space space = fixture.savedChatRoomBy("title", participants);
        Long spaceId = space.getId();

        // second가 아직 방에 없는 상태에서 first가 메시지 전송 → second.isRead=false
        messageService.saveMessage(firstId, spaceId, "hello");

        // second가 WS 연결 및 방 입장
        String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
        CountDownLatch latch = new CountDownLatch(2); // UPDATE_CHAT_ROOM + READ_EVENT
        List<String> secondMessages = new ArrayList<>();
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
        Thread.sleep(CONNECT_SETTLE_MS);

        WebSocketSession secondServerSession = websocketSessionManager.getSessionBy(secondId).iterator().next();
        spaceManager.addSessionToSpace(secondServerSession, spaceId);
        Thread.sleep(ROOM_JOIN_SETTLE_MS);

        // when: second가 채팅 내역 조회 → updatedCount > 0 이면 READ_EVENT + UPDATE_CHAT_ROOM 발행
        messageService.findMessageHistory(spaceId, secondId, null);

        // then: UPDATE_CHAT_ROOM + READ_EVENT 수신 대기
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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
        assertThat(readEventNode.get("chatRoomId").asLong()).isEqualTo(spaceId);
        assertThat(readEventNode.get("previousLastReadChatId").isNull()).isTrue();
        assertThat(readEventNode.get("currentLastReadChatId").isNull()).isFalse();
    }

    @Test
    @DisplayName("채팅 내역 조회 시 READ_EVENT에 이전 방문의 lastReadMessageId가 포함된다.")
    void 채팅_내역_조회_시_READ_EVENT에_이전_방문의_lastReadMessageId가_포함된다() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        Space space = fixture.savedChatRoomBy("title", List.of(first, second));
        Long spaceId = space.getId();

        // first가 첫 번째 메시지 전송 → second.isRead=false
        Long firstMessageId = messageService.saveMessage(firstId, spaceId, "first message");

        // second 첫 번째 입장: firstChat 읽음 처리 (lastReadChatId=null, updatedCount=1)
        messageService.findMessageHistory(spaceId, secondId, null);

        // first가 두 번째 메시지 전송 → second.isRead=false
        messageService.saveMessage(firstId, spaceId, "second message");

        // second WS 연결 및 방 입장
        String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
        CountDownLatch latch = new CountDownLatch(2);
        List<String> secondMessages = new ArrayList<>();
        socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
        Thread.sleep(CONNECT_SETTLE_MS);

        WebSocketSession secondServerSession = websocketSessionManager.getSessionBy(secondId).iterator().next();
        spaceManager.addSessionToSpace(secondServerSession, spaceId);
        Thread.sleep(ROOM_JOIN_SETTLE_MS);

        // when: second 두 번째 채팅 내역 조회 → lastReadChatId = firstMessageId (이전 방문 시 firstChat까지 읽었음)
        messageService.findMessageHistory(spaceId, secondId, null);

        // then: READ_EVENT에 lastReadChatId 포함 검증
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();

        String readEventPayload = secondMessages.stream()
                .filter(msg -> msg.contains("READ_EVENT"))
                .findFirst()
                .orElseThrow();
        JsonNode readEventNode = objectMapper.readTree(readEventPayload);
        assertThat(readEventNode.get("memberId").asLong()).isEqualTo(secondId);
        assertThat(readEventNode.get("previousLastReadChatId").asLong()).isEqualTo(firstMessageId);
    }

    @Test
    @DisplayName("Space 나가기 시 남은 참여자에게 UPDATE_CHAT_ROOM이 전송된다.")
    void Space_나가기_시_남은_참여자에게_UPDATE_CHAT_ROOM이_전송된다() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        Space space = fixture.savedChatRoomBy("title", List.of(first, second));
        Long spaceId = space.getId();

        // first가 WS 연결 및 방 입장
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        CountDownLatch latch = new CountDownLatch(1);
        List<String> firstMessages = new ArrayList<>();
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, latch);
        Thread.sleep(CONNECT_SETTLE_MS);

        WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        spaceManager.addSessionToSpace(firstServerSession, spaceId);

        // when: second가 채팅방 퇴장
        spaceService.leaveSpace(secondId, spaceId);

        // then: 남은 first에게 UPDATE_CHAT_ROOM 전송
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(firstMessages).isNotEmpty();

        JsonNode node = objectMapper.readTree(firstMessages.get(0));
        assertThat(node.get("messageType").asText()).isEqualTo("UPDATE_CHAT_ROOM");
        assertThat(node.get("chatRoomId").asLong()).isEqualTo(spaceId);
    }

    @Test
    @DisplayName("Space 이름 변경 시 참여자에게 변경된 제목의 UPDATE_CHAT_ROOM이 전송된다.")
    void Space_이름_변경_시_참여자에게_변경된_제목의_UPDATE_CHAT_ROOM이_전송된다() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        Space space = fixture.savedChatRoomBy("oldTitle", List.of(first));
        Long spaceId = space.getId();

        // first가 WS 연결 및 방 입장
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        CountDownLatch latch = new CountDownLatch(1);
        List<String> firstMessages = new ArrayList<>();
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, latch);
        Thread.sleep(CONNECT_SETTLE_MS);

        WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        spaceManager.addSessionToSpace(firstServerSession, spaceId);

        // when: 채팅방 이름 변경
        spaceService.renameSpace(firstId, spaceId, "newTitle");

        // then: UPDATE_CHAT_ROOM에 변경된 title 포함
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(firstMessages).isNotEmpty();

        JsonNode node = objectMapper.readTree(firstMessages.get(0));
        assertThat(node.get("messageType").asText()).isEqualTo("UPDATE_CHAT_ROOM");
        assertThat(node.get("chatRoomId").asLong()).isEqualTo(spaceId);
        assertThat(node.get("title").asText()).isEqualTo("newTitle");
    }

    @Test
    @DisplayName("멤버 초대 시 기존 참여자에게 UPDATE_CHAT_ROOM이 전송된다.")
    void 멤버_초대_시_기존_참여자에게_UPDATE_CHAT_ROOM이_전송된다() throws ExecutionException, InterruptedException, JsonProcessingException {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        // 초기 방: first만 참여
        Space space = fixture.savedChatRoomBy("title", List.of(first));
        Long spaceId = space.getId();

        // first가 WS 연결 및 방 입장
        String firstJSessionId = memberFixture.loginRequestBy(firstUsername, port);
        CountDownLatch latch = new CountDownLatch(1);
        List<String> firstMessages = new ArrayList<>();
        socketFixture.connectSocket(firstJSessionId, firstId, port, firstMessages, latch);
        Thread.sleep(CONNECT_SETTLE_MS);

        WebSocketSession firstServerSession = websocketSessionManager.getSessionBy(firstId).iterator().next();
        spaceManager.addSessionToSpace(firstServerSession, spaceId);

        // when: second를 초대
        spaceService.inviteMembers(firstId, spaceId, Set.of(secondId));

        // then: 기존 참여자 first에게 UPDATE_CHAT_ROOM 전송
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(firstMessages).isNotEmpty();

        JsonNode node = objectMapper.readTree(firstMessages.get(0));
        assertThat(node.get("messageType").asText()).isEqualTo("UPDATE_CHAT_ROOM");
        assertThat(node.get("chatRoomId").asLong()).isEqualTo(spaceId);
    }

    @Test
    @DisplayName("ROOM_ACTIVE 전송 시 unread가 있으면 READ_EVENT와 UPDATE_CHAT_ROOM이 소켓으로 전달된다.")
    void ROOM_ACTIVE_전송_시_unread가_있으면_READ_EVENT와_UPDATE_CHAT_ROOM이_소켓으로_전달된다()
            throws Exception {
        // given
        String firstUsername = "first";
        Member first = memberFixture.saveEncryptPasswordBy(firstUsername);
        Long firstId = first.getId();

        String secondUsername = "second";
        Member second = memberFixture.saveEncryptPasswordBy(secondUsername);
        Long secondId = second.getId();

        Space space = fixture.savedChatRoomBy("title", List.of(first, second));
        Long spaceId = space.getId();

        // second 연결 전 first가 메시지 전송 → second cursor=null (unread=1)
        messageService.saveMessage(firstId, spaceId, "hello");

        // second WS 연결 (latch=2: READ_EVENT + UPDATE_CHAT_ROOM 각 1건)
        String secondJSessionId = memberFixture.loginRequestBy(secondUsername, port);
        CountDownLatch latch = new CountDownLatch(2);
        List<String> secondMessages = new ArrayList<>();
        WebSocketSession secondClientSession =
                socketFixture.connectSocket(secondJSessionId, secondId, port, secondMessages, latch);
        Thread.sleep(CONNECT_SETTLE_MS);

        // 기존 패턴과 동일: addSessionToSpace 직접 호출로 Space 세션 등록
        WebSocketSession secondServerSession =
                websocketSessionManager.getSessionBy(secondId).iterator().next();
        spaceManager.addSessionToSpace(secondServerSession, spaceId);
        Thread.sleep(ROOM_JOIN_SETTLE_MS);

        // when: second 클라이언트가 ROOM_ACTIVE WS 메시지 전송
        RoomActiveRequest roomActive = RoomActiveRequest.builder()
                .messageType(MessageType.ROOM_ACTIVE)
                .chatRoomId(spaceId)
                .build();
        secondClientSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(roomActive)));

        // then: 3초 안에 READ_EVENT + UPDATE_CHAT_ROOM 수신
        boolean received = latch.await(BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(received).isTrue();

        List<String> messageTypes = secondMessages.stream()
                .map(msg -> {
                    try {
                        return objectMapper.readTree(msg).get("messageType").asText();
                    } catch (Exception e) {
                        return "";
                    }
                })
                .collect(Collectors.toList());
        assertThat(messageTypes).containsExactlyInAnyOrder("READ_EVENT", "UPDATE_CHAT_ROOM");
    }
}
