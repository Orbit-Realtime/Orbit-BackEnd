package com.chat.api;

import com.chat.service.dtos.ChatHistoryResponse;
import com.chat.utils.consts.SessionConst;
import com.chat.service.MessageService;
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

    private final MessageService messageService;

    @GetMapping("/api/chats")
    public Result<ChatHistoryResponse> chatHistory(@RequestParam("chatRoomId") Long chatRoomId,
                                                 @RequestParam(value = "beforeChatId", required = false) Long beforeChatId,
                                                 @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        // 채팅 내역 조회
        ChatHistoryResponse chatHistory = messageService.findMessageHistory(chatRoomId, loginMemberId, beforeChatId);

        return Result
                .<ChatHistoryResponse>builder()
                .data(chatHistory)
                .status(HttpStatus.OK)
                .message("채팅 메시지 조회에 성공했습니다.")
                .build();
    }
}
