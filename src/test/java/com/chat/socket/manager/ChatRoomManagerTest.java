package com.chat.socket.manager;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.service.dtos.chat.EnterChatRoom;
import com.chat.utils.consts.SessionConst;
import com.chat.utils.message.MessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@SpringBootTest
class ChatRoomManagerTest {

    @Autowired
    private ChatRoomManager chatRoomManager;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void init() {
        chatRoomManager.clearAll();
    }

    @Test
    @DisplayName("채팅방 세션을 추가한다.")
    void addSessionToRoomTest() {
        // given
        Long chatRoomId = 1L;
        Long memberId = 10L;
        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        // when
        chatRoomManager.addSessionToRoom(session, chatRoomId);

        // then
        Set<WebSocketSession> sessionsInRoom = chatRoomManager.getWebSocketSessionBy(chatRoomId);
        assertThat(sessionsInRoom).contains(session);
        Set<Long> roomsOfMember = chatRoomManager.getChatRoomIdsBy(memberId);
        assertThat(roomsOfMember).contains(chatRoomId);
    }

    @Test
    @DisplayName("여러 세션을 동일 채팅방에 추가할 수 있다")
    void addMultipleSessionsToRoomTest() {
        Long chatRoomId = 1L;

        WebSocketSession session1 = mock(WebSocketSession.class);
        given(session1.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 1L));

