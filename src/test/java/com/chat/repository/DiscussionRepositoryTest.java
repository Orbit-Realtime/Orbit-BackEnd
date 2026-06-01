package com.chat.repository;

import com.chat.entity.Discussion;
import com.chat.entity.DiscussionMessage;
import com.chat.entity.Member;
import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.repository.dtos.DiscussionSummary;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class DiscussionRepositoryTest {

    @Autowired private DiscussionRepository discussionRepository;
    @Autowired private DiscussionMessageRepository discussionMessageRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private SpaceRepository spaceRepository;
    @Autowired private EntityManager em;

    @Test
    @DisplayName("rootMessageId로 Discussion을 DB에서 조회할 수 있다.")
    void rootMessageId로_Discussion을_DB에서_조회할_수_있다() {
        // given
        Member member = createMember("user");
        Space space = createSpace("room");
        Message message = createMessage("hello", member, space);

        Discussion saved = discussionRepository.save(Discussion.of(message));
        em.flush();
        em.clear();

        // when
        Optional<Discussion> found = discussionRepository.findByRootMessageId(message.getId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    @DisplayName("동일한 Message에 Discussion을 두 번 생성하면 DataIntegrityViolationException이 발생한다.")
    void 동일한_Message에_Discussion을_두_번_생성하면_DataIntegrityViolationException이_발생한다() {
        // given
        Member member = createMember("user2");
        Space space = createSpace("room2");
        Message message = createMessage("hello", member, space);

        discussionRepository.saveAndFlush(Discussion.of(message));

        // when / then
        assertThatThrownBy(() -> discussionRepository.saveAndFlush(Discussion.of(message)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── findDiscussionSummariesByRootMessageIds ──────────────────────────────

    @Test
    @DisplayName("Discussion이 있는 Message는 discussionId와 DiscussionMessage 개수를 반환한다.")
    void Discussion이_있는_Message는_discussionId와_DiscussionMessage_개수를_반환한다() {
        // given
        Member member = createMember("user");
        Space space = createSpace("room");
        Message message = createMessage("원글", member, space);
        Discussion discussion = createDiscussion(message);

        createDiscussionMessage("답글1", discussion, member);
        createDiscussionMessage("답글2", discussion, member);

        em.flush();
        em.clear();

        // when
        List<DiscussionSummary> result =
                discussionRepository.findDiscussionSummariesByRootMessageIds(
                        List.of(message.getId()));

        // then
        assertThat(result).hasSize(1);
        DiscussionSummary summary = result.get(0);
        assertThat(summary.getMessageId()).isEqualTo(message.getId());
        assertThat(summary.getDiscussionId()).isEqualTo(discussion.getId());
        assertThat(summary.getDiscussionMessageCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Discussion이 없는 Message는 결과에서 제외된다.")
    void Discussion이_없는_Message는_결과에서_제외된다() {
        // given
        Member member = createMember("user");
        Space space = createSpace("room");
        Message messageWithDiscussion = createMessage("Discussion 있음", member, space);
        Message messageWithoutDiscussion = createMessage("Discussion 없음", member, space);

        createDiscussion(messageWithDiscussion);

        em.flush();
        em.clear();

        // when
        List<DiscussionSummary> result =
                discussionRepository.findDiscussionSummariesByRootMessageIds(
                        List.of(messageWithDiscussion.getId(), messageWithoutDiscussion.getId()));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMessageId()).isEqualTo(messageWithDiscussion.getId());
    }

    @Test
    @DisplayName("DiscussionMessage가 없는 Discussion은 개수가 0으로 집계된다.")
    void DiscussionMessage가_없는_Discussion은_개수가_0으로_집계된다() {
        // given
        Member member = createMember("user");
        Space space = createSpace("room");
        Message message = createMessage("원글", member, space);
        createDiscussion(message);

        em.flush();
        em.clear();

        // when
        List<DiscussionSummary> result =
                discussionRepository.findDiscussionSummariesByRootMessageIds(
                        List.of(message.getId()));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDiscussionMessageCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("여러 Message를 한 번에 조회하면 메시지별 DiscussionMessage 개수가 정확히 집계된다.")
    void 여러_Message를_한_번에_조회하면_메시지별_DiscussionMessage_개수가_정확히_집계된다() {
        // given
        Member member = createMember("user");
        Space space = createSpace("room");

        Message message1 = createMessage("원글1", member, space);
        Discussion discussion1 = createDiscussion(message1);
        createDiscussionMessage("답글1-1", discussion1, member);
        createDiscussionMessage("답글1-2", discussion1, member);

        Message message2 = createMessage("원글2", member, space);
        Discussion discussion2 = createDiscussion(message2);
        createDiscussionMessage("답글2-1", discussion2, member);
        createDiscussionMessage("답글2-2", discussion2, member);
        createDiscussionMessage("답글2-3", discussion2, member);

        em.flush();
        em.clear();

        // when
        List<DiscussionSummary> result =
                discussionRepository.findDiscussionSummariesByRootMessageIds(
                        List.of(message1.getId(), message2.getId()));

        // then
        assertThat(result).hasSize(2);

        Map<Long, DiscussionSummary> resultMap = result.stream()
                .collect(Collectors.toMap(DiscussionSummary::getMessageId, ds -> ds));

        assertThat(resultMap.get(message1.getId()).getDiscussionMessageCount()).isEqualTo(2L);
        assertThat(resultMap.get(message2.getId()).getDiscussionMessageCount()).isEqualTo(3L);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Member createMember(String username) {
        return memberRepository.save(Member.of(username, "password", username));
    }

    private Space createSpace(String title) {
        return spaceRepository.save(Space.of(title));
    }

    private Message createMessage(String content, Member member, Space space) {
        return messageRepository.save(Message.of(content, member, space));
    }

    private Discussion createDiscussion(Message message) {
        return discussionRepository.save(Discussion.of(message));
    }

    private DiscussionMessage createDiscussionMessage(
            String content, Discussion discussion, Member member) {
        return discussionMessageRepository.save(
                DiscussionMessage.of(content, discussion, member));
    }
}
