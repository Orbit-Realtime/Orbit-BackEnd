package com.chat.service;

import com.chat.entity.*;
import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.repository.*;
import com.chat.repository.dtos.ChatUnreadCount;
import com.chat.service.dtos.ChatHistory;
import com.chat.service.dtos.ChatHistoryResponse;
import com.chat.service.dtos.LastChatRead;
import com.chat.service.dtos.SaveChatData;
import com.chat.service.dtos.chat.UpdateChatRoom;
import com.chat.socket.event.PublishReadEvent;
import com.chat.socket.manager.ChatRoomManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ApplicationEventPublisher publisher;

    private final BroadcastDataBuilder broadcastDataBuilder;

    private final ChatRoomManager chatRoomManager;

    private final ChatRepository chatRepository;
    private final ChatReadRepository chatReadRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final MemberRepository memberRepository;

    public SaveChatData findChatData(Long chatId) {
        Chat findChat = chatRepository.findById(chatId).orElseThrow(
                () -> new CustomException(ErrorCode.CHAT_NOT_EXIST)
        );
        Long unreadMemberCount = chatReadRepository.findUnReadCountBy(chatId);

        return SaveChatData
                .builder()
                .chatId(findChat.getId())
                .createdDate(findChat.getCreatedDate())
                .unreadMemberCount(unreadMemberCount)
                .build();
    }

    @Transactional
    public Long saveChat(Long senderId, Long chatRoomId, String message) {

        Member findSender = memberRepository.findById(senderId).orElseThrow(
                () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
        );
        ChatRoom findChatRoom = chatRoomRepository.findById(chatRoomId).orElseThrow(
                () -> new CustomException(ErrorCode.CHAT_ROOM_NOT_EXIST)
        );

        Chat savedChat = chatRepository.save(new Chat(message, findSender, findChatRoom));
        saveChatRead(findSender.getId(), findChatRoom.getId(), savedChat);

        return savedChat.getId();
    }

    private void saveChatRead(Long senderId, Long chatRoomId, Chat chat) {

        // 발신자의 이전 미읽음 메시지 읽음 처리 (메시지를 보내는 행위 = 이전 메시지 모두 읽음)
        chatReadRepository.updateUnreadChatReadsToRead(senderId, chatRoomId);

        // 읽음 저장: 발신자=true, 방에 접속 중인 멤버=true, 나머지=false
        List<ChatRoomParticipant> findChatRoomParticipants
                = chatRoomParticipantRepository
                .findAllFetchMemberBy(chatRoomId);

        for (ChatRoomParticipant findChatRoomParticipant : findChatRoomParticipants) {
            Member participant = findChatRoomParticipant.getMember();
            boolean isRead = participant.getId().equals(senderId)
                    || chatRoomManager.isInRoom(chatRoomId, participant.getId());
            chatReadRepository.save(new ChatRead(isRead, participant, chat));
        }
    }

    @Transactional
    public ChatHistoryResponse findChatHistory(Long chatRoomId, Long memberId) {

        Member findMember = memberRepository.findById(memberId).orElseThrow(
                () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
        );

        return createChatHistoryResponse(chatRoomId, findMember.getId());
    }

    private ChatHistoryResponse createChatHistoryResponse(Long chatRoomId, Long memberId) {
        List<Chat> chats = chatRepository.findChatHistory(chatRoomId);

        if (chats.isEmpty()) {
            return new ChatHistoryResponse(null, List.of());
        }

        List<Long> chatIds = chats.stream()
                .map(Chat::getId)
                .toList();

        LastChatRead lastChatRead = chatReadRepository.findLastReadChatBy(memberId, chatRoomId)
                .stream().findFirst().orElse(null);
        Long lastReadChatId = lastChatRead != null ? lastChatRead.getLastChatReadId() : null;

        int updatedCount = chatReadRepository.updateUnreadChatReadsToRead(memberId, chatRoomId);
        if (updatedCount > 0) {
            Map<Long, UpdateChatRoom> updatesByMemberId = broadcastDataBuilder.build(chatRoomId, Set.of(memberId));
            publisher.publishEvent(new PublishReadEvent(
                    memberId,
                    chatRoomId,
                    lastReadChatId,
                    updatesByMemberId
            ));
        }

        Map<Long, Long> unreadMemberCountMap = chatReadRepository.countUnreadByChatIds(chatIds).stream()
                .collect(Collectors.toMap(
                        ChatUnreadCount::getChatId,
                        ChatUnreadCount::getUnreadMemberCount
                ));

        List<ChatHistory> messages = new ArrayList<>(chats.size());
        for (Chat chat : chats) {
            Member sender = chat.getMember();
            Long unreadCount = unreadMemberCountMap.getOrDefault(chat.getId(), 0L);

            messages.add(ChatHistory.builder()
                    .chatId(chat.getId())
                    .senderNickname(sender.getNickname())
                    .senderId(sender.getId())
                    .message(chat.getMessage())
                    .unreadMemberCount(unreadCount)
                    .createdDate(chat.getCreatedDate())
                    .build());
        }

        return new ChatHistoryResponse(lastReadChatId, messages);
    }
}
