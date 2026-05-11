package com.chat.service.dtos.chat;

import com.chat.utils.message.BaseWebSocketMessage;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
public class SendDiscussionMessage extends BaseWebSocketMessage {

    private Long discussionId;
    private String content;
}
