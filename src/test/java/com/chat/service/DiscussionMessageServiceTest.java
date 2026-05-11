package com.chat.service;

import com.chat.api.response.discussion.DiscussionMessageResponse;
import com.chat.entity.Discussion;
import com.chat.entity.Member;
import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.DiscussionRepository;
import com.chat.service.dtos.chat.DiscussionMessageEvent;
import com.chat.socket.event.PublishDiscussionMessageEvent;
import com.chat.utils.message.MessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@SpringBootTest
@RecordApplicationEvents
class DiscussionMessageServiceTest {

    @Autowired private DiscussionMessageService discussionMessageService;
    @Autowired private DiscussionRepository discussionRepository;
    @Autowired private TestDataFixture fixture;

    @Test
    @DisplayName("Space 참여자는 DiscussionMessage 목록을 조회할 수 있다")
    void Space_참여자는_DiscussionMessage_목록을_조회할_수_있다() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space space = fixture.savedChatRoomBy("space", List.of(member));
        Message message = fixture.savedSimpleChat("내용", member, space);
        Discussion discussion = discussionRepository.save(Discussion.of(message));
        discussionMessageService.broadcastDiscussionMessage(discussion.getId(), member.getId(), "첫 번째 답글");

        // when
        List<DiscussionMessageResponse> responses =
                discussionMessageService.findDiscussionMessages(discussion.getId(), member.getId());

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getContent()).isEqualTo("첫 번째 답글");
    }

    @Test
    @DisplayName("Space 미참여자는 DiscussionMessage 목록 조회가 거부된다")
    void Space_미참여자는_DiscussionMessage_목록_조회가_거부된다() {
        // given
        Member participant = fixture.savedMemberBy("participant");
        Member outsider = fixture.savedMemberBy("outsider");
        Space space = fixture.savedChatRoomBy("space", List.of(participant));
        Message message = fixture.savedSimpleChat("내용", participant, space);
        Discussion discussion = discussionRepository.save(Discussion.of(message));

        // when / then
        assertThatThrownBy(() ->
                discussionMessageService.findDiscussionMessages(discussion.getId(), outsider.getId()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SPACE_ACCESS_DENIED));
    }

    @Test
    @DisplayName("존재하지 않는 discussionId 조회 시 DISCUSSION_NOT_FOUND가 발생한다")
    void 존재하지_않는_discussionId_조회_시_DISCUSSION_NOT_FOUND가_발생한다() {
        // given
        Member member = fixture.savedMemberBy("member");
        Long notExistDiscussionId = Long.MAX_VALUE;

        // when / then
        assertThatThrownBy(() ->
                discussionMessageService.findDiscussionMessages(notExistDiscussionId, member.getId()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DISCUSSION_NOT_FOUND));
    }

    @Test
    @DisplayName("DiscussionMessage 목록은 id 오름차순으로 반환된다")
    void DiscussionMessage_목록은_id_오름차순으로_반환된다() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space space = fixture.savedChatRoomBy("space", List.of(member));
        Message message = fixture.savedSimpleChat("내용", member, space);
        Discussion discussion = discussionRepository.save(Discussion.of(message));

        discussionMessageService.broadcastDiscussionMessage(discussion.getId(), member.getId(), "첫 번째");
        discussionMessageService.broadcastDiscussionMessage(discussion.getId(), member.getId(), "두 번째");
        discussionMessageService.broadcastDiscussionMessage(discussion.getId(), member.getId(), "세 번째");

        // when
        List<DiscussionMessageResponse> responses =
                discussionMessageService.findDiscussionMessages(discussion.getId(), member.getId());

        // then
        assertThat(responses).hasSize(3);
        assertThat(responses).extracting(DiscussionMessageResponse::getContent)
                .containsExactly("첫 번째", "두 번째", "세 번째");
    }

    @Test
    @DisplayName("Space 미참여자가 broadcastDiscussionMessage 호출 시 SPACE_ACCESS_DENIED가 발생한다")
    void Space_미참여자가_broadcastDiscussionMessage_호출_시_SPACE_ACCESS_DENIED가_발생한다() {
        // given
        Member participant = fixture.savedMemberBy("participant");
        Member outsider = fixture.savedMemberBy("outsider");
        Space space = fixture.savedChatRoomBy("space", List.of(participant));
        Message message = fixture.savedSimpleChat("내용", participant, space);
        Discussion discussion = discussionRepository.save(Discussion.of(message));

        // when / then
        assertThatThrownBy(() ->
                discussionMessageService.broadcastDiscussionMessage(discussion.getId(), outsider.getId(), "답글"))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SPACE_ACCESS_DENIED));
    }

    @Test
    @DisplayName("blank content로 broadcastDiscussionMessage 호출 시 EMPTY_DISCUSSION_MESSAGE_CONTENT가 발생한다")
    void blank_content로_broadcastDiscussionMessage_호출_시_EMPTY_DISCUSSION_MESSAGE_CONTENT가_발생한다() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space space = fixture.savedChatRoomBy("space", List.of(member));
        Message message = fixture.savedSimpleChat("내용", member, space);
        Discussion discussion = discussionRepository.save(Discussion.of(message));

        // when / then
        assertThatThrownBy(() ->
                discussionMessageService.broadcastDiscussionMessage(discussion.getId(), member.getId(), "   "))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EMPTY_DISCUSSION_MESSAGE_CONTENT));
    }

    @Test
    @DisplayName("broadcastDiscussionMessage 저장 후 PublishDiscussionMessageEvent payload에 DISCUSSION_MESSAGE_EVENT 타입과 전체 필드가 담긴다")
    void broadcastDiscussionMessage_저장_후_PublishDiscussionMessageEvent_payload에_전체_필드가_담긴다(ApplicationEvents events) {
        // given
        Member member = fixture.savedMemberBy("member");
        Space space = fixture.savedChatRoomBy("space", List.of(member));
        Message message = fixture.savedSimpleChat("내용", member, space);
        Discussion discussion = discussionRepository.save(Discussion.of(message));

        // when
        discussionMessageService.broadcastDiscussionMessage(discussion.getId(), member.getId(), "답글");

        // then: 이벤트 1건 발행
        List<PublishDiscussionMessageEvent> published =
                events.stream(PublishDiscussionMessageEvent.class).toList();
        assertThat(published).hasSize(1);

        // spaceId = rootMessage의 Space ID
        PublishDiscussionMessageEvent publishedEvent = published.get(0);
        assertThat(publishedEvent.getSpaceId()).isEqualTo(space.getId());

        // payload 전체 필드 검증
        DiscussionMessageEvent payload = publishedEvent.getPayload();
        assertThat(payload.getMessageType()).isEqualTo(MessageType.DISCUSSION_MESSAGE_EVENT);
        assertThat(payload.getDiscussionId()).isEqualTo(discussion.getId());
        assertThat(payload.getSenderId()).isEqualTo(member.getId());
        assertThat(payload.getSenderNickname()).isEqualTo(member.getNickname());
        assertThat(payload.getContent()).isEqualTo("답글");
        assertThat(payload.getDiscussionMessageId()).isNotNull();
        assertThat(payload.getCreatedDate()).isNotNull();
    }
}
