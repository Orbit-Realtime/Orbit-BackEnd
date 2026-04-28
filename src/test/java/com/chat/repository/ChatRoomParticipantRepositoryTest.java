package com.chat.repository;

import com.chat.entity.Chat;
import com.chat.entity.ChatRoom;
import com.chat.entity.ChatRoomParticipant;
import com.chat.entity.Member;
import com.chat.repository.dtos.ChatRoomUnreadCount;
import com.chat.repository.dtos.ChatUnreadCount;
import com.chat.repository.dtos.MemberUnreadCount;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    @Test
    @DisplayName("방 내 여러 멤버의 cursor 기반 미읽음 수를 일괄 조회한다.")
    void findCursorUnreadCountsByMembersTest() {
        // given
        Member me = createMemberBy("me");
        Member other = createMemberBy("other");
        Member sender = createMemberBy("sender");
        ChatRoom chatRoom = createChatRoomBy("room");

        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(me).chatRoom(chatRoom).build());
        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(other).chatRoom(chatRoom).build());
        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(sender).chatRoom(chatRoom).build());

        Chat first = chatRepository.save(new Chat("msg1", sender, chatRoom));
        chatRepository.save(new Chat("msg2", sender, chatRoom));

        // me: cursor null → 전체 2개 unread
        // other: cursor = first → second만 1개 unread
        chatRoomParticipantRepository.updateLastReadChatId(
                other.getId(), chatRoom.getId(), first.getId());
        em.flush(); em.clear();

        // when
        List<MemberUnreadCount> result = chatRoomParticipantRepository
                .findCursorUnreadCountsByMembers(
                        chatRoom.getId(),
                        List.of(me.getId(), other.getId()));

        // then
        Map<Long, Long> countMap = result.stream()
                .collect(Collectors.toMap(
                        MemberUnreadCount::getMemberId,
                        MemberUnreadCount::getUnreadMemberCount));

        assertThat(countMap.get(me.getId())).isEqualTo(2L);
        assertThat(countMap.get(other.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("lastReadChatId가 null이면 findLastReadChatIdBy는 null을 반환한다.")
    void findLastReadChatIdBy_returnsNullWhenNotRead() {
        // given
        Member member = createMemberBy("member");
        ChatRoom chatRoom = createChatRoomBy("room");
        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(member).chatRoom(chatRoom).build());
        em.flush();
        em.clear();

        // when
        Long result = chatRoomParticipantRepository
                .findLastReadChatIdBy(member.getId(), chatRoom.getId());

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("lastReadChatId 갱신 후 findLastReadChatIdBy는 갱신된 값을 반환한다.")
    void findLastReadChatIdBy_returnsValueAfterUpdate() {
        // given
        Member member = createMemberBy("member");
        ChatRoom chatRoom = createChatRoomBy("room");
        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(member).chatRoom(chatRoom).build());
        chatRoomParticipantRepository.updateLastReadChatId(member.getId(), chatRoom.getId(),
                100L);
        em.flush();
        em.clear();

        // when
        Long result = chatRoomParticipantRepository
                .findLastReadChatIdBy(member.getId(), chatRoom.getId());

        // then
        assertThat(result).isEqualTo(100L);
    }

    @Test
    @DisplayName("메시지를 보낸 발신자는 unreadMemberCount 집계에서 제외된다.")
    void countUnreadMembers_senderNotCounted() {
        // given
        Member sender = createMemberBy("sender");
        ChatRoom chatRoom = createChatRoomBy("room");
        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(sender).chatRoom(chatRoom).build());

        Chat chat = chatRepository.save(new Chat("hello", sender, chatRoom));
        // 발신자 cursor = chatId (saveChat 흐름 재현)
        chatRoomParticipantRepository.updateLastReadChatId(
                sender.getId(), chatRoom.getId(), chat.getId());
        em.flush(); em.clear();

        // when
        Long count = chatRoomParticipantRepository
                .countUnreadMembers(chat.getId());

        // then: cursor = chatId → lastReadChatId < chatId 불충족 → 제외
        assertThat(count).isEqualTo(0L);
    }

    @Test
    @DisplayName("cursor가 null인 수신자는 unreadMemberCount 집계에 포함된다.")
    void countUnreadMembers_receiverNotRead() {
        // given
        Member sender = createMemberBy("sender");
        Member receiver1 = createMemberBy("receiver1");
        Member receiver2 = createMemberBy("receiver2");
        ChatRoom chatRoom = createChatRoomBy("room");

        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(sender).chatRoom(chatRoom).build());
        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(receiver1).chatRoom(chatRoom).build());
        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(receiver2).chatRoom(chatRoom).build());

        Chat chat = chatRepository.save(new Chat("hello", sender, chatRoom));
        chatRoomParticipantRepository.updateLastReadChatId(
                sender.getId(), chatRoom.getId(), chat.getId());
        em.flush(); em.clear();

        // when
        Long count = chatRoomParticipantRepository
                .countUnreadMembers(chat.getId());

        // then: receiver1, receiver2 cursor=null → 2
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("수신자 cursor가 갱신되면 해당 메시지의 unreadMemberCount가 감소한다.")
    void countUnreadMembers_afterReceiverReads() {
        // given
        Member sender = createMemberBy("sender");
        Member receiver = createMemberBy("receiver");
        ChatRoom chatRoom = createChatRoomBy("room");

        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(sender).chatRoom(chatRoom).build());
        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(receiver).chatRoom(chatRoom).build());

        Chat chat = chatRepository.save(new Chat("hello", sender, chatRoom));
        chatRoomParticipantRepository.updateLastReadChatId(
                sender.getId(), chatRoom.getId(), chat.getId());
        em.flush(); em.clear();

        Long beforeRead = chatRoomParticipantRepository
                .countUnreadMembers(chat.getId());
        assertThat(beforeRead).isEqualTo(1L);

        // receiver 입장 → cursor 갱신
        chatRoomParticipantRepository.updateLastReadChatId(
                receiver.getId(), chatRoom.getId(), chat.getId());
        em.flush(); em.clear();

        // when
        Long afterRead = chatRoomParticipantRepository
                .countUnreadMembers(chat.getId());

        // then
        assertThat(afterRead).isEqualTo(0L);
    }

    @Test
    @DisplayName("여러 메시지의 unreadMemberCount를 일괄 조회한다.")
    void countUnreadMembers_bulkPerMessage() {
        // given
        Member sender = createMemberBy("sender");
        Member receiver = createMemberBy("receiver");
        ChatRoom chatRoom = createChatRoomBy("room");

        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(sender).chatRoom(chatRoom).build());
        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(receiver).chatRoom(chatRoom).build());

        Chat first = chatRepository.save(new Chat("first", sender, chatRoom));
        Chat second = chatRepository.save(new Chat("second", sender, chatRoom));

        // sender: second까지 읽음, receiver: cursor=null
        chatRoomParticipantRepository.updateLastReadChatId(
                sender.getId(), chatRoom.getId(), second.getId());
        em.flush(); em.clear();

        // when
        List<ChatUnreadCount> result = chatRoomParticipantRepository
                .countUnreadMembers(List.of(first.getId(), second.getId()));
        Map<Long, Long> countMap = result.stream()
                .collect(Collectors.toMap(ChatUnreadCount::getChatId,
                                          ChatUnreadCount::getUnreadMemberCount));

        // then: 두 메시지 모두 receiver(null) → 1
        assertThat(countMap.get(first.getId())).isEqualTo(1L);
        assertThat(countMap.get(second.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("멤버마다 cursor 위치가 다를 때 각 메시지별 unreadMemberCount를 정확히 반환한다.")
    void countUnreadMembers_differentCursorPositions() {
        // given
        Member sender = createMemberBy("sender");
        Member readerOfFirst = createMemberBy("readerOfFirst");
        Member noReader = createMemberBy("noReader");
        ChatRoom chatRoom = createChatRoomBy("room");

        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(sender).chatRoom(chatRoom).build());
        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(readerOfFirst).chatRoom(chatRoom).build());
        chatRoomParticipantRepository.save(
                ChatRoomParticipant.builder().member(noReader).chatRoom(chatRoom).build());

        Chat first = chatRepository.save(new Chat("first", sender, chatRoom));
        Chat second = chatRepository.save(new Chat("second", sender, chatRoom));

        // sender: second까지, readerOfFirst: first까지, noReader: cursor=null
        chatRoomParticipantRepository.updateLastReadChatId(
                sender.getId(), chatRoom.getId(), second.getId());
        chatRoomParticipantRepository.updateLastReadChatId(
                readerOfFirst.getId(), chatRoom.getId(), first.getId());
        em.flush(); em.clear();

        // when
        List<ChatUnreadCount> result = chatRoomParticipantRepository
                .countUnreadMembers(List.of(first.getId(), second.getId()));
        Map<Long, Long> countMap = result.stream()
                .collect(Collectors.toMap(ChatUnreadCount::getChatId,
                                          ChatUnreadCount::getUnreadMemberCount));

        // then
        // firstMessage: noReader(null) → 1
        assertThat(countMap.get(first.getId())).isEqualTo(1L);
        // secondMessage: readerOfFirst(cursor=first.id < second.id), noReader(null) → 2
        assertThat(countMap.get(second.getId())).isEqualTo(2L);
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