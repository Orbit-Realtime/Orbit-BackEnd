package com.chat.socket.handler;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.service.ChatRoomService;
import com.chat.service.MemberService;
import com.chat.service.dtos.chat.EnterRoomRequest;
import com.chat.service.dtos.chat.RoomActiveRequest;
import com.chat.service.dtos.chat.RoomInactiveRequest;
import com.chat.service.dtos.chat.SendChat;
import com.chat.socket.manager.ChatRoomManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.chat.utils.consts.SessionConst;
import com.chat.utils.message.BaseWebSocketMessage;
import com.chat.utils.valid.IdValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationTextSocketHandler extends TextWebSocketHandler {

    private final WebsocketSessionManager websocketSessionManager;
    private final ChatRoomManager chatRoomManager;
    private final ChatRoomService chatRoomService;
    private final MemberService memberService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object sessionObject = session.getAttributes().get(SessionConst.SESSION_ID);

        if (sessionObject == null) {
            log.info("No session Id in afterConnection");
            throw new CustomException(ErrorCode.WEB_SOCKET_SESSION_NOT_EXIST);
        }

        Long loginMemberId = (Long) sessionObject;
        websocketSessionManager.addSession(loginMemberId, session);
        chatRoomManager.registerSession(session);

        log.info("Connect Websocket member : {}", loginMemberId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        BaseWebSocketMessage baseMessage = objectMapper.readValue(payload, BaseWebSocketMessage.class);

        switch (baseMessage.getMessageType()) {
            case CHAT_MESSAGE:
                SendChat sendChat = (SendChat) baseMessage;
                Long chatRoomId = sendChat.getChatRoomId();
                Long loginMemberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);

                if (chatRoomManager.getWebSocketSessionBy(chatRoomId).stream()
                        .noneMatch(s -> s.getId().equals(session.getId()))) {
                    log.warn("session not in room: session={}, chatRoomId={}", session.getId(),
                            chatRoomId);
                    break;
                }

                log.info("chat : {} member : {}", payload, loginMemberId);

                chatRoomService.broadCastMessage(loginMemberId, sendChat);

                break;
            case ENTER_ROOM:
                EnterRoomRequest enterRoomRequest = (EnterRoomRequest) baseMessage;
                IdValidator.requireChatRoomId(enterRoomRequest.getChatRoomId());
                WebSocketSession safeSession = websocketSessionManager.getWrappedSession(session);
                chatRoomManager.addSessionToRoom(safeSession, enterRoomRequest.getChatRoomId());

                break;
            case ROOM_ACTIVE:
                RoomActiveRequest activeRequest = (RoomActiveRequest) baseMessage;
                chatRoomManager.activateRoom(session.getId(), activeRequest.getChatRoomId());
                break;
            case ROOM_INACTIVE:
                RoomInactiveRequest inactiveRequest = (RoomInactiveRequest) baseMessage;
                chatRoomManager.deactivateRoom(session.getId(), inactiveRequest.getChatRoomId());
                break;
            default:
                //todo 채팅 메시지 예외처리
                log.info("exception");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long loginMemberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);

        if (loginMemberId == null) {
            log.warn("afterConnectionClosed: SESSION_ID not found, session={}", session.getId());
            return;
        }

        log.info("close Websocket member : {}", loginMemberId);
        memberService.removeSession(loginMemberId, session);
    }

}
