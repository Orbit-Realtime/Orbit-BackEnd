package com.chat.service;

import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.entity.Member;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.SpaceMemberRepository;
import com.chat.service.dtos.chat.UpdateChatRoom;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class BroadcastDataBuilderTest {

    @Autowired
    private BroadcastDataBuilder broadcastDataBuilder;
    @Autowired
    private SpaceMemberRepository spaceMemberRepository;
    @Autowired
    private TestDataFixture fixture;
    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("참여자가 없는 Space는 빈 Map을 반환한다.")
    void 참여자가_없는_Space는_빈_Map을_반환한다() {
        // given
        Space chatRoom = fixture.savedSimpleChatRoom("title");

        // when
        Map<Long, UpdateChatRoom> result = broadcastDataBuilder.build(chatRoom.getId());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("메시지가 없는 Space는 lastMessage와 createdDate가 null이다.")
    void 메시지가_없는_Space는_lastMessage와_createdDate가_null이다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        Space chatRoom = fixture.savedChatRoomBy("title", List.of(me, other));

        // when
        Map<Long, UpdateChatRoom> result = broadcastDataBuilder.build(chatRoom.getId());

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(me.getId()).getLastMessage()).isNull();
        assertThat(result.get(me.getId()).getCreatedDate()).isNull();
    }

    @Test
    @DisplayName("멤버별 unreadCount가 정확히 계산된다.")
    void 멤버별_unreadCount가_정확히_계산된다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        Member sender = fixture.savedMemberBy("sender");
        Space chatRoom = fixture.savedChatRoomBy("title", List.of(me, other, sender));

        Message first = fixture.savedSimpleChat("msg1", sender, chatRoom);
        fixture.savedSimpleChat("msg2", sender, chatRoom);

        // me: cursor null → 2개 unread
        // other: cursor = first → second만 1개 unread
        spaceMemberRepository.updateLastReadMessageId(
                other.getId(), chatRoom.getId(), first.getId());
        em.flush(); em.clear();

        // when
        Map<Long, UpdateChatRoom> result = broadcastDataBuilder.build(chatRoom.getId());

        // then
        assertThat(result.get(me.getId()).getUnreadMessageCount()).isEqualTo(2L);
        assertThat(result.get(other.getId()).getUnreadMessageCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("타겟 멤버가 없으면 빈 Map을 반환한다.")
    void 타겟_멤버가_없으면_빈_Map을_반환한다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Space chatRoom = fixture.savedChatRoomBy("title", List.of(me));

        // when
        Map<Long, UpdateChatRoom> result = broadcastDataBuilder.build(chatRoom.getId(), Set.of());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("타겟 멤버만 Map에 포함된다.")
    void 타겟_멤버만_Map에_포함된다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        Space chatRoom = fixture.savedChatRoomBy("title", List.of(me, other));

        // when: me만 타겟
        Map<Long, UpdateChatRoom> result = broadcastDataBuilder.build(chatRoom.getId(), Set.of(me.getId()));

        // then
        assertThat(result).hasSize(1);
        assertThat(result).containsKey(me.getId());
        assertThat(result).doesNotContainKey(other.getId());
    }

    @Test
    @DisplayName("타겟 멤버의 unreadCount가 정확히 계산된다.")
    void 타겟_멤버의_unreadCount가_정확히_계산된다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member sender = fixture.savedMemberBy("sender");
        Space chatRoom = fixture.savedChatRoomBy("title", List.of(me, sender));

        fixture.savedSimpleChat("msg", sender, chatRoom);
        // me: cursor null → 1개 unread

        // when: me만 타겟
        Map<Long, UpdateChatRoom> result = broadcastDataBuilder.build(chatRoom.getId(), Set.of(me.getId()));

        // then
        assertThat(result.get(me.getId()).getUnreadMessageCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Space title과 마지막 메시지 정보가 정확히 담긴다.")
    void Space_title과_마지막_메시지_정보가_정확히_담긴다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        Space chatRoom = fixture.savedChatRoomBy("myTitle", List.of(me, other));

        fixture.savedSimpleChat("first message", other, chatRoom);
        fixture.savedSimpleChat("last message", other, chatRoom);

        // when
        Map<Long, UpdateChatRoom> result = broadcastDataBuilder.build(chatRoom.getId());

        // then
        UpdateChatRoom updateChatRoom = result.get(me.getId());
        assertThat(updateChatRoom.getTitle()).isEqualTo("myTitle");
        assertThat(updateChatRoom.getLastMessage()).isEqualTo("last message");
        assertThat(updateChatRoom.getCreatedDate()).isNotNull();
    }
}
