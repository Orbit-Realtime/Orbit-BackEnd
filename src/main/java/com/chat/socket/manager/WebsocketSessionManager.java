package com.chat.socket.manager;

import com.chat.utils.annotation.VisibleForTesting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebsocketSessionManager {

    // 소켓에 연결된 사용자 정보
    private final Map<Long, Set<WebSocketSession>> activeMemberSessions = new ConcurrentHashMap<>();

    public void addSession(Long memberId, WebSocketSession session) {

        ConcurrentWebSocketSessionDecorator safeSession
                = new ConcurrentWebSocketSessionDecorator(session, 10_000, 256 * 1024);

        activeMemberSessions
                .computeIfAbsent(memberId, k -> ConcurrentHashMap.newKeySet())
                .add(safeSession);
    }

    public WebSocketSession getWrappedSession(WebSocketSession rawSession) {
        return activeMemberSessions.values().stream()
                .flatMap(Set::stream)
                .filter(s -> s.getId().equals(rawSession.getId()))
                .findFirst()
                .orElse(rawSession);
    }

    public Collection<WebSocketSession> getSessionBy(Long memberId) {
        Set<WebSocketSession> webSocketSessions = activeMemberSessions.get(memberId);
        return webSocketSessions != null ? webSocketSessions : Collections.emptySet();
    }

    public void removeSession(Long memberId, WebSocketSession session) {

        activeMemberSessions.computeIfPresent(memberId, (k, sessions) -> {
            sessions.removeIf(s -> s.getId().equals(session.getId()));
            return sessions.isEmpty() ? null : sessions;
        });
    }

    @VisibleForTesting
    public void clearAll() {
        activeMemberSessions.clear();
    }
}
