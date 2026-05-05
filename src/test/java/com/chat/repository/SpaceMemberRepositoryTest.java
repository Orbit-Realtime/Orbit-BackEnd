package com.chat.repository;

import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.entity.SpaceMember;
import com.chat.entity.Member;
import com.chat.repository.dtos.MessageUnreadMemberCount;
import com.chat.repository.dtos.MemberUnreadCount;
import com.chat.repository.dtos.RoomUnreadMessageCount;
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
class SpaceMemberRepositoryTest {

    @Autowired
    private SpaceMemberRepository spaceMemberRepository;
    @Autowired
    private SpaceRepository spaceRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("채팅방 참여 정보를 저장한다.")
    void saveTest() {
        // given
        String username = "username";
        Member savedMember = createMemberBy(username);

        String title = "title";
        Space chatRoom = Space.of(title);
        Space savedChatRoom = spaceRepository.save(chatRoom);
        SpaceMember spaceMember = SpaceMember.builder().member(savedMember).space(savedChatRoom).build();

        // when
        SpaceMember savedSpaceMember = spaceMemberRepository.save(spaceMember);

        // then
        assertThat(savedSpaceMember.getId()).isNotNull();
        assertThat(savedSpaceMember.getMember()).isEqualTo(savedMember);
        assertThat(savedSpaceMember.getSpace()).isEqualTo(savedChatRoom);
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
        Space chatRoom = createChatRoomBy(title);

        spaceMemberRepository.save(SpaceMember.builder().member(firstMember).space(chatRoom).build());
        spaceMemberRepository.save(SpaceMember.builder().member(secondMember).space(chatRoom).build());
        spaceMemberRepository.save(SpaceMember.builder().member(thirdMember).space(chatRoom).build());

        // when
        List<Long> chatRoomIds = spaceMemberRepository.findChatRoomIdsByExactMembers(memberIds, memberIds.size());

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
        Space chatRoom = createChatRoomBy(title);

        spaceMemberRepository.save(SpaceMember.builder().member(firstMember).space(chatRoom).build());
        spaceMemberRepository.save(SpaceMember.builder().member(secondMember).space(chatRoom).build());
        spaceMemberRepository.save(SpaceMember.builder().member(thirdMember).space(chatRoom).build());

        // when
        List<Long> chatRoomIds = spaceMemberRepository.findChatRoomIdsByExactMembers(memberIds, memberIds.size());

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
        Space chatRoom = createChatRoomBy(title);

        spaceMemberRepository.save(SpaceMember.builder().member(firstMember).space(chatRoom).build());
        spaceMemberRepository.save(SpaceMember.builder().member(secondMember).space(chatRoom).build());

        // when
        List<Long> chatRoomIds = spaceMemberRepository.findChatRoomIdsByExactMembers(memberIds, memberIds.size());

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
        Space first = createChatRoomBy(firstTitle);
        String secondTitle = "secondTitle";
        Space second = createChatRoomBy(secondTitle);

        SpaceMember firstParticipant = spaceMemberRepository.save(SpaceMember.builder().member(member).space(first).build());
        SpaceMember secondParticipant = spaceMemberRepository.save(SpaceMember.builder().member(member).space(second).build());

        // when
        List<SpaceMember> findSpaceMembers = spaceMemberRepository.findAllFetchChatRoomBy(member.getId());

        // then
        assertThat(findSpaceMembers).hasSize(2);
        assertThat(findSpaceMembers).containsExactly(firstParticipant, secondParticipant);
    }

    @Test
    @DisplayName("채팅방 ID 를 이용해 채팅방 참여, 회원 정보를 조회한다.")
    void findAllFetchMemberByTest() {
        // given
        String username = "username";
        Member member = createMemberBy(username);
        String title = "chatRoom";
        Space chatRoom = createChatRoomBy(title);
        spaceMemberRepository.save(SpaceMember.builder().member(member).space(chatRoom).build());

        // when
        List<SpaceMember> spaceMembers
                = spaceMemberRepository.findAllFetchMemberBy(chatRoom.getId());

        // then
        assertThat(spaceMembers).hasSize(1);
    }

