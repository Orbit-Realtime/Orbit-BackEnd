package com.chat.service.dtos;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class SaveMessageData {

    private Long chatId;
    private Long unreadMemberCount;
    private LocalDateTime createdDate;

    @Builder
    public SaveMessageData(Long chatId, Long unreadMemberCount, LocalDateTime createdDate) {
        this.chatId = chatId;
        this.unreadMemberCount = unreadMemberCount;
        this.createdDate = createdDate;
    }
}
