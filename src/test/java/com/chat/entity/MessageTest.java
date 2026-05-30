package com.chat.entity;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageTest {

    @Test
    @DisplayName("내용이 null이면 Message 생성 시 EMPTY_MESSAGE_CONTENT 예외가 발생한다.")
    void 내용이_null이면_Message_생성_시_EMPTY_MESSAGE_CONTENT_예외가_발생한다() {
        // given
        Member member = Member.of("username", "password", "nickname");
        Space space = Space.of("개발팀");

        // when & then
        assertThatThrownBy(() -> Message.of(null, member, space))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_MESSAGE_CONTENT);
    }

    @Test
    @DisplayName("내용이 공백이면 Message 생성 시 EMPTY_MESSAGE_CONTENT 예외가 발생한다.")
    void 내용이_공백이면_Message_생성_시_EMPTY_MESSAGE_CONTENT_예외가_발생한다() {
        // given
        Member member = Member.of("username", "password", "nickname");
        Space space = Space.of("개발팀");

        // when & then
        assertThatThrownBy(() -> Message.of("  ", member, space))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_MESSAGE_CONTENT);
    }

    @Test
    @DisplayName("Member가 null이면 Message 생성 시 MEMBER_NOT_FOUND 예외가 발생한다.")
    void Member가_null이면_Message_생성_시_MEMBER_NOT_FOUND_예외가_발생한다() {
        // given
        Space space = Space.of("개발팀");

        // when & then
        assertThatThrownBy(() -> Message.of("안녕하세요", null, space))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("Space가 null이면 Message 생성 시 SPACE_NOT_FOUND 예외가 발생한다.")
    void Space가_null이면_Message_생성_시_SPACE_NOT_FOUND_예외가_발생한다() {
        // given
        Member member = Member.of("username", "password", "nickname");

        // when & then
        assertThatThrownBy(() -> Message.of("안녕하세요", member, null))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
    }
}
