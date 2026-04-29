package com.chat.repository.dtos;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MemberUnreadCount {

    private Long memberId;
    private Long unreadMessageCount;

    public MemberUnreadCount(Long memberId, Long unreadMessageCount) {
        this.memberId = memberId;
        this.unreadMessageCount = unreadMessageCount;
    }
}
