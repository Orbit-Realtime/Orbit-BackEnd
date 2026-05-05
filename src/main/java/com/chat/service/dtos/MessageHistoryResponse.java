package com.chat.service.dtos;

import lombok.Getter;

import java.util.List;

@Getter
public class MessageHistoryResponse {

    private Long lastReadMessageId;
    private List<MessageHistory> messages;
    private boolean hasMore;

    public MessageHistoryResponse(Long lastReadMessageId, List<MessageHistory> messages, boolean hasMore) {
        this.lastReadMessageId = lastReadMessageId;
        this.messages = messages;
        this.hasMore = hasMore;
    }
}
