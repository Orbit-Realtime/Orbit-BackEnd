package com.chat.service;

import com.chat.api.response.chatroom.SpaceInviteCodeResponse;
import com.chat.api.response.chatroom.SpaceInviteInfoResponse;
import com.chat.api.response.chatroom.SpaceMemberResponse;
import com.chat.api.response.chatroom.SpaceSummaryResponse;
import com.chat.entity.Discussion;
import com.chat.entity.DiscussionMessage;
import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.entity.SpaceMember;
import com.chat.entity.Member;
import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.DiscussionMessageRepository;
import com.chat.repository.DiscussionRepository;
import com.chat.repository.SpaceMemberRepository;
import com.chat.repository.SpaceRepository;
import com.chat.service.dtos.SaveSpaceDTO;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class SpaceServiceTest {

    @Autowired
    private SpaceService spaceService;
    @Autowired
    private SpaceRepository spaceRepository;
    @Autowired
    private SpaceMemberRepository spaceMemberRepository;
    @Autowired
    private DiscussionRepository discussionRepository;
    @Autowired
    private DiscussionMessageRepository discussionMessageRepository;
    @Autowired
    private EntityManager em;
    @Autowired
    private TestDataFixture fixture;

    @Test
    @DisplayName("title과 생성자만으로 Space를 생성한다.")
    void title과_생성자만으로_Space를_생성한다() {
        // given
        String title = "개발팀";
        Member creator = fixture.savedMemberBy("creator");

        SaveSpaceDTO dto = SaveSpaceDTO
                .builder()
                .title(title)
                .senderId(creator.getId())
                .build();

        // when
        Long savedSpaceId = spaceService.saveSpace(dto);

        // then
        Space savedSpace = spaceRepository.findById(savedSpaceId).get();
        List<SpaceMember> participants = spaceMemberRepository.findAllFetchMemberBy(savedSpaceId);

        assertThat(savedSpace.getTitle()).isEqualTo(title);
        assertThat(participants).hasSize(1);
        assertThat(participants.get(0).getMember().getId()).isEqualTo(creator.getId());
    }

    @Test
    @DisplayName("Space 생성 시 title이 빈 문자열이면 EMPTY_SPACE_TITLE 예외가 발생한다.")
    void Space_생성_시_title이_빈_문자열이면_EMPTY_SPACE_TITLE_예외가_발생한다() {
        // given
        Member creator = fixture.savedMemberBy("creator");

        SaveSpaceDTO dto = SaveSpaceDTO
                .builder()
                .title("")
                .senderId(creator.getId())
                .build();

        // when & then
        assertThatThrownBy(() -> spaceService.saveSpace(dto))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_SPACE_TITLE);
    }

    @Test
    @DisplayName("참여한 Space 목록을 조회한다.")
    void 참여한_Space_목록을_조회한다() {
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
        List<SpaceSummaryResponse> chatRooms = spaceService.findSpaces(first.getId());

        // then
        assertThat(chatRooms).hasSize(3);
    }

    @Test
    @DisplayName("메시지가 없는 Space 조회 시 lastMessage는 null, unReadCount는 0이다.")
    void 메시지가_없는_Space_조회_시_lastMessage는_null이고_unReadCount는_0이다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        fixture.savedChatRoomBy("title", List.of(me, other));

        // when
        List<SpaceSummaryResponse> chatRooms = spaceService.findSpaces(me.getId());

        // then
        assertThat(chatRooms).hasSize(1);
        assertThat(chatRooms.get(0).getLastMessage()).isNull();
        assertThat(chatRooms.get(0).getUnreadMessageCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Space 목록 조회 시 가장 마지막 메시지 정보가 포함된다.")
    void Space_목록_조회_시_가장_마지막_메시지_정보가_포함된다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        Space space = fixture.savedChatRoomBy("title", List.of(me, other));

        fixture.savedSimpleChat("first message", other, space);
        fixture.savedSimpleChat("last message", other, space);

        // when
        List<SpaceSummaryResponse> chatRooms = spaceService.findSpaces(me.getId());

        // then
        assertThat(chatRooms).hasSize(1);
        assertThat(chatRooms.get(0).getLastMessage()).isEqualTo("last message");
        assertThat(chatRooms.get(0).getCreatedDate()).isNotNull();
    }

    @Test
    @DisplayName("Space 목록 조회 시 cursor 이후 메시지 수가 unread count로 반환된다.")
    void Space_목록_조회_시_cursor_이후_메시지_수가_unread_count로_반환된다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        Space space = fixture.savedChatRoomBy("title", List.of(me, other));

        Message firstMessage = fixture.savedSimpleChat("msg1", other, space);
        fixture.savedSimpleChat("msg2", other, space);

        // me cursor를 첫 번째 메시지까지만 읽음 → 두 번째 메시지만 unread
        spaceMemberRepository.updateLastReadMessageId(
                me.getId(), space.getId(), firstMessage.getId());

        // when
        List<SpaceSummaryResponse> chatRooms = spaceService.findSpaces(me.getId());

        // then
        assertThat(chatRooms).hasSize(1);
        assertThat(chatRooms.get(0).getUnreadMessageCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Space에서 나가면 SpaceMember 행이 삭제된다.")
    void Space에서_나가면_SpaceMember_행이_삭제된다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        Space space = fixture.savedChatRoomBy("title", List.of(me, other));

        // when
        spaceService.leaveSpace(me.getId(), space.getId());

        // then
        SpaceMember participant = spaceMemberRepository.findChatRoomBy(space.getId(), me.getId());
        assertThat(participant).isNull();

        // 상대방 행은 유지
        SpaceMember otherParticipant = spaceMemberRepository.findChatRoomBy(space.getId(), other.getId());
        assertThat(otherParticipant).isNotNull();
    }

    @Test
    @DisplayName("방 멤버가 아닌 사용자가 나가기를 시도하면 예외가 발생한다.")
    void 방_멤버가_아닌_사용자가_나가기를_시도하면_예외가_발생한다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member stranger = fixture.savedMemberBy("stranger");
        Space space = fixture.savedChatRoomBy("title", List.of(me));

        // when & then
        assertThatThrownBy(() -> spaceService.leaveSpace(stranger.getId(), space.getId()))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("Space 이름을 변경한다.")
    void Space_이름을_변경한다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Space space = fixture.savedChatRoomBy("original", List.of(me));

        // when
        spaceService.renameSpace(me.getId(), space.getId(), "changed");

        // then
        Space found = spaceRepository.findById(space.getId()).get();
        assertThat(found.getTitle()).isEqualTo("changed");
    }

    @Test
    @DisplayName("방 멤버가 아닌 사용자가 이름 변경을 시도하면 예외가 발생한다.")
    void 방_멤버가_아닌_사용자가_이름_변경을_시도하면_예외가_발생한다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member stranger = fixture.savedMemberBy("stranger");
        Space space = fixture.savedChatRoomBy("title", List.of(me));

        // when & then
        assertThatThrownBy(() -> spaceService.renameSpace(stranger.getId(), space.getId(), "new"))
                .isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("Space 멤버 목록을 조회한다.")
    void Space_멤버_목록을_조회한다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        Space space = fixture.savedChatRoomBy("title", List.of(me, other));

        // when
        List<SpaceMemberResponse> members = spaceService.findSpaceMembers(me.getId(), space.getId());

        // then
        assertThat(members).hasSize(2);
        assertThat(members).extracting(SpaceMemberResponse::getMemberId)
                .containsExactlyInAnyOrder(me.getId(), other.getId());
    }

    @Test
    @DisplayName("멤버를 초대하면 SpaceMember 행이 추가된다.")
    void 멤버를_초대하면_SpaceMember_행이_추가된다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member invitee = fixture.savedMemberBy("invitee");
        Space space = fixture.savedChatRoomBy("title", List.of(me));

        // when
        spaceService.inviteMembers(me.getId(), space.getId(), Set.of(invitee.getId()));

        // then
        SpaceMember participant = spaceMemberRepository.findChatRoomBy(space.getId(), invitee.getId());
        assertThat(participant).isNotNull();
    }

    @Test
    @DisplayName("이미 방 멤버인 사용자를 초대하면 중복 추가되지 않는다.")
    void 이미_방_멤버인_사용자를_초대하면_중복_추가되지_않는다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member existing = fixture.savedMemberBy("existing");
        Space space = fixture.savedChatRoomBy("title", List.of(me, existing));

        // when
        spaceService.inviteMembers(me.getId(), space.getId(), Set.of(existing.getId()));

        // then
        List<SpaceMember> participants = spaceMemberRepository.findAllFetchMemberBy(space.getId());
        assertThat(participants).hasSize(2);
    }

    @Test
    @DisplayName("lastReadMessageId가 null이면 Space의 모든 메시지가 unread count에 포함된다.")
    void lastReadMessageId가_null이면_Space의_모든_메시지가_unread_count에_포함된다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        Space space = fixture.savedChatRoomBy("title", List.of(me, other));

        fixture.savedSimpleChat("msg1", other, space);
        fixture.savedSimpleChat("msg2", other, space);
        // me cursor: null — 한 번도 읽지 않음

        // when
        List<SpaceSummaryResponse> chatRooms = spaceService.findSpaces(me.getId());

        // then
        assertThat(chatRooms.get(0).getUnreadMessageCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("cursor가 최신 메시지와 같으면 unread count는 0이다.")
    void cursor가_최신_메시지와_같으면_unread_count는_0이다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        Space space = fixture.savedChatRoomBy("title", List.of(me, other));

        fixture.savedSimpleChat("msg1", other, space);
        Message lastMessage = fixture.savedSimpleChat("msg2", other, space);

        // me cursor를 최신 메시지로 설정
        spaceMemberRepository.updateLastReadMessageId(
                me.getId(), space.getId(), lastMessage.getId());

        // when
        List<SpaceSummaryResponse> chatRooms = spaceService.findSpaces(me.getId());

        // then
        assertThat(chatRooms.get(0).getUnreadMessageCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("inviteCode로 Space 정보를 조회한다.")
    void inviteCode로_Space_정보를_조회한다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        Space space = fixture.savedChatRoomBy("개발팀", List.of(me, other));

        // when
        SpaceInviteInfoResponse response =
                spaceService.findSpaceByInviteCode(me.getId(), space.getInviteCode());

        // then
        assertThat(response.getSpaceId()).isEqualTo(space.getId());
        assertThat(response.getTitle()).isEqualTo("개발팀");
        assertThat(response.getMemberCount()).isEqualTo(2L);
        assertThat(response.isAlreadyJoined()).isTrue();
    }

    @Test
    @DisplayName("참여하지 않은 사용자가 inviteCode로 조회하면 alreadyJoined가 false이다.")
    void 참여하지_않은_사용자가_inviteCode로_조회하면_alreadyJoined가_false이다() {
        // given
        Member owner = fixture.savedMemberBy("owner");
        Member stranger = fixture.savedMemberBy("stranger");
        Space space = fixture.savedChatRoomBy("개발팀", List.of(owner));

        // when
        SpaceInviteInfoResponse response =
                spaceService.findSpaceByInviteCode(stranger.getId(), space.getInviteCode());

        // then
        assertThat(response.isAlreadyJoined()).isFalse();
        assertThat(response.getMemberCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("잘못된 inviteCode로 조회하면 INVALID_INVITE_CODE 예외가 발생한다.")
    void 잘못된_inviteCode로_조회하면_INVALID_INVITE_CODE_예외가_발생한다() {
        // given
        Member me = fixture.savedMemberBy("me");

        // when & then
        assertThatThrownBy(() -> spaceService.findSpaceByInviteCode(me.getId(), "invalidcode000000000000000000000"))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INVITE_CODE);
    }

    @Test
    @DisplayName("inviteCode로 Space에 참여하면 SpaceMember가 생성된다.")
    void inviteCode로_Space에_참여하면_SpaceMember가_생성된다() {
        // given
        Member owner = fixture.savedMemberBy("owner");
        Member newbie = fixture.savedMemberBy("newbie");
        Space space = fixture.savedChatRoomBy("개발팀", List.of(owner));

        // when
        Long returnedSpaceId = spaceService.joinSpaceByInviteCode(newbie.getId(), space.getInviteCode());

        // then
        assertThat(returnedSpaceId).isEqualTo(space.getId());
        SpaceMember joined = spaceMemberRepository.findChatRoomBy(space.getId(), newbie.getId());
        assertThat(joined).isNotNull();
    }

    @Test
    @DisplayName("이미 참여 중인 사용자가 join을 호출해도 SpaceMember가 중복 생성되지 않는다.")
    void 이미_참여_중인_사용자가_join을_호출해도_SpaceMember가_중복_생성되지_않는다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Space space = fixture.savedChatRoomBy("개발팀", List.of(me));

        // when
        Long returnedSpaceId = spaceService.joinSpaceByInviteCode(me.getId(), space.getInviteCode());

        // then
        assertThat(returnedSpaceId).isEqualTo(space.getId());
        assertThat(spaceMemberRepository.countBySpaceId(space.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("잘못된 inviteCode로 join하면 INVALID_INVITE_CODE 예외가 발생한다.")
    void 잘못된_inviteCode로_join하면_INVALID_INVITE_CODE_예외가_발생한다() {
        // given
        Member me = fixture.savedMemberBy("me");

        // when & then
        assertThatThrownBy(() -> spaceService.joinSpaceByInviteCode(me.getId(), "invalidcode000000000000000000000"))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INVITE_CODE);
    }

    @Test
    @DisplayName("Space 참여자는 inviteCode를 조회할 수 있다.")
    void Space_참여자는_inviteCode를_조회할_수_있다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Space space = fixture.savedChatRoomBy("개발팀", List.of(me));

        // when
        SpaceInviteCodeResponse response = spaceService.getSpaceInviteCode(me.getId(), space.getId());

        // then
        assertThat(response.getInviteCode()).isEqualTo(space.getInviteCode());
        assertThat(response.getInviteCode()).hasSize(32);
    }

    @Test
    @DisplayName("Space 미참여자가 inviteCode 조회를 호출하면 SPACE_NOT_FOUND 예외가 발생한다.")
    void Space_미참여자가_inviteCode_조회를_호출하면_SPACE_NOT_FOUND_예외가_발생한다() {
        // given
        Member owner = fixture.savedMemberBy("owner");
        Member stranger = fixture.savedMemberBy("stranger");
        Space space = fixture.savedChatRoomBy("개발팀", List.of(owner));

        // when & then
        assertThatThrownBy(() -> spaceService.getSpaceInviteCode(stranger.getId(), space.getId()))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 spaceId로 inviteCode 조회를 호출하면 SPACE_NOT_FOUND 예외가 발생한다.")
    void 존재하지_않는_spaceId로_inviteCode_조회를_호출하면_SPACE_NOT_FOUND_예외가_발생한다() {
        // given
        Member me = fixture.savedMemberBy("me");
        Long nonExistentSpaceId = 999_999L;

        // when & then
        assertThatThrownBy(() -> spaceService.getSpaceInviteCode(me.getId(), nonExistentSpaceId))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
    }

    @Test
    @DisplayName("DiscussionMessage 작성 후 Space unreadCount는 증가하지 않는다.")
    void DiscussionMessage_작성_후_Space_unreadCount는_증가하지_않는다() {
        // given
        Member memberA = fixture.savedMemberBy("memberA");
        Member memberB = fixture.savedMemberBy("memberB");
        Space space = fixture.savedChatRoomBy("space", List.of(memberA, memberB));

        Message rootMessage = fixture.savedSimpleChat("root message", memberA, space);

        // memberB가 rootMessage까지 읽은 상태
        spaceMemberRepository.updateLastReadMessageId(
                memberB.getId(), space.getId(), rootMessage.getId());

        Discussion discussion = discussionRepository.save(Discussion.of(rootMessage));
        discussionMessageRepository.save(DiscussionMessage.of("reply1", discussion, memberA));
        discussionMessageRepository.save(DiscussionMessage.of("reply2", discussion, memberA));
        discussionMessageRepository.save(DiscussionMessage.of("reply3", discussion, memberA));

        em.flush();
        em.clear();

        // when
        List<SpaceSummaryResponse> spaces = spaceService.findSpaces(memberB.getId());

        // then
        assertThat(spaces).hasSize(1);
        assertThat(spaces.get(0).getUnreadMessageCount()).isEqualTo(0L);
    }

    private List<Member> createParticipantsBy(Member first, Member second) {
        List<Member> participants = new ArrayList<>();
        participants.add(first);
        participants.add(second);

        return participants;
    }
}
