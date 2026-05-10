package com.chat.api;

import com.chat.api.response.discussion.DiscussionMessageResponse;
import com.chat.service.DiscussionMessageService;
import com.chat.utils.consts.SessionConst;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DiscussionMessageApiController {

    private final DiscussionMessageService discussionMessageService;

    @GetMapping("/api/discussions/{discussionId}/messages")
    public Result<List<DiscussionMessageResponse>> getDiscussionMessages(@PathVariable Long discussionId,
                                                               @SessionAttribute(name = SessionConst.SESSION_ID) Long loginMemberId) {

        List<DiscussionMessageResponse> messages =
                discussionMessageService.findDiscussionMessages(discussionId, loginMemberId);

        return Result.<List<DiscussionMessageResponse>>builder()
                .data(messages)
                .status(HttpStatus.OK)
                .message("Discussion 메시지 목록 조회에 성공했습니다.")
                .build();
    }
}
