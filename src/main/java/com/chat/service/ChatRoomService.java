package com.chat.service;

import com.chat.api.response.chatroom.ChatRoomMemberResponse;
import com.chat.api.response.chatroom.ChatRoomsResponse;
import com.chat.entity.*;
import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.repository.*;
import com.chat.repository.dtos.RoomUnreadMessageCount;
import com.chat.service.dtos.SaveChatData;
import com.chat.service.dtos.SaveChatRoomDTO;
import com.chat.service.dtos.chat.BroadcastChat;
import com.chat.service.dtos.chat.SendChat;
import com.chat.service.dtos.chat.UpdateChatRoom;
import com.chat.socket.event.PublishMessageEvent;
import com.chat.socket.event.PublishUpdateEvent;
import com.chat.socket.manager.ChatRoomManager;
import com.chat.utils.consts.SessionConst;
import com.chat.utils.message.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private final ApplicationEventPublisher publisher;
    private final BroadcastDataBuilder broadcastDataBuilder;
    private final ChatRoomManager chatRoomManager;

    private final ChatService chatService;

    private final SpaceRepository spaceRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final ChatRepository chatRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void broadCastMessage(Long memberId, SendChat sendChat) {
        Long chatRoomId = sendChat.getChatRoomId();
        Long saveChatId = chatService.saveChat(memberId, chatRoomId, sendChat.getMessage());

        Member sender = memberRepository.findById(memberId).orElseThrow(
                () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
        );

        SaveChatData chatData = chatService.findChatData(saveChatId);

        BroadcastChat broadcastChat = BroadcastChat.builder()
                .messageType(MessageType.CHAT_MESSAGE)
                .senderId(memberId)
                .senderNickname(sender.getNickname())
                .chatRoomId(chatRoomId)
                .message(sendChat.getMessage())
                .chatId(chatData.getChatId())
                .unreadMemberCount(chatData.getUnreadMemberCount())
                .createdDate(chatData.getCreatedDate())
                .build();

        Map<Long, UpdateChatRoom> updatesByMemberId = broadcastDataBuilder.build(chatRoomId);
        publisher.publishEvent(new PublishMessageEvent(broadcastChat, chatRoomId, updatesByMemberId));
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

        Space chatRoom = Space.of(title);
        Space savedChatRoom = spaceRepository.save(chatRoom);

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

    private void saveChatRoomParticipants(Space chatRoom, Member sender, List<Member> receivers) {
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

    public void validateParticipant(Long memberId, Long chatRoomId) {
        ChatRoomParticipant participant = chatRoomParticipantRepository.findChatRoomBy(chatRoomId, memberId);
        if (participant == null) {
            throw new CustomException(ErrorCode.CHAT_ROOM_NOT_EXIST);
        }
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
        Map<Long, Long> unreadMessageCountMap = chatRoomParticipantRepository
                .findRoomUnreadMessageCountsBy(chatRoomIds, memberId)
                .stream()
                .collect(Collectors.toMap(
                        RoomUnreadMessageCount::getChatRoomId,
                        RoomUnreadMessageCount::getUnreadMessageCount
                ));

        return participants
                .stream()
                .map(crp -> {
                    Long chatRoomId = crp.getChatRoom().getId();
                    Chat lastChat = lastChatMap.get(chatRoomId);

                    return ChatRoomsResponse.builder()
                            .chatRoomId(chatRoomId)
                            .title(crp.getChatRoom().getTitle())
                            .lastMessage(lastChat != null ? lastChat.getMessage() : null)
                            .lastChatId(lastChat != null ? lastChat.getId() : null)
                            .createdDate(lastChat != null ? lastChat.getCreatedDate() : null)
                            .unreadMessageCount(unreadMessageCountMap.getOrDefault(chatRoomId, 0L))
                            .build();
                })
                .toList();
    }

    @Transactional
    public void leaveChatRoom(Long memberId, Long chatRoomId) {
        ChatRoomParticipant participant
                = chatRoomParticipantRepository.findChatRoomBy(chatRoomId, memberId);
        if (participant == null) {
            throw new CustomException(ErrorCode.CHAT_ROOM_NOT_EXIST);
        }

        chatRoomParticipantRepository.deleteBy(chatRoomId, memberId);

        Set<WebSocketSession> sessions = new HashSet<>(chatRoomManager.getWebSocketSessionBy(chatRoomId));
        for (WebSocketSession session : sessions) {
            Long sessionMemberId = (Long) session.getAttributes().get(SessionConst.SESSION_ID);
            if (memberId.equals(sessionMemberId)) {
                chatRoomManager.removeChatRoomSession(chatRoomId, session);
            }
        }

        Map<Long, UpdateChatRoom> updatesByMemberId = broadcastDataBuilder.build(chatRoomId);
        publisher.publishEvent(new PublishUpdateEvent(chatRoomId, updatesByMemberId));
    }

    @Transactional
    public void renameChatRoom(Long memberId, Long chatRoomId, String title) {

        ChatRoomParticipant participant = chatRoomParticipantRepository.findChatRoomBy(chatRoomId, memberId);
        if (participant == null) {
            throw new CustomException(ErrorCode.CHAT_ROOM_NOT_EXIST);
        }

        Space chatRoom = spaceRepository.findById(chatRoomId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHAT_ROOM_NOT_EXIST));
        chatRoom.rename(title);

        Map<Long, UpdateChatRoom> updatesByMemberId = broadcastDataBuilder.build(chatRoomId);
        publisher.publishEvent(new PublishUpdateEvent(chatRoomId, updatesByMemberId));
    }

    public List<ChatRoomMemberResponse> findChatRoomMembers(Long memberId, Long chatRoomId) {

        ChatRoomParticipant participant = chatRoomParticipantRepository.findChatRoomBy(chatRoomId, memberId);
        if (participant == null) {
            throw new CustomException(ErrorCode.CHAT_ROOM_NOT_EXIST);
        }

        return chatRoomParticipantRepository.findAllFetchMemberBy(chatRoomId)
                .stream()
                .map(crp -> new ChatRoomMemberResponse(
                        crp.getMember().getId(),
                        crp.getMember().getNickname()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public void inviteMembers(Long memberId, Long chatRoomId, Set<Long> inviteeIds) {

        ChatRoomParticipant participant = chatRoomParticipantRepository.findChatRoomBy(chatRoomId, memberId);
        if (participant == null) {
            throw new CustomException(ErrorCode.CHAT_ROOM_NOT_EXIST);
        }

        Set<Long> existingMemberIds = chatRoomParticipantRepository
                .findAllFetchMemberBy(chatRoomId)
                .stream()
                .map(crp -> crp.getMember().getId())
                .collect(Collectors.toSet());

        Space chatRoom = spaceRepository.findById(chatRoomId).orElseThrow(
                () -> new CustomException(ErrorCode.CHAT_ROOM_NOT_EXIST)
        );

        List<Member> invitees = memberRepository.findAllById(inviteeIds);
        for (Member invitee : invitees) {
            if (!existingMemberIds.contains(invitee.getId())) {
                chatRoomParticipantRepository.save(
                        ChatRoomParticipant
                                .builder()
                                .chatRoom(chatRoom)
                                .member(invitee)
                                .build()
                );
            }
        }

        Map<Long, UpdateChatRoom> updatesByMemberId = broadcastDataBuilder.build(chatRoomId);
        publisher.publishEvent(new PublishUpdateEvent(chatRoomId, updatesByMemberId));
    }
}
