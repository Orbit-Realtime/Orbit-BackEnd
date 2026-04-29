package com.chat.service;

import com.chat.entity.Chat;
import com.chat.entity.ChatRoom;
import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.repository.*;
import com.chat.repository.dtos.MemberUnreadCount;
import com.chat.service.dtos.chat.UpdateChatRoom;
import com.chat.utils.message.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BroadcastDataBuilder {

    private final MemberRepository memberRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRepository chatRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;

    public Map<Long, UpdateChatRoom> build(Long chatRoomId) {
        if (chatRoomId == null) return Map.of();

        List<Long> memberIds = memberRepository.findMemberIdsIn(chatRoomId);
        if (memberIds.isEmpty()) return Map.of();

        return build(chatRoomId, new HashSet<>(memberIds));
    }

    public Map<Long, UpdateChatRoom> build(Long chatRoomId, Set<Long> targetMemberIds) {
        if (chatRoomId == null || targetMemberIds == null || targetMemberIds.isEmpty()) {
            return Map.of();
        }

        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId).orElseThrow(
                () -> new CustomException(ErrorCode.CHAT_ROOM_NOT_EXIST)
        );

        Chat lastChat = chatRepository
                .findLastChatBy(chatRoomId, PageRequest.of(0, 1))
                .stream().findFirst().orElse(null);

        Map<Long, Long> unreadCountMap = chatRoomParticipantRepository
                .findMemberUnreadMessageCountsBy(chatRoomId, new ArrayList<>(targetMemberIds))
                .stream()
                .collect(Collectors.toMap(
                        MemberUnreadCount::getMemberId,
                        MemberUnreadCount::getUnreadMessageCount
                ));

        return targetMemberIds.stream()
                .collect(Collectors.toMap(
                        memberId -> memberId,
                        memberId -> UpdateChatRoom.builder()
                                .messageType(MessageType.UPDATE_CHAT_ROOM)
                                .chatRoomId(chatRoomId)
                                .title(chatRoom.getTitle())
                                .lastMessage(lastChat != null ? lastChat.getMessage() : null)
                                .createdDate(lastChat != null ? lastChat.getCreatedDate() : null)
                                .unreadMessageCount(unreadCountMap.getOrDefault(memberId, 0L))
                                .build()
                ));
    }
}
