package com.chat.repository;

import com.chat.entity.Discussion;
import com.chat.entity.DiscussionMessage;
import com.chat.entity.Member;
import com.chat.entity.Message;
import com.chat.entity.Space;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class DiscussionMessageRepositoryTest {

    @Autowired private DiscussionMessageRepository discussionMessageRepository;
    @Autowired private DiscussionRepository discussionRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private SpaceRepository spaceRepository;

    @Test
    @DisplayName("Discussion 없이 DiscussionMessage를 저장할 수 없다")
    void discussionNullConstraint() {
        // given
        Member member = createMember("user");

        // when / then
        assertThatThrownBy(() ->
                discussionMessageRepository.saveAndFlush(DiscussionMessage.of("content", null, member)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Member 없이 DiscussionMessage를 저장할 수 없다")
    void memberNullConstraint() {
        // given
        Member member = createMember("user2");
        Space space = createSpace("room");
        Message message = createMessage("hello", member, space);
        Discussion discussion = discussionRepository.save(Discussion.of(message));

        // when / then
        assertThatThrownBy(() ->
                discussionMessageRepository.saveAndFlush(DiscussionMessage.of("content", discussion, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("content 없이 DiscussionMessage를 저장할 수 없다")
    void contentNullConstraint() {
        // given
        Member member = createMember("user3");
        Space space = createSpace("room2");
        Message message = createMessage("hello", member, space);
        Discussion discussion = discussionRepository.save(Discussion.of(message));

        // when / then
        assertThatThrownBy(() ->
                discussionMessageRepository.saveAndFlush(DiscussionMessage.of(null, discussion, member)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Member createMember(String username) {
        return memberRepository.save(Member.of(username, "password", username));
    }

    private Space createSpace(String title) {
        return spaceRepository.save(Space.of(title));
    }

    private Message createMessage(String content, Member member, Space space) {
        return messageRepository.save(new Message(content, member, space));
    }
}
