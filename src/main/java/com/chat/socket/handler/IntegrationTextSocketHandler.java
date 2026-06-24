package com.chat.socket.handler;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.service.DiscussionMessageService;
import com.chat.service.SpaceService;
import com.chat.service.MessageService;
import com.chat.service.MemberService;
import com.chat.service.dtos.chat.EnterRoomAckResponse;
import com.chat.service.dtos.chat.EnterRoomRequest;
import com.chat.service.dtos.chat.ErrorResponse;
import com.chat.service.dtos.chat.RoomActiveRequest;
import com.chat.service.dtos.chat.RoomInactiveRequest;
import com.chat.service.dtos.chat.SendChat;
import com.chat.service.dtos.chat.SendDiscussionMessage;
import com.chat.socket.manager.SpaceManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.chat.utils.consts.SessionConst;
import com.chat.utils.message.BaseWebSocketMessage;
import com.chat.utils.message.MessageType;
import com.chat.utils.valid.IdValidator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class IntegrationTextSocketHandler extends TextWebSocketHandler {

    private final WebsocketSessionManager websocketSessionManager;
    private final SpaceManager spaceManager;
    private final SpaceService spaceService;
    private final MessageService messageService;
    private final MemberService memberService;
    private final DiscussionMessageService discussionMessageService;
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
        spaceManager.registerSession(session);

        log.info("Connect Websocket member : {}", loginMemberId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        BaseWebSocketMessage baseMessage;
        try {
            baseMessage = objectMapper.readValue(payload, BaseWebSocketMessage.class);
        } catch (JsonProcessingException e) {
            log.warn("WS 메시지 파싱 실패: session={}", session.getId(), e);
            sendError(session, null, null, "INVALID_MESSAGE", "메시지 형식이 올바르지 않습니다.");
            return;
        }

        try {
            switch (baseMessage.getMessageType()) {
                case CHAT_MESSAGE:
                    SendChat sendChat = (SendChat) baseMessage;
                    Long chatRoomId = sendChat.getChatRoomId();
                    Long loginMemberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);

                    if (spaceManager.getWebSocketSessionBy(chatRoomId).stream()
                            .noneMatch(s -> s.getId().equals(session.getId()))) {
                        log.warn("session not in room: session={}, chatRoomId={}", session.getId(),
                                chatRoomId);
                        break;
                    }

                    log.info("chat : {} member : {}", payload, loginMemberId);

                    spaceService.broadCastMessage(loginMemberId, sendChat);

                    break;
                case ENTER_ROOM:
                    EnterRoomRequest enterRoomRequest = (EnterRoomRequest) baseMessage;
                    IdValidator.requireChatRoomId(enterRoomRequest.getChatRoomId());
                    Long enterMemberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);
                    spaceService.validateParticipant(enterMemberId, enterRoomRequest.getChatRoomId());
                    WebSocketSession safeSession = websocketSessionManager.getWrappedSession(session);
                    spaceManager.addSessionToSpace(safeSession, enterRoomRequest.getChatRoomId());
                    sendEnterRoomAck(safeSession, enterRoomRequest.getChatRoomId());

                    break;
                case ROOM_ACTIVE:
                    RoomActiveRequest activeRequest = (RoomActiveRequest) baseMessage;
                    Long activeRoomId = activeRequest.getChatRoomId();
                    Long activeMemberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);

                    spaceManager.activateSpace(session.getId(), activeRoomId);
                    messageService.onRoomActive(activeMemberId, activeRoomId);
                    break;
                case ROOM_INACTIVE:
                    RoomInactiveRequest inactiveRequest = (RoomInactiveRequest) baseMessage;
                    spaceManager.deactivateSpace(session.getId(), inactiveRequest.getChatRoomId());
                    break;
                case DISCUSSION_MESSAGE:
                    SendDiscussionMessage sendDiscussionMessage = (SendDiscussionMessage) baseMessage;
                    Long discussionMemberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);
                    discussionMessageService.broadcastDiscussionMessage(
                            sendDiscussionMessage.getDiscussionId(),
                            discussionMemberId,
                            sendDiscussionMessage.getContent()
                    );
                    break;
                default:
                    log.warn("알 수 없는 messageType: session={}, type={}", session.getId(), baseMessage.getMessageType());
                    sendError(session, baseMessage.getMessageType(), null, "INVALID_MESSAGE", "알 수 없는 메시지 타입입니다.");
            }
        } catch (CustomException e) {
            log.warn("WS 처리 중 CustomException: session={}, error={}", session.getId(), e.getErrorCode(), e);
            sendError(session, baseMessage.getMessageType(), extractChatRoomId(baseMessage),
                    mapErrorCode(e.getErrorCode()), e.getErrorCode().getErrorMessage());
        } catch (Exception e) {
            log.error("WS 처리 중 예상치 못한 오류: session={}", session.getId(), e);
            sendError(session, baseMessage.getMessageType(), extractChatRoomId(baseMessage),
                    "INTERNAL_ERROR", "서버 오류가 발생했습니다.");
        }
    }

    private void sendEnterRoomAck(WebSocketSession session, Long chatRoomId) {
        if (!session.isOpen()) return;
        try {
            EnterRoomAckResponse ack = EnterRoomAckResponse.builder()
                    .messageType(MessageType.ENTER_ROOM_ACK)
                    .chatRoomId(chatRoomId)
                    .build();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
        } catch (IOException e) {
            log.warn("ENTER_ROOM_ACK 전송 실패: session={}", session.getId(), e);
        }
    }

    private void sendError(WebSocketSession session, MessageType requestType, Long chatRoomId,
                            String errorCode, String message) {
        if (!session.isOpen()) return;
        try {
            ErrorResponse error = ErrorResponse.builder()
                    .messageType(MessageType.ERROR)
                    .requestType(requestType)
                    .chatRoomId(chatRoomId)
                    .errorCode(errorCode)
                    .message(message)
                    .build();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
        } catch (IOException e) {
            log.warn("ERROR 이벤트 전송 실패: session={}", session.getId(), e);
        }
    }

    private Long extractChatRoomId(BaseWebSocketMessage baseMessage) {
        if (baseMessage instanceof EnterRoomRequest r) return r.getChatRoomId();
        if (baseMessage instanceof SendChat r) return r.getChatRoomId();
        if (baseMessage instanceof RoomActiveRequest r) return r.getChatRoomId();
        if (baseMessage instanceof RoomInactiveRequest r) return r.getChatRoomId();
        return null;
    }

    private String mapErrorCode(ErrorCode errorCode) {
        return switch (errorCode) {
            case SPACE_NOT_FOUND -> "ROOM_NOT_FOUND";
            case USER_NOT_AUTHENTICATED -> "UNAUTHORIZED";
            default -> "INTERNAL_ERROR";
        };
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