    @Test
    @DisplayName("채팅방 참여 조회 시 사용자 정보를 fetch 해 쿼리가 1번만 실행된다.")
    void shouldFetchMembersWithParticipantsUsingSingleQuery() {
        // given
        String username = "username";
        Member member = createMemberBy(username);
        String title = "chatRoom";
        Space chatRoom = createChatRoomBy(title);
        spaceMemberRepository.save(SpaceMember.builder().member(member).space(chatRoom).build());

        em.flush();
        em.clear();

        Session session = em.unwrap(Session.class);
        session.getSessionFactory().getStatistics().setStatisticsEnabled(true);
        session.getSessionFactory().getStatistics().clear();

        // when
        List<SpaceMember> spaceMembers
                = spaceMemberRepository.findAllFetchMemberBy(chatRoom.getId());
        for (SpaceMember spaceMember : spaceMembers) {
            spaceMember.getMember().getUsername();
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
        Space chatRoom = createChatRoomBy(title);
        spaceMemberRepository.save(SpaceMember.builder().member(member).space(chatRoom).build());

        // when
        SpaceMember spaceMember = spaceMemberRepository.findChatRoomBy(chatRoom.getId(), member.getId());

        // then
        assertThat(spaceMember.getSpace()).isEqualTo(chatRoom);
        assertThat(spaceMember.getMember()).isEqualTo(member);
    }

    @Test
    @DisplayName("여러 채팅방 ID 로 참여자와 회원 정보를 일괄 조회한다.")
    void findAllFetchMemberByRoomIdsTest() {
        // given
        Member firstMember = createMemberBy("first");
        Member secondMember = createMemberBy("second");
        Member thirdMember = createMemberBy("third");

        Space firstRoom = createChatRoomBy("firstRoom");
        Space secondRoom = createChatRoomBy("secondRoom");

        spaceMemberRepository.save(SpaceMember.builder().member(firstMember).space(firstRoom).build());
        spaceMemberRepository.save(SpaceMember.builder().member(secondMember).space(firstRoom).build());
        spaceMemberRepository.save(SpaceMember.builder().member(thirdMember).space(secondRoom).build());

        em.flush();
        em.clear();

        // when
        List<SpaceMember> participants = spaceMemberRepository
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
        Space chatRoom = createChatRoomBy("chatRoom");
        spaceMemberRepository.save(SpaceMember.builder().member(member).space(chatRoom).build());

        // when
        spaceMemberRepository.deleteBy(chatRoom.getId(), member.getId());
        em.flush();
        em.clear();

        // then
        SpaceMember result = spaceMemberRepository.findChatRoomBy(chatRoom.getId(), member.getId());
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("채팅방 참여 생성 시 lastReadChatId 기본값은 null 이다.")
    void lastReadChatIdDefaultNullTest() {
        // given
        Member member = createMemberBy("member");
        Space chatRoom = createChatRoomBy("room");

        // when
        SpaceMember saved = spaceMemberRepository.save(
                SpaceMember.builder().member(member).space(chatRoom).build()
        );

        // then
        assertThat(saved.getLastReadChatId()).isNull();
    }

    @Test
    @DisplayName("lastReadChatId 가 null 인 경우 특정 chatId 로 갱신된다.")
    void updateLastReadChatIdFromNullTest() {
        // given
        Member member = createMemberBy("member");
        Space chatRoom = createChatRoomBy("room");
        spaceMemberRepository.save(
                SpaceMember.builder().member(member).space(chatRoom).build()
        );
        em.flush();
        em.clear();

        int updated = spaceMemberRepository.updateLastReadChatId(member.getId(), chatRoom.getId(), 100L);
        em.flush();
        em.clear();

        assertThat(updated).isEqualTo(1);
        SpaceMember found =
                spaceMemberRepository.findChatRoomBy(chatRoom.getId(), member.getId());
        assertThat(found.getLastReadChatId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("현재 cursor 보다 작은 chatId 로는 갱신되지 않는다.")
    void updateLastReadChatIdDoesNotDecreaseTest() {
        // given
        Member member = createMemberBy("member");
        Space chatRoom = createChatRoomBy("room");
        spaceMemberRepository.save(
                SpaceMember.builder().member(member).space(chatRoom).build());
        spaceMemberRepository.updateLastReadChatId(member.getId(), chatRoom.getId(),
                200L);
        em.flush();
        em.clear();

        // when
        int updated = spaceMemberRepository.updateLastReadChatId(
                member.getId(), chatRoom.getId(), 100L
        );
        em.flush();
        em.clear();

        // then
        assertThat(updated).isEqualTo(0);
        SpaceMember found =
                spaceMemberRepository.findChatRoomBy(chatRoom.getId(), member.getId());
        assertThat(found.getLastReadChatId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("cursor 이후 메시지만 미읽음으로 집계된다.")
    void findRoomUnreadMessageCountsByTest() {
        // given
        Member me = createMemberBy("me");
        Member other = createMemberBy("other");
        Space chatRoom = createChatRoomBy("room");
        spaceMemberRepository.save(
                SpaceMember.builder().member(me).space(chatRoom).build());
        spaceMemberRepository.save(
                SpaceMember.builder().member(other).space(chatRoom).build());

        Message first  = messageRepository.save(new Message("msg1", other, chatRoom));
        Message second = messageRepository.save(new Message("msg2", other, chatRoom));
        Message third  = messageRepository.save(new Message("msg3", other, chatRoom));

        spaceMemberRepository.updateLastReadChatId(me.getId(), chatRoom.getId(),
                first.getId());
        em.flush();
        em.clear();

        // when
        List<RoomUnreadMessageCount> result = spaceMemberRepository
                .findRoomUnreadMessageCountsBy(List.of(chatRoom.getId()), me.getId());

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUnreadMessageCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("방 내 여러 멤버의 cursor 기반 미읽음 수를 일괄 조회한다.")
    void findMemberUnreadMessageCountsByTest() {
        // given
        Member me = createMemberBy("me");
        Member other = createMemberBy("other");
        Member sender = createMemberBy("sender");
        Space chatRoom = createChatRoomBy("room");

        spaceMemberRepository.save(
                SpaceMember.builder().member(me).space(chatRoom).build());
        spaceMemberRepository.save(
                SpaceMember.builder().member(other).space(chatRoom).build());
        spaceMemberRepository.save(
                SpaceMember.builder().member(sender).space(chatRoom).build());

        Message first = messageRepository.save(new Message("msg1", sender, chatRoom));
        messageRepository.save(new Message("msg2", sender, chatRoom));

        // me: cursor null → 전체 2개 unread
        // other: cursor = first → second만 1개 unread
        spaceMemberRepository.updateLastReadChatId(
                other.getId(), chatRoom.getId(), first.getId());
        em.flush(); em.clear();

        // when
        List<MemberUnreadCount> result = spaceMemberRepository
                .findMemberUnreadMessageCountsBy(
                        chatRoom.getId(),
                        List.of(me.getId(), other.getId()));

        // then
        Map<Long, Long> countMap = result.stream()
                .collect(Collectors.toMap(
                        MemberUnreadCount::getMemberId,
                        MemberUnreadCount::getUnreadMessageCount));

        assertThat(countMap.get(me.getId())).isEqualTo(2L);
        assertThat(countMap.get(other.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("lastReadChatId가 null이면 findLastReadChatIdBy는 null을 반환한다.")
    void findLastReadChatIdBy_returnsNullWhenNotRead() {
        // given
        Member member = createMemberBy("member");
        Space chatRoom = createChatRoomBy("room");
        spaceMemberRepository.save(
                SpaceMember.builder().member(member).space(chatRoom).build());
        em.flush();
        em.clear();

        // when
        Long result = spaceMemberRepository
                .findLastReadChatIdBy(member.getId(), chatRoom.getId());

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("lastReadChatId 갱신 후 findLastReadChatIdBy는 갱신된 값을 반환한다.")
    void findLastReadChatIdBy_returnsValueAfterUpdate() {
        // given
        Member member = createMemberBy("member");
        Space chatRoom = createChatRoomBy("room");
        spaceMemberRepository.save(
                SpaceMember.builder().member(member).space(chatRoom).build());
        spaceMemberRepository.updateLastReadChatId(member.getId(), chatRoom.getId(),
                100L);
        em.flush();
        em.clear();

        // when
        Long result = spaceMemberRepository
                .findLastReadChatIdBy(member.getId(), chatRoom.getId());

        // then
        assertThat(result).isEqualTo(100L);
    }

    @Test
    @DisplayName("메시지를 보낸 발신자는 unreadMemberCount 집계에서 제외된다.")
    void countMessageUnreadMembers_senderNotCounted() {
        // given
        Member sender = createMemberBy("sender");
        Space chatRoom = createChatRoomBy("room");
        spaceMemberRepository.save(
                SpaceMember.builder().member(sender).space(chatRoom).build());

        Message chat = messageRepository.save(new Message("hello", sender, chatRoom));
        // 발신자 cursor = chatId (saveChat 흐름 재현)
        spaceMemberRepository.updateLastReadChatId(
                sender.getId(), chatRoom.getId(), chat.getId());
        em.flush(); em.clear();

        // when
        Long count = spaceMemberRepository
                .countMessageUnreadMembers(chat.getId());

        // then: cursor = chatId → lastReadChatId < chatId 불충족 → 제외
        assertThat(count).isEqualTo(0L);
    }

    @Test
    @DisplayName("cursor가 null인 수신자는 unreadMemberCount 집계에 포함된다.")
    void countMessageUnreadMembers_receiverNotRead() {
        // given
        Member sender = createMemberBy("sender");
        Member receiver1 = createMemberBy("receiver1");
        Member receiver2 = createMemberBy("receiver2");
        Space chatRoom = createChatRoomBy("room");

        spaceMemberRepository.save(
                SpaceMember.builder().member(sender).space(chatRoom).build());
        spaceMemberRepository.save(
                SpaceMember.builder().member(receiver1).space(chatRoom).build());
        spaceMemberRepository.save(
                SpaceMember.builder().member(receiver2).space(chatRoom).build());

        Message chat = messageRepository.save(new Message("hello", sender, chatRoom));
        spaceMemberRepository.updateLastReadChatId(
                sender.getId(), chatRoom.getId(), chat.getId());
        em.flush(); em.clear();

        // when
        Long count = spaceMemberRepository
                .countMessageUnreadMembers(chat.getId());

        // then: receiver1, receiver2 cursor=null → 2
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("수신자 cursor가 갱신되면 해당 메시지의 unreadMemberCount가 감소한다.")
    void countMessageUnreadMembers_afterReceiverReads() {
        // given
        Member sender = createMemberBy("sender");
        Member receiver = createMemberBy("receiver");
        Space chatRoom = createChatRoomBy("room");

        spaceMemberRepository.save(
                SpaceMember.builder().member(sender).space(chatRoom).build());
        spaceMemberRepository.save(
                SpaceMember.builder().member(receiver).space(chatRoom).build());

        Message chat = messageRepository.save(new Message("hello", sender, chatRoom));
        spaceMemberRepository.updateLastReadChatId(
                sender.getId(), chatRoom.getId(), chat.getId());
        em.flush(); em.clear();

        Long beforeRead = spaceMemberRepository
                .countMessageUnreadMembers(chat.getId());
        assertThat(beforeRead).isEqualTo(1L);

        // receiver 입장 → cursor 갱신
        spaceMemberRepository.updateLastReadChatId(
                receiver.getId(), chatRoom.getId(), chat.getId());
        em.flush(); em.clear();

        // when
        Long afterRead = spaceMemberRepository
                .countMessageUnreadMembers(chat.getId());

        // then
        assertThat(afterRead).isEqualTo(0L);
    }

    @Test
    @DisplayName("여러 메시지의 unreadMemberCount를 일괄 조회한다.")
    void countMessageUnreadMembers_bulkPerMessage() {
        // given
        Member sender = createMemberBy("sender");
        Member receiver = createMemberBy("receiver");
        Space chatRoom = createChatRoomBy("room");

        spaceMemberRepository.save(
                SpaceMember.builder().member(sender).space(chatRoom).build());
        spaceMemberRepository.save(
                SpaceMember.builder().member(receiver).space(chatRoom).build());

        Message first = messageRepository.save(new Message("first", sender, chatRoom));
        Message second = messageRepository.save(new Message("second", sender, chatRoom));

        // sender: second까지 읽음, receiver: cursor=null
        spaceMemberRepository.updateLastReadChatId(
                sender.getId(), chatRoom.getId(), second.getId());
        em.flush(); em.clear();

        // when
        List<MessageUnreadMemberCount> result = spaceMemberRepository
                .countMessageUnreadMembers(List.of(first.getId(), second.getId()));
        Map<Long, Long> countMap = result.stream()
                .collect(Collectors.toMap(MessageUnreadMemberCount::getChatId,
                                          MessageUnreadMemberCount::getUnreadMemberCount));

        // then: 두 메시지 모두 receiver(null) → 1
        assertThat(countMap.get(first.getId())).isEqualTo(1L);
        assertThat(countMap.get(second.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("멤버마다 cursor 위치가 다를 때 각 메시지별 unreadMemberCount를 정확히 반환한다.")
    void countMessageUnreadMembers_differentCursorPositions() {
        // given
        Member sender = createMemberBy("sender");
        Member readerOfFirst = createMemberBy("readerOfFirst");
        Member noReader = createMemberBy("noReader");
        Space chatRoom = createChatRoomBy("room");

        spaceMemberRepository.save(
                SpaceMember.builder().member(sender).space(chatRoom).build());
        spaceMemberRepository.save(
                SpaceMember.builder().member(readerOfFirst).space(chatRoom).build());
        spaceMemberRepository.save(
                SpaceMember.builder().member(noReader).space(chatRoom).build());

        Message first = messageRepository.save(new Message("first", sender, chatRoom));
        Message second = messageRepository.save(new Message("second", sender, chatRoom));

        // sender: second까지, readerOfFirst: first까지, noReader: cursor=null
        spaceMemberRepository.updateLastReadChatId(
                sender.getId(), chatRoom.getId(), second.getId());
        spaceMemberRepository.updateLastReadChatId(
                readerOfFirst.getId(), chatRoom.getId(), first.getId());
        em.flush(); em.clear();

        // when
        List<MessageUnreadMemberCount> result = spaceMemberRepository
                .countMessageUnreadMembers(List.of(first.getId(), second.getId()));
        Map<Long, Long> countMap = result.stream()
                .collect(Collectors.toMap(MessageUnreadMemberCount::getChatId,
                                          MessageUnreadMemberCount::getUnreadMemberCount));

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

    private Space createChatRoomBy(String title) {
        Space chatRoom = Space.of(title);
        return spaceRepository.save(chatRoom);
    }
}
