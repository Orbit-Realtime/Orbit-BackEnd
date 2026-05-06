package com.chat.entity;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "discussion",
        uniqueConstraints = @UniqueConstraint(
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

    private Discussion(Message rootMessage) {
        validateRootMessage(rootMessage);
        this.rootMessage = rootMessage;
    }

    public static Discussion of(Message rootMessage) {
        return new Discussion(rootMessage);
    }

    private static void validateRootMessage(Message rootMessage) {
        if (rootMessage == null) {
            throw new CustomException(ErrorCode.MESSAGE_NOT_FOUND);
        }
    }
}