package com.chat.api;

import com.chat.service.ChatRoomService;
import com.chat.service.dtos.ChatHistoryResponse;
import com.chat.utils.consts.SessionConst;
import com.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatApiController {

    private final ChatService chatService;
    private final ChatRoomService chatRoomService;

    @GetMapping("/api/chats")
    public Result<ChatHistoryResponse> chatHistory(@RequestParam("chatRoomId") Long chatRoomId,
                                                 @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        // 채팅 내역 조회
        ChatHistoryResponse chatHistory = chatService.findChatHistory(chatRoomId, loginMemberId);

        if (chatHistory.getUpdatedCount() > 0) {
            chatRoomService.broadcastAfterRead(loginMemberId, chatRoomId, chatHistory.getLastReadChatId());
        }

        return Result
                .<ChatHistoryResponse>builder()
                .data(chatHistory)
                .status(HttpStatus.OK)
                .message("채팅 메시지 조회에 성공했습니다.")
                .build();
    }
}
