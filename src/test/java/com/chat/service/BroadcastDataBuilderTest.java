package com.chat.service;

import com.chat.entity.Chat;
import com.chat.entity.Space;
import com.chat.entity.Member;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.ChatRoomParticipantRepository;
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
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    @Autowired
    private TestDataFixture fixture;
    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("채팅방에 참여 중인 멤버가 없으면 빈 Map을 반환한다.")
    void build_noMembers_returnsEmptyMap() {
        // given
        Space chatRoom = fixture.savedSimpleChatRoom("title");

        // when
        Map<Long, UpdateChatRoom> result = broadcastDataBuilder.build(chatRoom.getId());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("채팅 메시지가 없는 방은 lastMessage와 createdDate가 null이다.")
    void build_noMessages_returnsNullLastMessageAndCreatedDate() {
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
    @DisplayName("멤버별 읽지 않은 메시지 수가 정확히 담긴다.")
    void build_returnsCorrectUnreadCountPerMember() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        Member sender = fixture.savedMemberBy("sender");
        Space chatRoom = fixture.savedChatRoomBy("title", List.of(me, other, sender));

        Chat first = fixture.savedSimpleChat("msg1", sender, chatRoom);
        fixture.savedSimpleChat("msg2", sender, chatRoom);

        // me: cursor null → 2개 unread
        // other: cursor = first → second만 1개 unread
        chatRoomParticipantRepository.updateLastReadChatId(
                other.getId(), chatRoom.getId(), first.getId());
        em.flush(); em.clear();

        // when
        Map<Long, UpdateChatRoom> result = broadcastDataBuilder.build(chatRoom.getId());

        // then
        assertThat(result.get(me.getId()).getUnreadMessageCount()).isEqualTo(2L);
        assertThat(result.get(other.getId()).getUnreadMessageCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("targetMemberIds가 빈 Set이면 빈 Map을 반환한다.")
    void buildWithTarget_emptyTargetIds_returnsEmptyMap() {
        // given
        Member me = fixture.savedMemberBy("me");
        Space chatRoom = fixture.savedChatRoomBy("title", List.of(me));

        // when
        Map<Long, UpdateChatRoom> result = broadcastDataBuilder.build(chatRoom.getId(), Set.of());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("targetMemberIds에 포함된 멤버만 Map에 담긴다.")
    void buildWithTarget_onlyTargetMembersIncluded() {
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
    @DisplayName("targetMemberIds 멤버의 unreadMessageCount가 정확히 담긴다.")
    void buildWithTarget_returnsCorrectUnreadCount() {
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
    @DisplayName("채팅방 title과 마지막 메시지가 정확히 담긴다.")
    void build_returnsTitleAndLastMessage() {
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
