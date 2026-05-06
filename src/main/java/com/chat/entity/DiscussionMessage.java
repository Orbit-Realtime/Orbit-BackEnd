package com.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "discussion_message")
public class DiscussionMessage extends BaseEntity {

    @Id
    @GeneratedValue
    @Column(name = "discussion_message_id")
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discussion_id", nullable = false)
    private Discussion discussion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    public static DiscussionMessage of(String content, Discussion discussion, Member member) {
        DiscussionMessage dm = new DiscussionMessage();
        dm.content = content;
        dm.discussion = discussion;
        dm.member = member;
        return dm;
    }
}
