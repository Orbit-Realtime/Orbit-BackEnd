package com.chat.service;

import com.chat.api.request.member.JoinRequest;
import com.chat.api.request.member.LoginRequest;
import com.chat.api.response.member.GetMembersResponse;
import com.chat.entity.ChatRoom;
import com.chat.entity.ChatRoomParticipant;
import com.chat.entity.Member;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.ChatRoomParticipantRepository;
import com.chat.repository.MemberRepository;
import com.chat.service.dtos.LoginResponse;
import com.chat.socket.manager.ChatRoomManager;
import com.chat.socket.manager.WebsocketSessionManager;
import com.chat.utils.consts.SessionConst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@Transactional
@SpringBootTest
class MemberServiceTest {

    @Autowired
    private MemberService memberService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TestDataFixture fixture;
    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    @Autowired
    private ChatRoomParticipantService chatRoomParticipantService;
    @Autowired
    private WebsocketSessionManager websocketSessionManager;
    @Autowired
    private ChatRoomManager chatRoomManager;

    @BeforeEach
    void setUp() {
        websocketSessionManager.clearAll();
        chatRoomManager.clearAll();
    }

    @Test
    @DisplayName("사용자가 회원가입한다.")
    void joinTest() {
        // given
        JoinRequest request = JoinRequest.builder()
                .username("username")
                .password("password")
                .nickname("nickname")
                .build();

        // when
        Long joinMemberId = memberService.join(request);

        // then
        Member findMember = memberRepository.findById(joinMemberId).get();
        assertThat(findMember.getId()).isEqualTo(joinMemberId);
        assertThat(findMember.getUsername()).isEqualTo("username");
        assertThat(findMember.getPassword()).isNotEqualTo("password");
    }

    @Test
    @DisplayName("사용자가 로그인한다.")
    void loginTest() {
        // given
        Long joinMemberId = joinMember("username", "password", "nickname");

        // login()은 Propagation.NOT_SUPPORTED로 실행되어 별도 TX에서 DB를 조회한다.
        // join()으로 저장한 데이터가 별도 TX에서 보이려면 먼저 커밋되어야 한다.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        LoginRequest request = LoginRequest.builder()
                .username("username")
                .password("password")
                .build();

        // when
        LoginResponse response = memberService.login(request);

        // then
        assertThat(response.getMemberId()).isEqualTo(joinMemberId);
        assertThat(response.getNickname()).isEqualTo("nickname");

        // T1에서 커밋된 데이터는 자동 롤백되지 않으므로 직접 정리
        memberRepository.deleteById(joinMemberId);
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    @Test
    @DisplayName("가입된 모든 사용자를 조회한다.")
    void findMembersTest() {
        // given
        String firstUsername = "first";
        Long firstMemberId = joinSimpleMember(firstUsername);
        String secondUsername = "second";
        Long secondMemberId = joinSimpleMember(secondUsername);
        String thirdUsername = "third";
        Long thirdMemberId = joinSimpleMember(thirdUsername);

        // when
        List<GetMembersResponse> members = memberService.findMembers();

        // then
        assertThat(members).hasSize(3);
    }

    @Test
    @DisplayName("채팅방이 없으면 세션만 제거한다.")
    void removeSession_noChatRoom() {
        // given
        Long memberId = joinSimpleMember("user1");
        WebSocketSession mockSession = mock(WebSocketSession.class);
        given(mockSession.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));
        websocketSessionManager.addSession(memberId, mockSession);

        // when
        memberService.removeSession(memberId, mockSession);

        // then
        assertThat(websocketSessionManager.getSessionBy(memberId)).isEmpty();
    }

    @Test
    @DisplayName("단일 세션 제거 시 채팅방에서 나간다.")
    void removeSession_singleSession_leavesRoom() {
        // given
        Long memberId = joinSimpleMember("user2");
        Member member = memberRepository.findById(memberId).get();

        List<Member> participants = new ArrayList<>();
        participants.add(member);
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", participants);
        Long chatRoomId = chatRoom.getId();

        chatRoomParticipantService.enterChatRoom(chatRoomId, memberId);

        WebSocketSession mockSession = mock(WebSocketSession.class);
        given(mockSession.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));
        websocketSessionManager.addSession(memberId, mockSession);
        chatRoomManager.addSessionToRoom(mockSession, chatRoomId);

        // when
        memberService.removeSession(memberId, mockSession);

        // then
        assertThat(websocketSessionManager.getSessionBy(memberId)).isEmpty();
        assertThat(chatRoomManager.getChatRoomIdsBy(memberId)).isNull();
        ChatRoomParticipant participant = chatRoomParticipantRepository.findChatRoomBy(chatRoomId, memberId);
        assertThat(participant.isParticipate()).isFalse();
    }

    @Test
    @DisplayName("같은 멤버의 다른 세션이 남아있으면 채팅방에서 나가지 않는다.")
    void removeSession_multiSession_doesNotLeaveRoom() {
        // given
        Long memberId = joinSimpleMember("user3");
        Member member = memberRepository.findById(memberId).get();

        List<Member> participants = new ArrayList<>();
        participants.add(member);
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", participants);
        Long chatRoomId = chatRoom.getId();

        chatRoomParticipantService.enterChatRoom(chatRoomId, memberId);

        WebSocketSession sessionA = mock(WebSocketSession.class);
        given(sessionA.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));
        WebSocketSession sessionB = mock(WebSocketSession.class);
        given(sessionB.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, memberId));

        websocketSessionManager.addSession(memberId, sessionA);
        websocketSessionManager.addSession(memberId, sessionB);
        chatRoomManager.addSessionToRoom(sessionA, chatRoomId);
        chatRoomManager.addSessionToRoom(sessionB, chatRoomId);

        // when: sessionA 만 제거
        memberService.removeSession(memberId, sessionA);

        // then: sessionB 가 남아있으므로 채팅방 참여 유지
        assertThat(websocketSessionManager.getSessionBy(memberId)).hasSize(1);
        assertThat(chatRoomManager.getChatRoomIdsBy(memberId)).contains(chatRoomId);
        ChatRoomParticipant participant = chatRoomParticipantRepository.findChatRoomBy(chatRoomId, memberId);
        assertThat(participant.isParticipate()).isTrue();
    }

    private Long joinMember(String username, String password, String nickname) {
        JoinRequest request = JoinRequest.builder()
                .username(username)
                .password(password)
                .nickname(nickname)
                .build();

        return memberService.join(request);
    }

    private Long joinSimpleMember(String username) {
        JoinRequest request = JoinRequest.builder()
                .username(username)
                .password("password")
                .nickname("nickname")
                .build();

        return memberService.join(request);
    }
}