        WebSocketSession session2 = mock(WebSocketSession.class);
        given(session2.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 2L));

        chatRoomManager.addSessionToRoom(session1, chatRoomId);
        chatRoomManager.addSessionToRoom(session2, chatRoomId);

        Set<WebSocketSession> sessions = chatRoomManager.getWebSocketSessionBy(chatRoomId);
        assertThat(sessions).contains(session1, session2);
    }

    @Test
    @DisplayName("채팅방 ID가 유효하지 않으면 예외 발생")
    void invalidChatRoomIdTest() {
        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 1L));

        assertThatThrownBy(() -> chatRoomManager.addSessionToRoom(session, null))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("채팅방에 세션이 존재하면 해당 세션 세트를 반환한다")
    void getWebSocketSessionByTest() {
        // given
        Long chatRoomId = 1L;
        Long memberId = 100L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        chatRoomManager.addSessionToRoom(session, chatRoomId);

        // when
        Set<WebSocketSession> result = chatRoomManager.getWebSocketSessionBy(chatRoomId);

        // then
        assertThat(result).isNotNull();
        assertThat(result).contains(session);
    }

    @Test
    @DisplayName("채팅방이 존재하지 않으면 빈 세트를 반환한다")
    void nonExistentRoomTest() {
        Long chatRoomId = 999L;

        Set<WebSocketSession> result = chatRoomManager.getWebSocketSessionBy(chatRoomId);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("채팅방 세션이 모두 제거되면 빈 세트를 반환한다")
    void emptySessionSetTest() {
        Long chatRoomId = 999L;
        Long memberId = 42L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        chatRoomManager.addSessionToRoom(session, chatRoomId);
        chatRoomManager.removeChatRoomSession(chatRoomId, session);

        Set<WebSocketSession> result = chatRoomManager.getWebSocketSessionBy(chatRoomId);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("해당 멤버가 참여한 채팅방 ID 반환")
    void getChatRoomIdsByTest() {
        // given
        Long chatRoomId1 = 1L;
        Long chatRoomId2 = 2L;
        Long memberId = 42L;

        WebSocketSession session1 = mock(WebSocketSession.class);
        WebSocketSession session2 = mock(WebSocketSession.class);

        given(session1.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));
        given(session2.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        // when
        chatRoomManager.addSessionToRoom(session1, chatRoomId1);
        chatRoomManager.addSessionToRoom(session2, chatRoomId2);

        // then
        Set<Long> chatRoomIds = chatRoomManager.getChatRoomIdsBy(memberId);
        assertThat(chatRoomIds).isNotNull();
        assertThat(chatRoomIds).contains(chatRoomId1, chatRoomId2);
    }

    @Test
    @DisplayName("해당 멤버가 참여한 채팅방이 없으면 null 반환")
    void noRoomsTest() {
        Long memberId = 99L; // 참여한 방 없음

        Set<Long> chatRoomIds = chatRoomManager.getChatRoomIdsBy(memberId);
        assertThat(chatRoomIds).isNull();
    }

    @Test
    @DisplayName("정상적으로 특정 멤버 세션 제거")
    void removeChatRoomSessionTest() {
        Long chatRoomId = 1L;
        Long memberId = 42L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        chatRoomManager.addSessionToRoom(session, chatRoomId);

        assertThat(chatRoomManager.getWebSocketSessionBy(chatRoomId)).contains(session);
        assertThat(chatRoomManager.getChatRoomIdsBy(memberId)).contains(chatRoomId);

        boolean result = chatRoomManager.removeChatRoomSession(chatRoomId, session);

        assertThat(result).isTrue();
        assertThat(chatRoomManager.getWebSocketSessionBy(chatRoomId)).isEmpty();
        assertThat(chatRoomManager.getChatRoomIdsBy(memberId)).isNull();
    }

    @Test
    @DisplayName("채팅방이 없으면 false 를 반환하고 예외가 발생하지 않는다.")
    void removeChatRoomSession_noRoomTest() {
        Long chatRoomId = 999L;
        Long memberId = 42L;

        WebSocketSession mockSession = mock(WebSocketSession.class);
        given(mockSession.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        boolean result = chatRoomManager.removeChatRoomSession(chatRoomId, mockSession);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("방에 없는 세션을 제거해도 다른 세션은 유지된다.")
    void removeChatRoomSession_noMemberSessionTest() {
        Long chatRoomId = 1L;

        WebSocketSession sessionInRoom = mock(WebSocketSession.class);
        given(sessionInRoom.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 99L));

        WebSocketSession sessionNotInRoom = mock(WebSocketSession.class);
        given(sessionNotInRoom.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 42L));

        chatRoomManager.addSessionToRoom(sessionInRoom, chatRoomId);
        assertThat(chatRoomManager.getWebSocketSessionBy(chatRoomId)).contains(sessionInRoom);

        chatRoomManager.removeChatRoomSession(chatRoomId, sessionNotInRoom);

        assertThat(chatRoomManager.getWebSocketSessionBy(chatRoomId)).contains(sessionInRoom);
    }

    @Test
    @DisplayName("멤버와 채팅방 모두 제거")
    void removeChatRoomSession_cleanUpTest() {
        Long chatRoomId = 1L;
        Long memberId = 42L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        chatRoomManager.addSessionToRoom(session, chatRoomId);

        boolean result = chatRoomManager.removeChatRoomSession(chatRoomId, session);

        assertThat(result).isTrue();
        assertThat(chatRoomManager.getChatRoomIdsBy(memberId)).isNull();
    }

    @Test
    @DisplayName("동일 멤버의 세션이 여러 개일 때 하나 제거해도 멤버는 방에 남아있다.")
    void removeChatRoomSession_multiSessionReturnsFalse() {
        // given
        Long chatRoomId = 1L;
        Long memberId = 42L;

        WebSocketSession sessionA = mock(WebSocketSession.class);
        given(sessionA.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        WebSocketSession sessionB = mock(WebSocketSession.class);
        given(sessionB.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        chatRoomManager.addSessionToRoom(sessionA, chatRoomId);
        chatRoomManager.addSessionToRoom(sessionB, chatRoomId);

        // when: sessionA 만 제거
        boolean memberLeftRoom = chatRoomManager.removeChatRoomSession(chatRoomId, sessionA);

        // then: sessionB 가 남아있으므로 멤버는 방에 남아있음
        assertThat(memberLeftRoom).isFalse();
        assertThat(chatRoomManager.getWebSocketSessionBy(chatRoomId)).contains(sessionB);
        assertThat(chatRoomManager.getChatRoomIdsBy(memberId)).contains(chatRoomId);
    }

    @Test
    @DisplayName("마지막 세션 제거 시 멤버가 방에서 나간다.")
    void removeChatRoomSession_lastSessionReturnsTrue() {
        // given
        Long chatRoomId = 1L;
        Long memberId = 42L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        chatRoomManager.addSessionToRoom(session, chatRoomId);

        // when
        boolean memberLeftRoom = chatRoomManager.removeChatRoomSession(chatRoomId, session);

        // then: 세션이 없으므로 멤버가 방에서 나감
        assertThat(memberLeftRoom).isTrue();
        assertThat(chatRoomManager.getChatRoomIdsBy(memberId)).isNull();
        assertThat(chatRoomManager.getWebSocketSessionBy(chatRoomId)).isEmpty();
    }

    @Test
    @DisplayName("세션이 없는 채팅방에 broadcastEnterChatRoom 호출 시 예외가 발생하지 않는다.")
    void broadcastEnterChatRoom_emptyRoom_noException() {
        Long chatRoomId = 999L;  // 등록되지 않은 방
        EnterChatRoom enterChatRoom = EnterChatRoom.builder()
                .messageType(MessageType.CHAT_ENTER)
                .memberId(1L)
                .build();

        // when & then: 세션이 없는 방에 브로드캐스트해도 예외가 발생하지 않아야 함
        assertThatCode(() -> chatRoomManager.broadcastEnterChatRoom(chatRoomId, enterChatRoom))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("채팅방의 모든 세션에 입장 메시지를 브로드캐스트한다.")
    void broadcastEnterChatRoomTest() throws IOException {
        // given
        Long chatRoomId = 1L;
        WebSocketSession session1 = mock(WebSocketSession.class);
        given(session1.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 1L));
        given(session1.isOpen()).willReturn(true);
        WebSocketSession session2 = mock(WebSocketSession.class);
        given(session2.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 2L));
        given(session2.isOpen()).willReturn(true);

        // 테스트용 채팅방 등록
        chatRoomManager.addSessionToRoom(session1, chatRoomId);
        chatRoomManager.addSessionToRoom(session2, chatRoomId);

        EnterChatRoom enterChatRoom = EnterChatRoom.builder()
                .messageType(MessageType.CHAT_ENTER)
                .memberId(1L)
                .build();

        // when
        chatRoomManager.broadcastEnterChatRoom(chatRoomId, enterChatRoom);

        // then - 열린 세션에 sendMessage()가 한 번씩 호출되어야 함
        verify(session1, times(1)).sendMessage(any(TextMessage.class));
        verify(session2, times(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("개별 세션 메시지 전송 중 IOException 발생 시 예외 없이 warn 로그만 남긴다.")
    void broadcastEnterChatRoom_ioException() throws IOException {
        // given
        Long chatRoomId = 1L;
        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 1L));
        given(session.isOpen()).willReturn(true);
        doThrow(new IOException("send fail")).when(session).sendMessage(any(TextMessage.class));

        chatRoomManager.addSessionToRoom(session, chatRoomId);
        EnterChatRoom enterChatRoom = EnterChatRoom.builder()
                .messageType(MessageType.CHAT_ENTER)
                .memberId(1L)
                .build();

        // when & then: 개별 세션 IOException은 warn 로그로 처리되고 예외가 전파되지 않아야 함
        assertThatCode(() -> chatRoomManager.broadcastEnterChatRoom(chatRoomId, enterChatRoom))
                .doesNotThrowAnyException();
        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("닫힌 세션은 브로드캐스트 대상에서 제외된다.")
    void broadcastEnterChatRoom_skipClosedSession() throws IOException {
        // given
        Long chatRoomId = 1L;

        WebSocketSession openSession = mock(WebSocketSession.class);
        given(openSession.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 1L));
        given(openSession.isOpen()).willReturn(true);

        WebSocketSession closedSession = mock(WebSocketSession.class);
        given(closedSession.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 2L));
        given(closedSession.isOpen()).willReturn(false);

        chatRoomManager.addSessionToRoom(openSession, chatRoomId);
        chatRoomManager.addSessionToRoom(closedSession, chatRoomId);

        EnterChatRoom enterChatRoom = EnterChatRoom.builder()
                .messageType(MessageType.CHAT_ENTER)
                .memberId(1L)
                .build();

        // when
        chatRoomManager.broadcastEnterChatRoom(chatRoomId, enterChatRoom);

        // then: 열린 세션에만 전송, 닫힌 세션은 스킵
        verify(openSession, times(1)).sendMessage(any(TextMessage.class));
        verify(closedSession, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("메시지의 실제 JSON 내용이 올바르게 브로드캐스트된다.")
    void broadcastEnterChatRoom_verifyMessageContent() throws Exception {
        // given
        Long chatRoomId = 1L;
        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, 1L));
        given(session.isOpen()).willReturn(true);
        chatRoomManager.addSessionToRoom(session, chatRoomId);

        EnterChatRoom message = EnterChatRoom.builder()
                .messageType(MessageType.CHAT_ENTER)
                .memberId(1L)
                .build();

        // when
        chatRoomManager.broadcastEnterChatRoom(chatRoomId, message);

        // then - 실제 전송된 메시지 내용 검증
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(1)).sendMessage(captor.capture());

        String sentPayload = captor.getValue().getPayload();
        String expectedJson = objectMapper.writeValueAsString(message);

        assertThat(sentPayload).isEqualTo(expectedJson);
    }
}
