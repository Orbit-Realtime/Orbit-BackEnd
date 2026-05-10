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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
@SpringBootTest
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
        discussionMessageService.saveDiscussionMessage(discussion.getId(), member.getId(), "첫 번째 답글");

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
    @DisplayName("Space 참여자는 DiscussionMessage를 저장할 수 있다")
    void Space_참여자는_DiscussionMessage를_저장할_수_있다() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space space = fixture.savedChatRoomBy("space", List.of(member));
        Message message = fixture.savedSimpleChat("내용", member, space);
        Discussion discussion = discussionRepository.save(Discussion.of(message));

        // when
        DiscussionMessageResponse response =
                discussionMessageService.saveDiscussionMessage(discussion.getId(), member.getId(), "답글입니다");

        // then
        assertThat(response.getDiscussionMessageId()).isNotNull();
        assertThat(response.getContent()).isEqualTo("답글입니다");
    }

    @Test
    @DisplayName("빈 content로 DiscussionMessage 저장 시 EMPTY_DISCUSSION_MESSAGE_CONTENT가 발생한다")
    void 빈_content로_DiscussionMessage_저장_시_EMPTY_DISCUSSION_MESSAGE_CONTENT가_발생한다() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space space = fixture.savedChatRoomBy("space", List.of(member));
        Message message = fixture.savedSimpleChat("내용", member, space);
        Discussion discussion = discussionRepository.save(Discussion.of(message));

        // when / then
        assertThatThrownBy(() ->
                discussionMessageService.saveDiscussionMessage(discussion.getId(), member.getId(), "   "))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.EMPTY_DISCUSSION_MESSAGE_CONTENT));
    }

    @Test
    @DisplayName("DiscussionMessage 목록은 id 오름차순으로 반환된다")
    void DiscussionMessage_목록은_id_오름차순으로_반환된다() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space space = fixture.savedChatRoomBy("space", List.of(member));
        Message message = fixture.savedSimpleChat("내용", member, space);
        Discussion discussion = discussionRepository.save(Discussion.of(message));

        discussionMessageService.saveDiscussionMessage(discussion.getId(), member.getId(), "첫 번째");
        discussionMessageService.saveDiscussionMessage(discussion.getId(), member.getId(), "두 번째");
        discussionMessageService.saveDiscussionMessage(discussion.getId(), member.getId(), "세 번째");

        // when
        List<DiscussionMessageResponse> responses =
                discussionMessageService.findDiscussionMessages(discussion.getId(), member.getId());

        // then
        assertThat(responses).hasSize(3);
        assertThat(responses).extracting(DiscussionMessageResponse::getContent)
                .containsExactly("첫 번째", "두 번째", "세 번째");
    }

    @Test
    @DisplayName("저장된 DiscussionMessageResponse에 sender 정보와 discussionId가 올바르게 담긴다")
    void 저장된_DiscussionMessageResponse에_sender_정보와_discussionId가_올바르게_담긴다() {
        // given
        Member member = fixture.savedMemberBy("member");
        Space space = fixture.savedChatRoomBy("space", List.of(member));
        Message message = fixture.savedSimpleChat("내용", member, space);
        Discussion discussion = discussionRepository.save(Discussion.of(message));

        // when
        DiscussionMessageResponse response =
                discussionMessageService.saveDiscussionMessage(discussion.getId(), member.getId(), "답글");

        // then
        assertThat(response.getDiscussionMessageId()).isNotNull();
        assertThat(response.getDiscussionId()).isEqualTo(discussion.getId());
        assertThat(response.getSenderId()).isEqualTo(member.getId());
        assertThat(response.getSenderNickname()).isEqualTo(member.getNickname());
        assertThat(response.getContent()).isEqualTo("답글");
        assertThat(response.getCreatedDate()).isNotNull();
    }
}
