package com.chat.socket.manager;

import com.chat.exception.CustomException;
import com.chat.utils.consts.SessionConst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@SpringBootTest
class ChatRoomManagerTest {

    @Autowired
    private ChatRoomManager chatRoomManager;

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
    @DisplayName("채팅방에 접속 중인 멤버는 isInRoom이 true를 반환한다.")
    void isInRoom_returnsTrueWhenMemberInRoom() {
        // given
        Long chatRoomId = 1L;
        Long memberId = 10L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));
        chatRoomManager.addSessionToRoom(session, chatRoomId);

        // when & then
        assertThat(chatRoomManager.isInRoom(chatRoomId, memberId)).isTrue();
    }

    @Test
    @DisplayName("채팅방에 접속 중이지 않은 멤버는 isInRoom이 false를 반환한다.")
    void isInRoom_returnsFalseWhenMemberNotInRoom() {
        // given
        Long chatRoomId = 1L;
        Long memberInRoom = 10L;
        Long memberNotInRoom = 99L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberInRoom));
        chatRoomManager.addSessionToRoom(session, chatRoomId);

        // when & then
        assertThat(chatRoomManager.isInRoom(chatRoomId, memberNotInRoom)).isFalse();
    }

    @Test
    @DisplayName("채팅방 자체가 없으면 isInRoom이 false를 반환한다.")
    void isInRoom_returnsFalseForNonExistentRoom() {
        // given
        Long chatRoomId = 999L;
        Long memberId = 10L;

        // when & then
        assertThat(chatRoomManager.isInRoom(chatRoomId, memberId)).isFalse();
    }

    @Test
    @DisplayName("동일 멤버의 세션이 여러 개여도 isInRoom이 true를 반환한다.")
    void isInRoom_returnsTrueForMultipleSessionsSameMember() {
        // given
        Long chatRoomId = 1L;
        Long memberId = 10L;

        WebSocketSession sessionA = mock(WebSocketSession.class);
        given(sessionA.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));
        WebSocketSession sessionB = mock(WebSocketSession.class);
        given(sessionB.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        chatRoomManager.addSessionToRoom(sessionA, chatRoomId);
        chatRoomManager.addSessionToRoom(sessionB, chatRoomId);

        // when & then
        assertThat(chatRoomManager.isInRoom(chatRoomId, memberId)).isTrue();
    }

    @Test
    @DisplayName("마지막 세션 제거 후 isInRoom이 false를 반환한다.")
    void isInRoom_returnsFalseAfterSessionRemoved() {
        // given
        Long chatRoomId = 1L;
        Long memberId = 10L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));
        chatRoomManager.addSessionToRoom(session, chatRoomId);
        chatRoomManager.removeChatRoomSession(chatRoomId, session);

        // when & then
        assertThat(chatRoomManager.isInRoom(chatRoomId, memberId)).isFalse();
    }

    @Test
    @DisplayName("ENTER_ROOM 하면 isRoomActive가 true를 반환한다.")
    void isRoomActive_returnsTrueWhenActivated() {
        Long chatRoomId = 1L;
        Long memberId = 10L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getId()).willReturn("session-1");
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        chatRoomManager.registerSession(session);
        chatRoomManager.addSessionToRoom(session, chatRoomId);

        assertThat(chatRoomManager.isRoomActive(memberId, chatRoomId)).isTrue();
    }

    @Test
    @DisplayName("ENTER_ROOM 후 ROOM_INACTIVE 하면 isRoomActive가 false를 반환한다.")
    void isRoomActive_returnsFalseAfterEnterAndInactivate() {
        Long chatRoomId = 1L;
        Long memberId = 10L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getId()).willReturn("session-1");
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        chatRoomManager.registerSession(session);
        chatRoomManager.addSessionToRoom(session, chatRoomId);    // auto-activate 됨
        chatRoomManager.deactivateRoom("session-1", chatRoomId);  // deactivate

        assertThat(chatRoomManager.isRoomActive(memberId, chatRoomId)).isFalse();
    }

    @Test
    @DisplayName("ROOM_INACTIVE 후 ROOM_ACTIVE 하면 isRoomActive가 다시 true를 반환한다.")
    void isRoomActive_returnsTrueAfterReactivate() {
        Long chatRoomId = 1L;
        Long memberId = 10L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getId()).willReturn("session-1");
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        chatRoomManager.registerSession(session);
        chatRoomManager.addSessionToRoom(session, chatRoomId);   // auto-activate
        chatRoomManager.deactivateRoom("session-1", chatRoomId); // ROOM_INACTIVE
        chatRoomManager.activateRoom("session-1", chatRoomId);   // ROOM_ACTIVE (복구)

        assertThat(chatRoomManager.isRoomActive(memberId, chatRoomId)).isTrue();
    }

    @Test
    @DisplayName("다중 세션 중 하나라도 active이면 isRoomActive가 true를 반환한다.")
    void isRoomActive_returnsTrueIfAnySessionActive() {
        Long chatRoomId = 1L;
        Long memberId = 10L;

        WebSocketSession sessionA = mock(WebSocketSession.class);
        given(sessionA.getId()).willReturn("session-a");
        given(sessionA.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        WebSocketSession sessionB = mock(WebSocketSession.class);
        given(sessionB.getId()).willReturn("session-b");
        given(sessionB.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        chatRoomManager.registerSession(sessionA);
        chatRoomManager.addSessionToRoom(sessionA, chatRoomId);   // auto-activate A

        chatRoomManager.registerSession(sessionB);
        chatRoomManager.addSessionToRoom(sessionB, chatRoomId);   // auto-activate B
        chatRoomManager.deactivateRoom("session-b", chatRoomId);  // B를 inactive로 전환

        // A만 active인 상태
        assertThat(chatRoomManager.isRoomActive(memberId, chatRoomId)).isTrue();
    }

    @Test
    @DisplayName("다중 세션 모두 inactive이면 isRoomActive가 false를 반환한다.")
    void isRoomActive_returnsFalseWhenAllSessionsInactive() {
        Long chatRoomId = 1L;
        Long memberId = 10L;

        WebSocketSession sessionA = mock(WebSocketSession.class);
        given(sessionA.getId()).willReturn("session-a");
        given(sessionA.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        WebSocketSession sessionB = mock(WebSocketSession.class);
        given(sessionB.getId()).willReturn("session-b");
        given(sessionB.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        // sessionA: add → auto-activate → deactivate
        chatRoomManager.registerSession(sessionA);
        chatRoomManager.addSessionToRoom(sessionA, chatRoomId);
        chatRoomManager.deactivateRoom("session-a", chatRoomId);

        // sessionB: add → auto-activate → deactivate
        chatRoomManager.registerSession(sessionB);
        chatRoomManager.addSessionToRoom(sessionB, chatRoomId);
        chatRoomManager.deactivateRoom("session-b", chatRoomId);

        assertThat(chatRoomManager.isRoomActive(memberId, chatRoomId)).isFalse();
    }

    @Test
    @DisplayName("방 전환(ENTER_ROOM) 시 이전 방의 active 상태가 자동으로 클리어된다.")
    void addSessionToRoom_clearsActiveStateOnRoomSwitch() {
        Long room1 = 1L;
        Long room2 = 2L;
        Long memberId = 10L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getId()).willReturn("session-1");
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        chatRoomManager.registerSession(session);
        chatRoomManager.addSessionToRoom(session, room1);   // auto-activate room1
        assertThat(chatRoomManager.isRoomActive(memberId, room1)).isTrue();

        // room2로 이동
        chatRoomManager.addSessionToRoom(session, room2);

        assertThat(chatRoomManager.isRoomActive(memberId, room1)).isFalse();  // room1 deactivated
        assertThat(chatRoomManager.isRoomActive(memberId, room2)).isTrue();   // room2 activated
    }

    @Test
    @DisplayName("removeSessionState 후 isRoomActive가 false를 반환한다.")
    void removeSessionState_clearsActiveState() {
        Long chatRoomId = 1L;
        Long memberId = 10L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getId()).willReturn("session-1");
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        chatRoomManager.registerSession(session);
        chatRoomManager.addSessionToRoom(session, chatRoomId);   // auto-activate
        assertThat(chatRoomManager.isRoomActive(memberId, chatRoomId)).isTrue();

        chatRoomManager.removeSessionState(session);

        assertThat(chatRoomManager.isRoomActive(memberId, chatRoomId)).isFalse();
    }

    @Test
    @DisplayName("방에 등록되지 않은 세션으로 activateRoom을 호출해도 active 상태가 되지 않는다.")
    void activateRoom_rejectsSessionNotInRoom() {
        Long chatRoomId = 1L;
        Long memberId = 10L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getId()).willReturn("session-1");
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        chatRoomManager.registerSession(session);
        // addSessionToRoom 호출 안 함 — 방에 없음

        chatRoomManager.activateRoom("session-1", chatRoomId);

        assertThat(chatRoomManager.isRoomActive(memberId, chatRoomId)).isFalse();
    }

    @Test
    @DisplayName("다른 방으로 이동 시 이전 방에서 세션이 자동으로 제거된다.")
    void addSessionToRoom_switchRoomTest() {
        // given
        Long room1 = 1L;
        Long room2 = 2L;
        Long memberId = 10L;

        WebSocketSession session = mock(WebSocketSession.class);
        given(session.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID,
                memberId));

        chatRoomManager.addSessionToRoom(session, room1);
        assertThat(chatRoomManager.isInRoom(room1, memberId)).isTrue();

        // when: 2방으로 이동
        chatRoomManager.addSessionToRoom(session, room2);

        // then: 1방에서 제거, 2방에만 존재
        assertThat(chatRoomManager.isInRoom(room1, memberId)).isFalse();
        assertThat(chatRoomManager.isInRoom(room2, memberId)).isTrue();
        assertThat(chatRoomManager.getWebSocketSessionBy(room1)).isEmpty();
        assertThat(chatRoomManager.getChatRoomIdsBy(memberId)).containsExactly(room2);
    }
}
