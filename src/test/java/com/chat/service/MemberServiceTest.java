package com.chat.service;

import com.chat.api.request.member.JoinRequest;
import com.chat.api.request.member.LoginRequest;
import com.chat.api.response.member.GetMembersResponse;
import com.chat.entity.Member;
import com.chat.exception.CustomException;
import com.chat.repository.MemberRepository;
import com.chat.service.dtos.LoginResponse;
import com.chat.socket.manager.SpaceManager;
import com.chat.socket.manager.WebsocketSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class MemberServiceTest {

    @Autowired
    private MemberService memberService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private WebsocketSessionManager websocketSessionManager;
    @Autowired
    private SpaceManager spaceManager;

    @BeforeEach
    void setUp() {
        websocketSessionManager.clearAll();
        spaceManager.clearAll();
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
    @DisplayName("닉네임을 변경한다.")
    void changeNicknameTest() {
        // given
        Long memberId = joinSimpleMember("user");
        String newNickname = "newNickname";

        // when
        memberService.changeNickname(memberId, newNickname);

        // then
        Member findMember = memberRepository.findById(memberId).get();
        assertThat(findMember.getNickname()).isEqualTo(newNickname);
    }

    @Test
    @DisplayName("비밀번호를 변경한다.")
    void changePasswordTest() {
        // given
        Long memberId = joinMember("user", "oldPassword", "nickname");
        String oldPassword = memberRepository.findById(memberId).get().getPassword();

        // when
        memberService.changePassword(memberId, "oldPassword", "newPassword");

        // then
        Member findMember = memberRepository.findById(memberId).get();
        assertThat(findMember.getPassword()).isNotEqualTo(oldPassword);
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 비밀번호 변경 시 예외가 발생한다.")
    void changePassword_wrongCurrentPassword_throwsException() {
        // given
        Long memberId = joinMember("user", "correctPassword", "nickname");

        // when & then
        assertThatThrownBy(() -> memberService.changePassword(memberId, "wrongPassword", "newPassword"))
                .isInstanceOf(CustomException.class);
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