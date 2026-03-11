package com.chat.service;

import com.chat.api.response.chatroom.ChatRoomsResponse;
import com.chat.api.response.chatroom.OpponentResponse;
import com.chat.entity.*;
import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.repository.*;
import com.chat.repository.dtos.ChatRoomUnreadCount;
import com.chat.service.dtos.LastChatRead;
import com.chat.service.dtos.SaveChatData;
import com.chat.service.dtos.SaveChatRoomDTO;
import com.chat.service.dtos.chat.EnterChatRoom;
import com.chat.service.dtos.chat.SendChat;
import com.chat.service.dtos.chat.UpdateChatRoom;
import com.chat.socket.event.PublishEnterRoomEvent;
import com.chat.socket.event.PublishMessageEvent;
import com.chat.socket.manager.ChatRoomManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.chat.utils.message.MessageType;
import com.chat.utils.valid.IdValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private final ApplicationEventPublisher publisher;

    private final ChatRoomParticipantService chatRoomParticipantService;
    private final ChatReadService chatReadService;
    private final ChatService chatService;

    private final WebsocketSessionManager websocketSessionManager;
    private final ChatRoomManager chatRoomManager;
    private final ObjectMapper objectMapper;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final ChatRepository chatRepository;
    private final ChatReadRepository chatReadRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void connectChatRoomSocket(WebSocketSession session, Long memberId, Long chatRoomId) {

        IdValidator.requireIds(memberId, chatRoomId);

        chatRoomParticipantService.enterChatRoom(chatRoomId, memberId);

        LastChatRead lastChatRead = chatReadService.findLastChatBy(memberId, chatRoomId);
        EnterChatRoom enterChatRoom = EnterChatRoom.builder()
                .messageType(MessageType.CHAT_ENTER)
                .lastReadChatId(lastChatRead != null ? lastChatRead.getLastChatReadId() : null)
                .memberId(memberId)
                .build();
        publisher.publishEvent(new PublishEnterRoomEvent(session, chatRoomId, enterChatRoom));
    }

    @Transactional
    public void broadCastMessage(SendChat sendChat) {
        Long chatRoomId = sendChat.getChatRoomId();
        Set<WebSocketSession> SessionsInChatRoom = chatRoomManager.getWebSocketSessionBy(chatRoomId);

        Long senderId = sendChat.getSenderId();
        String message = sendChat.getMessage();
        Long saveChatId = chatService.saveChat(senderId, chatRoomId, message);

        SaveChatData chatData = chatService.findChatData(saveChatId);
        sendChat.updateSavedChat(chatData);

        publisher.publishEvent(new PublishMessageEvent(sendChat, SessionsInChatRoom));
//        chatBroadcastListener.publishMessageToSessions(new PublishMessageEvent(sendChat, SessionsInChatRoom));
    }

    public void broadcastToChatRoomMembers(Long chatRoomId) {

        Pageable limitOne = createLimitOne();

        List<Long> memberIdsInChatRoom = memberRepository.findMemberIdsIn(chatRoomId);
        for (Long memberId : memberIdsInChatRoom) {

            Collection<WebSocketSession> sessions = websocketSessionManager.getSessionBy(memberId);
            if (sessions.isEmpty()) {
                continue;
            }

            Chat lastChat = chatRepository
                    .findLastChatBy(chatRoomId, limitOne)
                    .stream()
                    .findFirst()
                    .orElse(null);
            Long unReadCount = chatReadRepository.findUnReadCountBy(chatRoomId, memberId);

            UpdateChatRoom updateChatRoom = UpdateChatRoom
                    .builder()
                    .messageType(MessageType.UPDATE_CHAT_ROOM)
                    .chatRoomId(chatRoomId)
                    .lastMessage(lastChat != null ? lastChat.getMessage() : null)
                    .createdDate(lastChat != null ? lastChat.getCreatedDate() : null)
                    .unReadCount(unReadCount)
                    .build();

            try {
                String updateChatRoomString = objectMapper.writeValueAsString(updateChatRoom);
                for (WebSocketSession session : sessions) {
                    session.sendMessage(new TextMessage(updateChatRoomString));
                }
            } catch (IOException e) {
                throw new CustomException(ErrorCode.CHAT_ROOM_BROADCAST_IO_EXCEPTION);
            }
        }
    }

    @Transactional
    public Long saveChatRoom(SaveChatRoomDTO saveChatRoomDTO) {

        Long senderId = saveChatRoomDTO.getSenderId();
        Set<Long> receiverIds = saveChatRoomDTO.getReceiverIds();
        isSenderIncludeInReceivers(senderId, receiverIds);

        Member findSender = memberRepository.findById(senderId).orElseThrow(
                () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        List<Member> findReceivers = findReceiverMembers(saveChatRoomDTO.getReceiverIds());

        isExistChatRoom(senderId, receiverIds);

        List<String> participants = createParticipants(findSender, findReceivers);

        String title = ensureTitle(saveChatRoomDTO.getTitle(), participants);

        ChatRoom chatRoom = ChatRoom.of(title);
        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);

        saveChatRoomParticipants(savedChatRoom, findSender, findReceivers);

        return savedChatRoom.getId();
    }

    private void isSenderIncludeInReceivers(Long senderId, Set<Long> receiverIds) {
        if (receiverIds.contains(senderId)) {
            throw new CustomException(ErrorCode.Include_Sender_In_Receivers);
        }
    }

    private List<Member> findReceiverMembers(Set<Long> receiverIds) {
        List<Member> receivers = memberRepository.findAllById(receiverIds);
        if (receiverIds.size() != receivers.size()) {
            throw new CustomException(ErrorCode.MEMBERS_NOT_FOUDN);
        }
        return receivers;
    }

    private void isExistChatRoom(Long senderId, Set<Long> receiverIds) {
        List<Long> memberIds = Stream.concat(Stream.of(senderId), receiverIds.stream())
                .collect(Collectors.toList());

        List<Long> chatRoomIds = chatRoomParticipantRepository.findChatRoomIdsByExactMembers(memberIds, memberIds.size());
        if (!chatRoomIds.isEmpty()) {
            throw new CustomException(ErrorCode.CHAT_ROOM_ALREADY_EXIST);
        }
    }

    private String ensureTitle(String title, List<String> participants) {
        if (title == null || title.isEmpty()) {
            return generateDefaultTitle(participants);
        }

        return title;
    }

    private String generateDefaultTitle(List<String> participants) {
        return participants.stream()
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private List<String> createParticipants(Member sender, List<Member> receivers) {
        List<String> receiverUsernames = receivers.stream()
                .map(Member::getUsername)
                .collect(Collectors.toList());
        receiverUsernames.add(sender.getUsername());

        return receiverUsernames;
    }

    private void saveChatRoomParticipants(ChatRoom chatRoom, Member sender, List<Member> receivers) {
        ChatRoomParticipant senderChatRoomParticipant
                = ChatRoomParticipant.builder().chatRoom(chatRoom).member(sender).build();
        chatRoomParticipantRepository.save(senderChatRoomParticipant);

        for (Member findReceiver : receivers) {
            ChatRoomParticipant receiverChatRoomParticipant
                    = ChatRoomParticipant.builder().chatRoom(chatRoom).member(findReceiver).build();
            chatRoomParticipantRepository.save(receiverChatRoomParticipant);
        }
    }

    public List<ChatRoomsResponse> findChatRooms(Long memberId) {

        Member findMember = memberRepository.findById(memberId).orElseThrow(
                () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
        );

        return createChatRoomsResponse(findMember.getId());
    }

    private List<ChatRoomsResponse> createChatRoomsResponse(Long memberId) {

        // 참여 채팅방 목록 조회
        List<ChatRoomParticipant> participants
                = chatRoomParticipantRepository.findAllFetchChatRoomBy(memberId);

        if (participants.isEmpty()) {
            return List.of();
        }

        List<Long> chatRoomIds = participants
                .stream()
                .map(crp -> crp.getChatRoom().getId())
                .toList();

        // 채팅방별 마지막 메시지 일괄 조회
        Map<Long, Chat> lastChatMap = chatRepository
                .findLastChatsBy(chatRoomIds)
                .stream()
                .collect(Collectors.toMap(
                        c -> c.getChatRoom().getId(),
                        c -> c
                ));

        // 채팅방별 안 읽은 메시지 수 일괄 조회
        Map<Long, Long> unReadCountMap = chatReadRepository
                .findChatRoomUnreadCountsBy(chatRoomIds, memberId)
                .stream()
                .collect(Collectors.toMap(
                        ChatRoomUnreadCount::getChatRoomId,
                        ChatRoomUnreadCount::getUnreadCount
                ));

        // 채팅방별 참여자 목록 일괄 조회
        Map<Long, List<ChatRoomParticipant>> participantsByRoom = chatRoomParticipantRepository
                .findAllFetchMemberBy(chatRoomIds)
                .stream()
                .collect(Collectors.groupingBy(
                        crp -> crp.getChatRoom().getId()
                ));

        return participants
                .stream()
                .map(crp -> {
                    Long chatRoomId = crp.getChatRoom().getId();
                    Chat lastChat = lastChatMap.get(chatRoomId);

                    List<OpponentResponse> opponents = createOpponentResponses(
                            participantsByRoom.getOrDefault(chatRoomId, List.of()),
                            memberId);

                    return ChatRoomsResponse.builder()
                            .chatRoomId(chatRoomId)
                            .title(crp.getChatRoom().getTitle())
                            .lastMessage(lastChat != null ? lastChat.getMessage() : null)
                            .createdDate(lastChat != null ? lastChat.getCreatedDate() : null)
                            .unReadCount(unReadCountMap.getOrDefault(chatRoomId, 0L))
                            .opponents(opponents)
                            .build();
                })
                .toList();
    }

    private List<OpponentResponse> createOpponentResponses(List<ChatRoomParticipant> chatRoomParticipants, Long memberId) {
        return chatRoomParticipants.stream()
                .map(ChatRoomParticipant::getMember)
                .filter(member -> !member.getId().equals(memberId))
                .map(member -> new OpponentResponse(member.getId(), member.getNickname()))
                .collect(Collectors.toList());
    }

    private Pageable createLimitOne() {
        return PageRequest.of(0, 1);
    }
}
