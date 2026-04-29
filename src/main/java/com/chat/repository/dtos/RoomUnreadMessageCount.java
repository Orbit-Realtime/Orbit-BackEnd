package com.chat.repository.dtos;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RoomUnreadMessageCount {

    private Long chatRoomId;
    private Long unreadMessageCount;

    public RoomUnreadMessageCount(Long chatRoomId, Long unreadMessageCount) {
        this.chatRoomId = chatRoomId;
        this.unreadMessageCount = unreadMessageCount;
    }
}
