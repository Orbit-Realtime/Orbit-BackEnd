package com.chat.service;

import com.chat.entity.Chat;
import com.chat.entity.ChatRoom;
import com.chat.entity.Member;
import com.chat.fixture.MemberFixture;
import com.chat.fixture.SocketFixture;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.ChatRepository;
import com.chat.service.dtos.chat.EnterChatRoom;
import com.chat.service.dtos.chat.SendChat;
import com.chat.service.dtos.chat.UpdateChatRoom;
import com.chat.socket.manager.ChatRoomManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.chat.utils.message.MessageType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
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
        String payload = recievedMessgaes.get(0);
        EnterChatRoom enterChatRoom = objectMapper.readValue(payload, EnterChatRoom.class);
        assertThat(enterChatRoom.getMessageType()).isEqualTo(MessageType.CHAT_ENTER);
        assertThat(enterChatRoom.getMemberId()).isNull();
        assertThat(enterChatRoom.getLastReadChatId()).isNull();
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
        String payload = recievedMessgaes.get(0);
        EnterChatRoom enterChatRoom = objectMapper.readValue(payload, EnterChatRoom.class);
        assertThat(enterChatRoom.getMessageType()).isEqualTo(MessageType.CHAT_ENTER);
        assertThat(enterChatRoom.getMemberId()).isEqualTo(firstId);
        assertThat(enterChatRoom.getLastReadChatId()).isEqualTo(firstChatId);

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

        // then
        String payload = secondMessages.get(1);
        objectMapper.addMixIn(SendChat.class, SendChatIgnoreMixIn.class);
        SendChat chatData = objectMapper.readValue(payload, SendChat.class);
        assertThat(chatData.getMessage()).isEqualTo(message);
        assertThat(chatData.getSenderId()).isEqualTo(firstId);
        Chat findChat = chatRepository.findById(chatData.getChatId()).get();
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

        // then
        boolean messageReceived = latch.await(3, TimeUnit.SECONDS);
        String payload = recievedMessgaes.get(0);
        objectMapper.addMixIn(UpdateChatRoom.class, SendChatIgnoreMixIn.class);
        UpdateChatRoom chatData = objectMapper.readValue(payload, UpdateChatRoom.class);
        assertThat(payload).isNotEmpty();
        assertThat(chatData.getChatRoomId()).isEqualTo(chatRoomId);
        assertThat(chatData.getLastMessage()).isEqualTo("secondChat");
        assertThat(chatData.getUnReadCount()).isEqualTo(1);
    }

    public abstract class SendChatIgnoreMixIn {
        @JsonIgnore
        private LocalDateTime createDate;
        @JsonIgnore
        private LocalDateTime createdDate;
    }
}
