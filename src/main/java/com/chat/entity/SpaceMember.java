package com.chat.entity;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static jakarta.persistence.FetchType.LAZY;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "space_member",
        indexes = {
                @Index(name = "idx_space_member_space_id", columnList = "space_id"),
                @Index(name = "idx_space_member_member_id", columnList = "member_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uq_space_member",
                columnNames = {"member_id", "space_id"}
        )
)
public class SpaceMember extends BaseEntity {

    @Id
    @GeneratedValue
    @Column(name = "space_member_id")
    private Long id;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id", nullable = false)
    private Space space;

    private SpaceMember(Member member, Space space) {
        validateMember(member);
        validateSpace(space);

        this.member = member;
        this.space = space;
    }

    public static SpaceMember of(Member member, Space space) {
        return new SpaceMember(member, space);
    }

    private void validateMember(Member member) {
        if (member == null) {
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND);
        }
    }

    private void validateSpace(Space space) {
        if (space == null) {
            throw new CustomException(ErrorCode.SPACE_NOT_FOUND);
        }
    }
}
