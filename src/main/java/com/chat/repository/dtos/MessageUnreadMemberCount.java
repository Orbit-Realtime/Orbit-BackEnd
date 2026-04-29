package com.chat.repository.dtos;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MessageUnreadMemberCount {

    private Long chatId;
    private Long unreadMemberCount;

    public MessageUnreadMemberCount(Long chatId, Long unreadMemberCount) {
        this.chatId = chatId;
        this.unreadMemberCount = unreadMemberCount;
    }
}
