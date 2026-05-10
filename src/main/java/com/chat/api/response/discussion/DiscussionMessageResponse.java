package com.chat.api.response.discussion;

import com.chat.entity.DiscussionMessage;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class DiscussionMessageResponse {

    private Long discussionMessageId;
    private Long discussionId;
    private Long senderId;
    private String senderNickname;
    private String content;
    private LocalDateTime createdDate;

    @Builder
    public DiscussionMessageResponse(Long discussionMessageId, Long discussionId,
                                     Long senderId, String senderNickname,
                                     String content, LocalDateTime createdDate) {
        this.discussionMessageId = discussionMessageId;
        this.discussionId = discussionId;
        this.senderId = senderId;
        this.senderNickname = senderNickname;
        this.content = content;
        this.createdDate = createdDate;
    }

    public static DiscussionMessageResponse from(DiscussionMessage dm) {
        return DiscussionMessageResponse.builder()
                .discussionMessageId(dm.getId())
                .discussionId(dm.getDiscussion().getId())
                .senderId(dm.getMember().getId())
                .senderNickname(dm.getMember().getNickname())
                .content(dm.getContent())
                .createdDate(dm.getCreatedDate())
                .build();
    }
}
