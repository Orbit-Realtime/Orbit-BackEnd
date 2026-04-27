package com.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static jakarta.persistence.FetchType.LAZY;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = {
        @Index(name = "idx_chat_room_participant_chat_room_id", columnList = "chat_room_id"),
        @Index(name = "idx_chat_room_participant_member_id",   columnList = "member_id")
})
public class ChatRoomParticipant extends BaseEntity {

    @Id
    @GeneratedValue
    @Column(name = "chat_room_participant_id")
    private Long id;

    @Column(name = "last_read_chat_id")
    private Long lastReadChatId;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "chat_room_id")
    private ChatRoom chatRoom;

    @Builder
    public ChatRoomParticipant(Member member, ChatRoom chatRoom) {
        this.member = member;
        this.chatRoom = chatRoom;
    }

    public void updateLastReadChatId(Long chatId) {
        if (chatId == null) {
            return;
        }

        if (this.lastReadChatId == null || this.lastReadChatId < chatId) {
            this.lastReadChatId = chatId;
        }
    }
}
