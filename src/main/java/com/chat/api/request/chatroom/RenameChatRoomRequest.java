package com.chat.api.request.chatroom;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RenameChatRoomRequest {
    private String title;
}
