package com.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = {@Index(name = "idx_chat_room_id_id", columnList = "chat_room_id, chat_id DESC")})
public class Message extends BaseEntity {

    @Id
    @GeneratedValue
    @Column(name = "chat_id")
    private Long id;
    private String message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id")
    private Space space;

    public Message(String message, Member member, Space space) {
        this.message = message;
        this.member = member;
        this.space = space;
    }
}
