package com.chat.service.dtos;

import lombok.Getter;

import java.util.List;

@Getter
public class ChatHistoryResponse {

    private Long lastReadMessageId;
    private List<ChatHistory> messages;
    private boolean hasMore;

    public ChatHistoryResponse(Long lastReadMessageId, List<ChatHistory> messages, boolean hasMore) {
        this.lastReadMessageId = lastReadMessageId;
        this.messages = messages;
        this.hasMore = hasMore;
    }
}
