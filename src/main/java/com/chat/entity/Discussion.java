package com.chat.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "discussion", uniqueConstraints
        = @UniqueConstraint(
                name = "uq_discussion_root_message_id",
                columnNames = "root_message_id"
        )
)
public class Discussion extends BaseEntity {

    @Id
    @GeneratedValue
    @Column(name = "discussion_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_message_id", nullable = false)
    private Message rootMessage;

    public static Discussion of(Message rootMessage) {
        Discussion discussion = new Discussion();
        discussion.rootMessage = rootMessage;
        return discussion;
    }
}
