package com.chat.socket.event;

import com.chat.service.dtos.chat.DiscussionMessageEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PublishDiscussionMessageEvent {

    private final DiscussionMessageEvent payload;
    private final Long spaceId;
}
