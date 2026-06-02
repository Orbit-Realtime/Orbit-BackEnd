package com.chat.socket;

import com.chat.entity.Discussion;
import com.chat.entity.Member;
import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.fixture.MemberFixture;
import com.chat.fixture.SocketFixture;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.DiscussionRepository;
import com.chat.service.DiscussionMessageService;
import com.chat.socket.manager.SpaceManager;
import com.chat.socket.manager.WebsocketSessionManager;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiscussionMessageBroadcastTest {

    // StandardWebSocketClient.execute().get()은 클라이언트 측 연결 완료만 보장한다.
    // 서버의 afterConnectionEstablished()는 별도 Tomcat I/O 스레드에서 실행되므로
    // 클라이언트 Future 완료 시점에 websocketSessionManager 세션 등록이 완료됐다는 보장이 없다.
    // getSessionBy() 호출 전 서버 세션 등록 완료를 기다리기 위해 짧게 대기한다.
    private static final long SERVER_SESSION_REGISTER_WAIT_MS = 300;

    @Autowired private DiscussionMessageService discussionMessageService;
    @Autowired private DiscussionRepository discussionRepository;
    @Autowired private TestDataFixture fixture;
    @Autowired private MemberFixture memberFixture;
    @Autowired private SocketFixture socketFixture;
    @Autowired private SpaceManager spaceManager;
    @Autowired private WebsocketSessionManager websocketSessionManager;

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
    @DisplayName("broadcastDiscussionMessage 호출 시 Space 세션에 DISCUSSION_MESSAGE_EVENT payload가 broadcast된다")
    void broadcastDiscussionMessage_Space_세션에_DISCUSSION_MESSAGE_EVENT_payload가_broadcast된다()
            throws Exception {
        // given
        String senderUsername = "sender";
        String receiverUsername = "receiver";
        Member sender = memberFixture.saveEncryptPasswordBy(senderUsername);
        Member receiver = memberFixture.saveEncryptPasswordBy(receiverUsername);

        Space space = fixture.savedChatRoomBy("space", List.of(sender, receiver));
        Message rootMessage = fixture.savedSimpleChat("원글", sender, space);
        Discussion discussion = discussionRepository.save(Discussion.of(rootMessage));

        // receiver: WS 연결 및 Space 등록
        String receiverJSessionId = memberFixture.loginRequestBy(receiverUsername, port);
        CountDownLatch latch = new CountDownLatch(1);
        List<String> receivedMessages = new ArrayList<>();
        socketFixture.connectSocket(receiverJSessionId, receiver.getId(), port, receivedMessages, latch);
        Thread.sleep(SERVER_SESSION_REGISTER_WAIT_MS);

        WebSocketSession receiverServerSession =
                websocketSessionManager.getSessionBy(receiver.getId()).iterator().next();
        spaceManager.addSessionToSpace(receiverServerSession, space.getId());

        // when: sender의 broadcastDiscussionMessage → AFTER_COMMIT → broadcast
        discussionMessageService.broadcastDiscussionMessage(
                discussion.getId(), sender.getId(), "토론 답글");

        // then: receiver가 DISCUSSION_MESSAGE_EVENT를 수신할 때까지 대기
        boolean received = latch.await(3, TimeUnit.SECONDS);
        assertThat(received).isTrue();
        assertThat(receivedMessages).isNotEmpty();

        JsonNode node = objectMapper.readTree(receivedMessages.get(0));
        assertThat(node.get("messageType").asText()).isEqualTo("DISCUSSION_MESSAGE_EVENT");
        assertThat(node.get("discussionId").asLong()).isEqualTo(discussion.getId());
        assertThat(node.get("senderId").asLong()).isEqualTo(sender.getId());
        assertThat(node.get("senderNickname").asText()).isEqualTo(sender.getNickname());
        assertThat(node.get("content").asText()).isEqualTo("토론 답글");
        assertThat(node.get("discussionMessageId").isNull()).isFalse();
        assertThat(node.get("createdDate").isNull()).isFalse();
        assertThat(node.get("spaceId").asLong()).isEqualTo(space.getId());
    }
}
