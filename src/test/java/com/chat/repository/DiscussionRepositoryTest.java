package com.chat.repository;

import com.chat.entity.Discussion;
import com.chat.entity.Member;
import com.chat.entity.Message;
import com.chat.entity.Space;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class DiscussionRepositoryTest {

    @Autowired private DiscussionRepository discussionRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private SpaceRepository spaceRepository;
    @Autowired private EntityManager em;

    @Test
    @DisplayName("rootMessageId로 Discussion을 조회할 수 있다")
    void findByRootMessageId_roundTrip() {
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
    @DisplayName("하나의 Message에는 Discussion을 하나만 생성할 수 있다")
    void uniqueRootMessage() {
        // given
        Member member = createMember("user2");
        Space space = createSpace("room2");
        Message message = createMessage("hello", member, space);

        discussionRepository.saveAndFlush(Discussion.of(message));

        // when / then
        assertThatThrownBy(() -> discussionRepository.saveAndFlush(Discussion.of(message)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("rootMessage 없이 Discussion을 저장할 수 없다")
    void rootMessageNullConstraint() {
        // when / then
        assertThatThrownBy(() -> discussionRepository.saveAndFlush(Discussion.of(null)))
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
