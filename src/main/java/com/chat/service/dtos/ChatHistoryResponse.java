package com.chat.service.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ChatHistoryResponse {

    private Long lastReadChatId;
    private List<ChatHistory> messages;
}
