package com.chat.service;

import com.chat.api.response.chatroom.ChatRoomMemberResponse;
import com.chat.api.response.chatroom.ChatRoomsResponse;
import com.chat.api.response.chatroom.OpponentResponse;
import com.chat.entity.Chat;
import com.chat.entity.ChatRead;
import com.chat.entity.ChatRoom;
import com.chat.entity.ChatRoomParticipant;
import com.chat.entity.Member;
import com.chat.exception.CustomException;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.ChatReadRepository;
import com.chat.repository.ChatRepository;
import com.chat.repository.ChatRoomParticipantRepository;
import com.chat.repository.ChatRoomRepository;
import com.chat.service.dtos.SaveChatRoomDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class ChatRoomServiceTest {

    @Autowired
    private ChatRoomService chatRoomService;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private ChatReadRepository chatReadRepository;
    @Autowired
    private TestDataFixture fixture;

    @Test
    @DisplayName("채팅방을 저장한다.")
    void saveChatRoomTest() {
        // given
        String title = "title";

        Member sender = fixture.savedMemberBy("sender");
        Member firstReceiver = fixture.savedMemberBy("firstReceiver");
        Member secondReceiver = fixture.savedMemberBy("secondReceiver");

        Set<Long> receiverIds = new HashSet<>();
        receiverIds.add(firstReceiver.getId());
        receiverIds.add(secondReceiver.getId());

        SaveChatRoomDTO dto = SaveChatRoomDTO
                .builder()
                .title(title)
                .senderId(sender.getId())
                .receiverIds(receiverIds)
                .build();

        // when
        Long savedChatRoomId = chatRoomService.saveChatRoom(dto);

        // then
        ChatRoom chatRoom = chatRoomRepository.findById(savedChatRoomId).get();

        List<ChatRoomParticipant> chatRoomParticipants
                = chatRoomParticipantRepository.findAllFetchMemberBy(savedChatRoomId);

        Set<Long> participantMemberIds = chatRoomParticipants.stream()
                .map(p -> p.getMember().getId())
                .collect(Collectors.toSet());

        Set<Long> expectedMemberIds = Set.of(
                sender.getId(),
                firstReceiver.getId(),
                secondReceiver.getId()
        );

        assertThat(chatRoom.getTitle()).isEqualTo(title);
        assertThat(participantMemberIds)
                .hasSize(3)
                .containsExactlyInAnyOrderElementsOf(expectedMemberIds);
    }

    @Test
    @DisplayName("채팅방 목록을 조회한다.")
    void findChatRoomsTest() {
        // given
        Member first = fixture.savedMemberBy("first");
        Member second = fixture.savedMemberBy("second");
        Member third = fixture.savedMemberBy("third");
        Member fourth = fixture.savedMemberBy("fourth");

        Long firstId = first.getId();
        List<Member> secondParticipants = createParticipantsBy(first, second);
        fixture.savedChatRoomBy("title", secondParticipants);

        List<Member> thirdParticipants = createParticipantsBy(first, third);
        fixture.savedChatRoomBy("title", thirdParticipants);

        List<Member> fourthParticipants = createParticipantsBy(first, fourth);
        fixture.savedChatRoomBy("title", fourthParticipants);

        // when
        List<ChatRoomsResponse> chatRooms = chatRoomService.findChatRooms(first.getId());

        // then
        assertThat(chatRooms).hasSize(3);
    }

    @Test
    @DisplayName("메시지가 없는 채팅방 조회 시 lastMessage 는 null, unReadCount 는 0 이다.")
    void findChatRooms_withNoMessagesTest() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        fixture.savedChatRoomBy("title", List.of(me, other));

        // when
        List<ChatRoomsResponse> chatRooms = chatRoomService.findChatRooms(me.getId());

        // then
        assertThat(chatRooms).hasSize(1);
        assertThat(chatRooms.get(0).getLastMessage()).isNull();
        assertThat(chatRooms.get(0).getUnreadMessageCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("채팅방 목록 조회 시 가장 마지막 메시지 정보가 포함된다.")
    void findChatRooms_lastMessageTest() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        ChatRoom chatRoom = fixture.savedChatRoomBy("title", List.of(me, other));

        fixture.savedSimpleChat("first message", other, chatRoom);
        fixture.savedSimpleChat("last message", other, chatRoom);

        // when
        List<ChatRoomsResponse> chatRooms = chatRoomService.findChatRooms(me.getId());

        // then
        assertThat(chatRooms).hasSize(1);
        assertThat(chatRooms.get(0).getLastMessage()).isEqualTo("last message");
        assertThat(chatRooms.get(0).getCreatedDate()).isNotNull();
    }

    @Test
    @DisplayName("채팅방 목록 조회 시 읽지 않은 메시지 수가 포함된다.")
    void findChatRooms_unReadCountTest() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        ChatRoom chatRoom = fixture.savedChatRoomBy("title", List.of(me, other));

        Chat firstChat = fixture.savedSimpleChat("msg1", other, chatRoom);
        Chat secondChat = fixture.savedSimpleChat("msg2", other, chatRoom);

        chatReadRepository.save(new ChatRead(false, me, firstChat));
        chatReadRepository.save(new ChatRead(false, me, secondChat));

        // when
        List<ChatRoomsResponse> chatRooms = chatRoomService.findChatRooms(me.getId());

        // then
        assertThat(chatRooms).hasSize(1);
        assertThat(chatRooms.get(0).getUnreadMessageCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("채팅방 목록 조회 시 본인을 제외한 상대방 정보가 포함된다.")
    void findChatRooms_opponentsTest() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        fixture.savedChatRoomBy("title", List.of(me, other));

        // when
        List<ChatRoomsResponse> chatRooms = chatRoomService.findChatRooms(me.getId());

        // then
        assertThat(chatRooms).hasSize(1);
        List<OpponentResponse> opponents = chatRooms.get(0).getOpponents();
        assertThat(opponents).hasSize(1);
        assertThat(opponents.get(0).getOpponentId()).isEqualTo(other.getId());
    }

    @Test
    @DisplayName("채팅방에서 나가면 ChatRoomParticipant 행이 삭제된다.")
    void leaveChatRoomTest() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        ChatRoom chatRoom = fixture.savedChatRoomBy("title", List.of(me, other));

        // when
        chatRoomService.leaveChatRoom(me.getId(), chatRoom.getId());

        // then
        ChatRoomParticipant participant = chatRoomParticipantRepository.findChatRoomBy(chatRoom.getId(), me.getId());
        assertThat(participant).isNull();

        // 상대방 행은 유지
        ChatRoomParticipant otherParticipant = chatRoomParticipantRepository.findChatRoomBy(chatRoom.getId(), other.getId());
        assertThat(otherParticipant).isNotNull();
    }

    @Test
    @DisplayName("방 멤버가 아닌 사용자가 나가기를 시도하면 예외가 발생한다.")
    void leaveChatRoom_notMember_throwsException() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member stranger = fixture.savedMemberBy("stranger");
        ChatRoom chatRoom = fixture.savedChatRoomBy("title", List.of(me));

        // when & then
        assertThatThrownBy(() -> chatRoomService.leaveChatRoom(stranger.getId(), chatRoom.getId()))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("채팅방 이름을 변경한다.")
    void renameChatRoomTest() {
        // given
        Member me = fixture.savedMemberBy("me");
        ChatRoom chatRoom = fixture.savedChatRoomBy("original", List.of(me));

        // when
        chatRoomService.renameChatRoom(me.getId(), chatRoom.getId(), "changed");

        // then
        ChatRoom found = chatRoomRepository.findById(chatRoom.getId()).get();
        assertThat(found.getTitle()).isEqualTo("changed");
    }

    @Test
    @DisplayName("방 멤버가 아닌 사용자가 이름 변경을 시도하면 예외가 발생한다.")
    void renameChatRoom_notMember_throwsException() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member stranger = fixture.savedMemberBy("stranger");
        ChatRoom chatRoom = fixture.savedChatRoomBy("title", List.of(me));

        // when & then
        assertThatThrownBy(() -> chatRoomService.renameChatRoom(stranger.getId(), chatRoom.getId(), "new"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("채팅방 멤버 목록을 조회한다.")
    void findChatRoomMembersTest() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        ChatRoom chatRoom = fixture.savedChatRoomBy("title", List.of(me, other));

        // when
        List<ChatRoomMemberResponse> members = chatRoomService.findChatRoomMembers(me.getId(), chatRoom.getId());

        // then
        assertThat(members).hasSize(2);
        assertThat(members).extracting(ChatRoomMemberResponse::getMemberId)
                .containsExactlyInAnyOrder(me.getId(), other.getId());
    }

    @Test
    @DisplayName("멤버를 초대하면 ChatRoomParticipant 행이 추가된다.")
    void inviteMembersTest() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member invitee = fixture.savedMemberBy("invitee");
        ChatRoom chatRoom = fixture.savedChatRoomBy("title", List.of(me));

        // when
        chatRoomService.inviteMembers(me.getId(), chatRoom.getId(), Set.of(invitee.getId()));

        // then
        ChatRoomParticipant participant = chatRoomParticipantRepository.findChatRoomBy(chatRoom.getId(), invitee.getId());
        assertThat(participant).isNotNull();
    }

    @Test
    @DisplayName("이미 방 멤버인 사용자를 초대하면 중복 추가되지 않는다.")
    void inviteMembers_alreadyMember_skipped() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member existing = fixture.savedMemberBy("existing");
        ChatRoom chatRoom = fixture.savedChatRoomBy("title", List.of(me, existing));

        // when
        chatRoomService.inviteMembers(me.getId(), chatRoom.getId(), Set.of(existing.getId()));

        // then
        List<ChatRoomParticipant> participants = chatRoomParticipantRepository.findAllFetchMemberBy(chatRoom.getId());
        assertThat(participants).hasSize(2);
    }

    // todo connect & broadCastMessage 테스트 필요

    private List<Member> createParticipantsBy(Member first, Member second) {
        List<Member> participants = new ArrayList<>();
        participants.add(first);
        participants.add(second);

        return participants;
    }
}