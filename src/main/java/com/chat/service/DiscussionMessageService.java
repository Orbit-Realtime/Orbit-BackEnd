package com.chat.service;

import com.chat.api.response.discussion.DiscussionMessageResponse;
import com.chat.entity.Discussion;
import com.chat.entity.DiscussionMessage;
import com.chat.entity.Member;
import com.chat.entity.SpaceMember;
import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.repository.DiscussionMessageRepository;
import com.chat.repository.DiscussionRepository;
import com.chat.repository.MemberRepository;
import com.chat.repository.SpaceMemberRepository;
import com.chat.service.dtos.chat.DiscussionMessageEvent;
import com.chat.socket.event.PublishDiscussionMessageEvent;
import com.chat.utils.message.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiscussionMessageService {

    private final ApplicationEventPublisher publisher;
    private final DiscussionRepository discussionRepository;
    private final DiscussionMessageRepository discussionMessageRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final MemberRepository memberRepository;

    public List<DiscussionMessageResponse> findDiscussionMessages(Long discussionId, Long memberId) {
        findAccessibleDiscussion(discussionId, memberId);

        List<DiscussionMessage> messages =
                discussionMessageRepository.findByDiscussionIdFetchMember(discussionId);

        return messages.stream()
                .map(dm -> DiscussionMessageResponse.from(dm))
                .toList();
    }

    @Transactional
    public void broadcastDiscussionMessage(Long discussionId, Long memberId, String content) {
        Discussion discussion = findAccessibleDiscussion(discussionId, memberId);
        Long spaceId = discussion.getRootMessage().getSpace().getId();

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        DiscussionMessage saved = discussionMessageRepository.save(
                DiscussionMessage.of(content, discussion, member));

        DiscussionMessageEvent event = DiscussionMessageEvent.builder()
                .messageType(MessageType.DISCUSSION_MESSAGE_EVENT)
                .chatId(discussion.getRootMessage().getId())
                .discussionMessageId(saved.getId())
                .discussionId(discussionId)
                .senderId(memberId)
                .senderNickname(member.getNickname())
                .content(saved.getContent())
                .createdDate(saved.getCreatedDate())
                .build();

        publisher.publishEvent(new PublishDiscussionMessageEvent(event, spaceId));
    }

    private Discussion findAccessibleDiscussion(Long discussionId, Long memberId) {
        Discussion discussion = discussionRepository.findByIdFetchRootMessageAndSpace(discussionId)
                .orElseThrow(() -> new CustomException(ErrorCode.DISCUSSION_NOT_FOUND));

        Long spaceId = discussion.getRootMessage().getSpace().getId();
        SpaceMember spaceMember = spaceMemberRepository.findChatRoomBy(spaceId, memberId);

        if (spaceMember == null) {
            throw new CustomException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        return discussion;
    }
}
