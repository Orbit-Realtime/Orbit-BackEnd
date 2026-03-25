package com.chat.service.dtos;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;

import java.util.List;

@Getter
public class ChatHistoryResponse {

    private Long lastReadChatId;
    private List<ChatHistory> messages;
    @JsonIgnore
    private int updatedCount;

    public ChatHistoryResponse(Long lastReadChatId, List<ChatHistory> messages, int updatedCount) {
        this.lastReadChatId = lastReadChatId;
        this.messages = messages;
        this.updatedCount = updatedCount;
    }
}
