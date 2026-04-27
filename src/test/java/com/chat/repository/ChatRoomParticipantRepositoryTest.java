package com.chat.repository;

import com.chat.entity.Chat;
import com.chat.entity.ChatRoom;
import com.chat.entity.ChatRoomParticipant;
import com.chat.entity.Member;
import com.chat.repository.dtos.ChatRoomUnreadCount;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class ChatRoomParticipantRepositoryTest {

    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("채팅방 참여 정보를 저장한다.")
    void saveTest() {
        // given
        String username = "username";
        Member savedMember = createMemberBy(username);

        String title = "title";
        ChatRoom chatRoom = ChatRoom.of(title);
        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);
        ChatRoomParticipant chatRoomParticipant = ChatRoomParticipant.builder().member(savedMember).chatRoom(savedChatRoom).build();

        // when
        ChatRoomParticipant savedChatRoomParticipant = chatRoomParticipantRepository.save(chatRoomParticipant);

        // then
        assertThat(savedChatRoomParticipant.getId()).isNotNull();
        assertThat(savedChatRoomParticipant.getMember()).isEqualTo(savedMember);
        assertThat(savedChatRoomParticipant.getChatRoom()).isEqualTo(savedChatRoom);
    }

    @Test
    @DisplayName("사용자 ID 들로 구성된 채팅방이 존재하는지 확인한다.")
    void countByExactMembersTest() {
        // given
        List<Long> memberIds = new ArrayList<>();
        String firstUsername = "first";
        Member firstMember = createMemberBy(firstUsername);
        memberIds.add(firstMember.getId());

        String secondUsername = "second";
        Member secondMember = createMemberBy(secondUsername);
        memberIds.add(secondMember.getId());

        String thirdUsername = "third";
        Member thirdMember = createMemberBy(thirdUsername);
        memberIds.add(thirdMember.getId());

        String title = "title";
        ChatRoom chatRoom = createChatRoomBy(title);

        chatRoomParticipantRepository.save(ChatRoomParticipant.builder().member(firstMember).chatRoom(chatRoom).build());
        chatRoomParticipantRepository.save(ChatRoomParticipant.builder().member(secondMember).chatRoom(chatRoom).build());
        chatRoomParticipantRepository.save(ChatRoomParticipant.builder().member(thirdMember).chatRoom(chatRoom).build());

        // when
        List<Long> chatRoomIds = chatRoomParticipantRepository.findChatRoomIdsByExactMembers(memberIds, memberIds.size());

        // then
        assertThat(chatRoomIds).hasSize(1);
    }

    @Test
    @DisplayName("일부 사용자만 포함된 경우 채팅방이 조회되지 않는다.")
    void partialMemberChatRoomTest() {
        // given
        List<Long> memberIds = new ArrayList<>();
        String firstUsername = "first";
        Member firstMember = createMemberBy(firstUsername);
        memberIds.add(firstMember.getId());

        String secondUsername = "second";
        Member secondMember = createMemberBy(secondUsername);
        memberIds.add(secondMember.getId());

        String thirdUsername = "third";
        Member thirdMember = createMemberBy(thirdUsername);

        String title = "title";
        ChatRoom chatRoom = createChatRoomBy(title);

        chatRoomParticipantRepository.save(ChatRoomParticipant.builder().member(firstMember).chatRoom(chatRoom).build());
        chatRoomParticipantRepository.save(ChatRoomParticipant.builder().member(secondMember).chatRoom(chatRoom).build());
        chatRoomParticipantRepository.save(ChatRoomParticipant.builder().member(thirdMember).chatRoom(chatRoom).build());

        // when
        List<Long> chatRoomIds = chatRoomParticipantRepository.findChatRoomIdsByExactMembers(memberIds, memberIds.size());

        // then
        assertThat(chatRoomIds).hasSize(0);
    }

    @Test
    @DisplayName("채팅방 사용자보다 많은 ID 가 포함된 경우 채팅방이 조회되지 않는다.")
    void memberChatRoomTest() {
        // given
        List<Long> memberIds = new ArrayList<>();
        String firstUsername = "first";
        Member firstMember = createMemberBy(firstUsername);
        memberIds.add(firstMember.getId());

        String secondUsername = "second";
        Member secondMember = createMemberBy(secondUsername);
        memberIds.add(secondMember.getId());

        String thirdUsername = "third";
        Member thirdMember = createMemberBy(thirdUsername);
        memberIds.add(thirdMember.getId());

        String title = "title";
        ChatRoom chatRoom = createChatRoomBy(title);

        chatRoomParticipantRepository.save(ChatRoomParticipant.builder().member(firstMember).chatRoom(chatRoom).build());
        chatRoomParticipantRepository.save(ChatRoomParticipant.builder().member(secondMember).chatRoom(chatRoom).build());

        // when
        List<Long> chatRoomIds = chatRoomParticipantRepository.findChatRoomIdsByExactMembers(memberIds, memberIds.size());

        // then
        assertThat(chatRoomIds).hasSize(0);
    }

    @Test
    @DisplayName("사용자 ID 로 채팅방 참여 정보를 조회한다.")
    void findAllByMemberIdTest() {
        // given
        String username = "username";
        Member member = createMemberBy(username);

        String firstTitle = "first";
        ChatRoom first = createChatRoomBy(firstTitle);
        String secondTitle = "secondTitle";
        ChatRoom second = createChatRoomBy(secondTitle);

        ChatRoomParticipant firstParticipant = chatRoomParticipantRepository.save(ChatRoomParticipant.builder().member(member).chatRoom(first).build());
        ChatRoomParticipant secondParticipant = chatRoomParticipantRepository.save(ChatRoomParticipant.builder().member(member).chatRoom(second).build());

        // when
        List<ChatRoomParticipant> findChatRoomParticipants = chatRoomParticipantRepository.findAllFetchChatRoomBy(member.getId());

        // then
        assertThat(findChatRoomParticipants).hasSize(2);
        assertThat(findChatRoomParticipants).containsExactly(firstParticipant, secondParticipant);
    }

    @Test
    @DisplayName("채팅방 ID 를 이용해 채팅방 참여, 회원 정보를 조회한다.")
    void findAllFetchMemberByTest() {
        // given
        String username = "username";
        Member member = createMemberBy(username);
        String title = "chatRoom";
        ChatRoom chatRoom = createChatRoomBy(title);
        chatRoomParticipantRepository.save(ChatRoomParticipant.builder().member(member).chatRoom(chatRoom).build());

        // when
        List<ChatRoomParticipant> chatRoomParticipants
                = chatRoomParticipantRepository.findAllFetchMemberBy(chatRoom.getId());

        // then
        assertThat(chatRoomParticipants).hasSize(1);
    }

    @Test
    @DisplayName("채팅방 참여 조회 시 사용자 정보를 fetch 해 쿼리가 1번만 실행된다.")
    void shouldFetchMembersWithParticipantsUsingSingleQuery() {
        // given
        String username = "username";
        Member member = createMemberBy(username);
        String title = "chatRoom";
        ChatRoom chatRoom = createChatRoomBy(title);
        chatRoomParticipantRepository.save(ChatRoomParticipant.builder().member(member).chatRoom(chatRoom).build());

        em.flush();
        em.clear();

        Session session = em.unwrap(Session.class);
        session.getSessionFactory().getStatistics().setStatisticsEnabled(true);
        session.getSessionFactory().getStatistics().clear();

        // when
        List<ChatRoomParticipant> chatRoomParticipants
                = chatRoomParticipantRepository.findAllFetchMemberBy(chatRoom.getId());
        for (ChatRoomParticipant chatRoomParticipant : chatRoomParticipants) {
            chatRoomParticipant.getMember().getUsername();
        }
        long queryCount = session.getSessionFactory().getStatistics().getPrepareStatementCount();

        // then
        assertThat(queryCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("채팅방 ID 와 사용자 ID 를 이용해 채팅방 참여 데이터를 조회한다.")
    void findChatRoomByChatRoomIdAndMemberIdTest() {
        // given
        String username = "username";
        Member member = createMemberBy(username);
        String title = "chatRoom";
        ChatRoom chatRoom = createChatRoomBy(title);
        chatRoomParticipantRepository.save(ChatRoomParticipant.builder().member(member).chatRoom(chatRoom).build());

        // when
        ChatRoomParticipant chatRoomParticipant = chatRoomParticipantRepository.findChatRoomBy(chatRoom.getId(), member.getId());

        // then
        assertThat(chatRoomParticipant.getChatRoom()).isEqualTo(chatRoom);
        assertThat(chatRoomParticipant.getMember()).isEqualTo(member);
    }

    @Test
    @DisplayName("여러 채팅방 ID 로 참여자와 회원 정보를 일괄 조회한다.")
    void findAllFetchMemberByRoomIdsTest() {
        // given
        Member firstMember = createMemberBy("first");
        Member secondMember = createMemberBy("second");
        Member thirdMember = createMemberBy("third");

        ChatRoom firstRoom = createChatRoomBy("firstRoom");
        ChatRoom secondRoom = createChatRoomBy("secondRoom");

        chatRoomParticipantRepository.save(ChatRoomParticipant.builder().member(firstMember).chatRoom(firstRoom).build());
        chatRoomParticipantRepository.save(ChatRoomParticipant.builder().member(secondMember).chatRoom(firstRoom).build());
        chatRoomParticipantRepository.save(ChatRoomParticipant.builder().member(thirdMember).chatRoom(secondRoom).build());

        em.flush();
        em.clear();

        // when
        List<ChatRoomParticipant> participants = chatRoomParticipantRepository
                .findAllFetchMemberBy(List.of(firstRoom.getId(), secondRoom.getId()));

        // then
        assertThat(participants).hasSize(3);
        assertThat(participants)
                .extracting(crp -> crp.getMember().getNickname())
                .doesNotContainNull();
    }

    @Test
    @DisplayName("채팅방 ID 와 사용자 ID 로 채팅방 참여 데이터를 삭제한다.")
    void deleteByTest() {
        // given
        Member member = createMemberBy("username");
        ChatRoom chatRoom = createChatRoomBy("chatRoom");
        chatRoomParticipantRepository.save(ChatRoomParticipant.builder().member(member).chatRoom(chatRoom).build());

        // when
        chatRoomParticipantRepository.deleteBy(chatRoom.getId(), member.getId());
        em.flush();
        em.clear();

        // then
        ChatRoomParticipant result = chatRoomParticipantRepository.findChatRoomBy(chatRoom.getId(), member.getId());
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("채팅방 참여 생성 시 lastReadChatId 기본값은 null 이다.")
    void lastReadChatIdDefaultNullTest() {
        // given
        Member member = createMemberBy("member");
        ChatRoom chatRoom = createChatRoomBy("room");

        // when
        ChatRoomParticipant saved = chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(member).chatRoom(chatRoom).build()
        );

        // then
        assertThat(saved.getLastReadChatId()).isNull();
    }

    @Test
    @DisplayName("lastReadChatId 가 null 인 경우 특정 chatId 로 갱신된다.")
    void updateLastReadChatIdFromNullTest() {
        // given
        Member member = createMemberBy("member");
        ChatRoom chatRoom = createChatRoomBy("room");
        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(member).chatRoom(chatRoom).build()
        );
        em.flush();
        em.clear();

        int updated = chatRoomParticipantRepository.updateLastReadChatId(member.getId(), chatRoom.getId(), 100L);
        em.flush();
        em.clear();

        assertThat(updated).isEqualTo(1);
        ChatRoomParticipant found =
                chatRoomParticipantRepository.findChatRoomBy(chatRoom.getId(), member.getId());
        assertThat(found.getLastReadChatId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("현재 cursor 보다 작은 chatId 로는 갱신되지 않는다.")
    void updateLastReadChatIdDoesNotDecreaseTest() {
        // given
        Member member = createMemberBy("member");
        ChatRoom chatRoom = createChatRoomBy("room");
        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(member).chatRoom(chatRoom).build());
        chatRoomParticipantRepository.updateLastReadChatId(member.getId(), chatRoom.getId(),
                200L);
        em.flush();
        em.clear();

        // when
        int updated = chatRoomParticipantRepository.updateLastReadChatId(
                member.getId(), chatRoom.getId(), 100L
        );
        em.flush();
        em.clear();

        // then
        assertThat(updated).isEqualTo(0);
        ChatRoomParticipant found =
                chatRoomParticipantRepository.findChatRoomBy(chatRoom.getId(), member.getId());
        assertThat(found.getLastReadChatId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("cursor 이후 메시지만 미읽음으로 집계된다.")
    void findCursorUnreadCountsByTest() {
        // given
        Member me = createMemberBy("me");
        Member other = createMemberBy("other");
        ChatRoom chatRoom = createChatRoomBy("room");
        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(me).chatRoom(chatRoom).build());
        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(other).chatRoom(chatRoom).build());

        Chat first  = chatRepository.save(new Chat("msg1", other, chatRoom));
        Chat second = chatRepository.save(new Chat("msg2", other, chatRoom));
        Chat third  = chatRepository.save(new Chat("msg3", other, chatRoom));

        chatRoomParticipantRepository.updateLastReadChatId(me.getId(), chatRoom.getId(),
                first.getId());
        em.flush();
        em.clear();

        // when
        List<ChatRoomUnreadCount> result = chatRoomParticipantRepository
                .findCursorUnreadCountsBy(List.of(chatRoom.getId()), me.getId());

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUnreadMessageCount()).isEqualTo(2L);
    }

    private Member createMemberBy(String username) {
        String commonPassword = "commonPassword";
        Member member = Member.of(username, commonPassword, username);
        return memberRepository.save(member);
    }

    private ChatRoom createChatRoomBy(String title) {
        ChatRoom chatRoom = ChatRoom.of(title);
        return chatRoomRepository.save(chatRoom);
    }
}