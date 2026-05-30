package com.chat.entity;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberTest {

    @Test
    @DisplayName("사용자 ID가 null이면 회원 생성 시 EMPTY_USERNAME 예외가 발생한다.")
    void 사용자_ID가_null이면_회원_생성_시_EMPTY_USERNAME_예외가_발생한다() {
        assertThatThrownBy(() -> Member.of(null, "password", "nickname"))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_USERNAME);
    }

    @Test
    @DisplayName("사용자 ID가 공백이면 회원 생성 시 EMPTY_USERNAME 예외가 발생한다.")
    void 사용자_ID가_공백이면_회원_생성_시_EMPTY_USERNAME_예외가_발생한다() {
        assertThatThrownBy(() -> Member.of("  ", "password", "nickname"))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_USERNAME);
    }

    @Test
    @DisplayName("비밀번호가 null이면 회원 생성 시 EMPTY_PASSWORD 예외가 발생한다.")
    void 비밀번호가_null이면_회원_생성_시_EMPTY_PASSWORD_예외가_발생한다() {
        assertThatThrownBy(() -> Member.of("username", null, "nickname"))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_PASSWORD);
    }

    @Test
    @DisplayName("비밀번호가 공백이면 회원 생성 시 EMPTY_PASSWORD 예외가 발생한다.")
    void 비밀번호가_공백이면_회원_생성_시_EMPTY_PASSWORD_예외가_발생한다() {
        assertThatThrownBy(() -> Member.of("username", "  ", "nickname"))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_PASSWORD);
    }

    @Test
    @DisplayName("닉네임이 null이면 회원 생성 시 EMPTY_NICKNAME 예외가 발생한다.")
    void 닉네임이_null이면_회원_생성_시_EMPTY_NICKNAME_예외가_발생한다() {
        assertThatThrownBy(() -> Member.of("username", "password", null))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_NICKNAME);
    }

    @Test
    @DisplayName("닉네임이 공백이면 회원 생성 시 EMPTY_NICKNAME 예외가 발생한다.")
    void 닉네임이_공백이면_회원_생성_시_EMPTY_NICKNAME_예외가_발생한다() {
        assertThatThrownBy(() -> Member.of("username", "password", "  "))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_NICKNAME);
    }

    @Test
    @DisplayName("유효한 닉네임으로 변경하면 닉네임이 갱신된다.")
    void 유효한_닉네임으로_변경하면_닉네임이_갱신된다() {
        // given
        Member member = Member.of("username", "password", "oldNickname");

        // when
        member.changeNickname("newNickname");

        // then
        assertThat(member.getNickname()).isEqualTo("newNickname");
    }

    @Test
    @DisplayName("닉네임이 null이면 닉네임을 변경할 수 없다.")
    void 닉네임이_null이면_닉네임을_변경할_수_없다() {
        // given
        Member member = Member.of("username", "password", "nickname");

        // when & then
        assertThatThrownBy(() -> member.changeNickname(null))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_NICKNAME);
    }

    @Test
    @DisplayName("닉네임이 공백이면 닉네임을 변경할 수 없다.")
    void 닉네임이_공백이면_닉네임을_변경할_수_없다() {
        // given
        Member member = Member.of("username", "password", "nickname");

        // when & then
        assertThatThrownBy(() -> member.changeNickname("  "))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_NICKNAME);
    }

    @Test
    @DisplayName("유효한 비밀번호로 변경하면 비밀번호가 갱신된다.")
    void 유효한_비밀번호로_변경하면_비밀번호가_갱신된다() {
        // given
        Member member = Member.of("username", "oldPassword", "nickname");

        // when
        member.changePassword("newEncodedPassword");

        // then
        assertThat(member.getPassword()).isEqualTo("newEncodedPassword");
    }

    @Test
    @DisplayName("비밀번호가 null이면 비밀번호를 변경할 수 없다.")
    void 비밀번호가_null이면_비밀번호를_변경할_수_없다() {
        // given
        Member member = Member.of("username", "password", "nickname");

        // when & then
        assertThatThrownBy(() -> member.changePassword(null))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_PASSWORD);
    }

    @Test
    @DisplayName("비밀번호가 공백이면 비밀번호를 변경할 수 없다.")
    void 비밀번호가_공백이면_비밀번호를_변경할_수_없다() {
        // given
        Member member = Member.of("username", "password", "nickname");

        // when & then
        assertThatThrownBy(() -> member.changePassword("  "))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_PASSWORD);
    }
}
