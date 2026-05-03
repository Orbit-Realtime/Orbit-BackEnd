package com.chat.utils.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageType {

    CHAT_MESSAGE("채팅 메시지"),
    CHAT_ENTER("채팅방 접속"),
    ENTER_ROOM("채팅방 입장 요청"),
    UPDATE_CHAT_ROOM("채팅방 목록 갱신"),
    READ_EVENT("읽음 이벤트"),
    ROOM_ACTIVE("방 활성화"),
    ROOM_INACTIVE("방 비활성화"),
    ERROR("에러 응답"),
    ;

    private final String description;
}
