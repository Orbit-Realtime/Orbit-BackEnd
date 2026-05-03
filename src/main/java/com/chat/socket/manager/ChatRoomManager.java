package com.chat.socket.manager;

import com.chat.utils.annotation.VisibleForTesting;
import com.chat.utils.consts.SessionConst;
import com.chat.utils.valid.IdValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatRoomManager {

    private final Map<Long, Set<WebSocketSession>> chatRooms = new ConcurrentHashMap<>();
    private final Map<Long, Set<Long>> memberToRoomsMap = new ConcurrentHashMap<>();
    private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

    public void addSessionToRoom(WebSocketSession session, Long chatRoomId) {

        IdValidator.requireChatRoomId(chatRoomId);
        Long loginMemberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);

        // 방 전환
        chatRooms.forEach((roomId, sessions) -> {
            if (!roomId.equals(chatRoomId) &&
                    sessions.stream().anyMatch(s -> s.getId().equals(session.getId()))) {
                removeChatRoomSession(roomId, session);

                SessionState state = sessionStates.get(session.getId());
                if (state != null) {
                    state.deactivatedIfRoom(roomId);
                } else {
                    log.warn("addSessionToRoom: no SessionState for sessionId={}", session.getId());
                }
            }
        });

        chatRooms.computeIfAbsent(chatRoomId, key -> ConcurrentHashMap.newKeySet()).add(session);
        memberToRoomsMap.computeIfAbsent(loginMemberId, k -> ConcurrentHashMap.newKeySet()).add(chatRoomId);
        SessionState state = sessionStates.get(session.getId());
        if (state != null) {
            state.activate(chatRoomId);
        } else {
            log.warn("addSessionToRoom: no SessionState for sessionId={}", session.getId());
        }
    }

    public boolean isInRoom(Long chatRoomId, Long memberId) {
        return getWebSocketSessionBy(chatRoomId).stream()
                .anyMatch(s -> memberId.equals(s.getAttributes().get(SessionConst.SESSION_ID)));
    }

    public Set<WebSocketSession> getWebSocketSessionBy(Long chatRoomId) {
        Set<WebSocketSession> sessions = chatRooms.get(chatRoomId);
        if (sessions == null) {
            return Collections.emptySet();
        }
        return sessions;
    }

    public Set<Long> getChatRoomIdsBy(Long memberId) {
        Set<Long> rooms = memberToRoomsMap.get(memberId);
        if (rooms == null) return Collections.emptySet();
        return Set.copyOf(rooms);
    }

    public boolean removeChatRoomSession(Long chatRoomId, WebSocketSession closingSession) {
        Set<WebSocketSession> sessions = chatRooms.get(chatRoomId);
        if (sessions == null) {
            return false;
        }

        sessions.removeIf(s -> s.getId().equals(closingSession.getId()));

        Long memberId = (Long) closingSession.getAttributes().get(SessionConst.SESSION_ID);
        // 이 방에 같은 memberId의 다른 세션이 남아있는지 확인
        boolean memberStillInRoom = sessions.stream()
                .anyMatch(s ->
                        memberId.equals(s.getAttributes().get(SessionConst.SESSION_ID)));

        if (!memberStillInRoom) {
            Set<Long> rooms = memberToRoomsMap.get(memberId);
            if (rooms != null) {
                rooms.remove(chatRoomId);
                if (rooms.isEmpty()) memberToRoomsMap.remove(memberId);
            }
        }

        if (sessions.isEmpty()) {
            chatRooms.remove(chatRoomId);
        }

        return !memberStillInRoom;
    }

    public void registerSession(WebSocketSession session) {
        Long memberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);
        sessionStates.put(session.getId(), new SessionState(memberId));
    }

    public void activateRoom(String sessionId, Long chatRoomId) {
        SessionState state = sessionStates.get(sessionId);
        if (state == null) {
            log.warn("activateRoom: no SessionState for sessionId={}", sessionId);
            return;
        }

        Set<WebSocketSession> sessions = chatRooms.get(chatRoomId);
        if (sessions == null ||
                sessions.stream().noneMatch(s -> s.getId().equals(sessionId))) {

            log.warn("activateRoom rejected: session={} not in room={}", sessionId, chatRoomId);
            return;
        }
        state.activate(chatRoomId);
    }

    public boolean isRoomActive(Long memberId, Long chatRoomId) {
        return sessionStates.values().stream()
                .anyMatch(state -> memberId.equals(state.getMemberId())
                        && chatRoomId.equals(state.getActiveRoomId()));
    }

    public void deactivateRoom(String sessionId, Long chatRoomId) {
        SessionState state = sessionStates.get(sessionId);
        if (state == null) {
            return;
        }
        state.deactivatedIfRoom(chatRoomId);
    }

    public void removeSessionState(WebSocketSession session) {
        sessionStates.remove(session.getId());
    }

    @VisibleForTesting
    public void clearAll() {
        chatRooms.clear();
        memberToRoomsMap.clear();
        sessionStates.clear();
    }
}
