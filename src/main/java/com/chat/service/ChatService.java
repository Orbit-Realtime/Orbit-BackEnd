package com.chat.service;

import com.chat.entity.*;
import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.repository.*;
import com.chat.repository.dtos.ChatUnreadCount;
import com.chat.service.dtos.ChatHistory;
import com.chat.service.dtos.SaveChatData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ChatRepository chatRepository;
    private final ChatReadRepository chatReadRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final MemberRepository memberRepository;

    public SaveChatData findChatData(Long chatId) {
        Chat findChat = chatRepository.findById(chatId).orElseThrow(
                () -> new CustomException(ErrorCode.CHAT_NOT_EXIST)
        );
        Long unReadcount = chatReadRepository.findUnReadCountBy(chatId);

        return SaveChatData
                .builder()
                .chatId(findChat.getId())
                .createdDate(findChat.getCreatedDate())
                .unReadCount(unReadcount)
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

        // 내가 보낸 메시지 이전의 메시지 모두 읽음처리
        chatReadRepository.updateUnreadChatReadsToRead(senderId, chatRoomId);

        // 읽음 저장
        List<ChatRoomParticipant> findChatRoomParticipants
                = chatRoomParticipantRepository
                .findAllFetchMemberBy(chatRoomId);

        for (ChatRoomParticipant findChatRoomParticipant : findChatRoomParticipants) {

            Member participant = findChatRoomParticipant.getMember();

            if (!participant.getId().equals(senderId)) {
                boolean isRead = findChatRoomParticipant.isParticipate();
                ChatRead chatRead = new ChatRead(isRead, participant, chat);
                chatReadRepository.save(chatRead);
            } else {
                chatReadRepository.save(new ChatRead(true, participant, chat));
            }
        }
    }

    @Transactional
    public List<ChatHistory> findChatHistory(Long chatRoomId, Long memberId) {

        Member findMember = memberRepository.findById(memberId).orElseThrow(
                () -> new CustomException(ErrorCode.MEMBER_NOT_FOUND)
        );

        return createChatHistoryResponse(chatRoomId, findMember.getId());
    }

    private List<ChatHistory> createChatHistoryResponse(Long chatRoomId, Long memberId) {
        List<Chat> chats = chatRepository.findChatHistory(chatRoomId);

        if (chats.isEmpty()) {
            return List.of();
        }

        List<Long> chatIds = chats.stream()
                .map(Chat::getId)
                .toList();

        chatReadRepository.updateUnreadChatReadsToRead(memberId, chatRoomId);

        Map<Long, Long> unreadCountMap = chatReadRepository.countUnreadByChatIds(chatIds).stream()
                .collect(Collectors.toMap(
                        ChatUnreadCount::getChatId,
                        ChatUnreadCount::getUnreadCount
                ));

        List<ChatHistory> response = new ArrayList<>(chats.size());
        for (Chat chat : chats) {
            Member sender = chat.getMember();
            Long unreadCount = unreadCountMap.getOrDefault(chat.getId(), 0L);

            response.add(ChatHistory.builder()
                    .chatId(chat.getId())
                    .senderNickname(sender.getNickname())
                    .senderId(sender.getId())
                    .message(chat.getMessage())
                    .unReadCount(unreadCount)
                    .createdDate(chat.getCreatedDate())
                    .build());
        }
        return response;
    }
}
