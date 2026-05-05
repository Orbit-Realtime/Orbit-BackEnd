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
public class SpaceMember extends BaseEntity {

    @Id
    @GeneratedValue
    @Column(name = "chat_room_participant_id")
    private Long id;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "chat_room_id")
    private Space space;

    @Builder
    public SpaceMember(Member member, Space space) {
        this.member = member;
        this.space = space;
    }

    public void updateLastReadMessageId(Long messageId) {
        if (messageId == null) {
            return;
        }

        if (this.lastReadMessageId == null || this.lastReadMessageId < messageId) {
            this.lastReadMessageId = messageId;
        }
    }
}
