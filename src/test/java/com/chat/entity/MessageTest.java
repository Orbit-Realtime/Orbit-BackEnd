package com.chat.entity;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import org.assertj.core.api.AbstractObjectAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageTest {

    @Test
    @DisplayName("Message 엔티티를 생성한다.")
    void createMessageTest() {
        // given
        Member member = Member.of("username", "password", "nickname");
        Space space = Space.of("개발팀");
        String content = "안녕하세요";

        // when
        Message message = Message.of(content, member, space);

        // then
        assertThat(message.getContent()).isEqualTo(content);
        assertThat(message.getMember()).isEqualTo(member);
        assertThat(message.getSpace()).isEqualTo(space);
    }

    @Test
    @DisplayName("content 가 없을 시 Message 엔티티를 생성하면 CustomException 이 발생한다.")
    void nullContentCreateMessageFailTest() {
        // given
        Member member = Member.of("username", "password", "nickname");
        Space space = Space.of("개발팀");

        // when
        AbstractObjectAssert<?, CustomException> extracting = assertThatThrownBy(
                () -> Message.of(null, member, space))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> (CustomException) ex);

        // then
        extracting.satisfies(ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EMPTY_MESSAGE_CONTENT);
        });
    }

    @Test
    @DisplayName("content 가 공백이면 Message 엔티티를 생성하면 CustomException 이 발생한다.")
    void blankContentCreateMessageFailTest() {
        // given
        Member member = Member.of("username", "password", "nickname");
        Space space = Space.of("개발팀");
        String content = "  ";

        // when
        AbstractObjectAssert<?, CustomException> extracting = assertThatThrownBy(
                () -> Message.of(content, member, space))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> (CustomException) ex);

        // then
        extracting.satisfies(ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EMPTY_MESSAGE_CONTENT);
        });
    }

    @Test
    @DisplayName("member 가 없을 시 Message 엔티티를 생성하면 CustomException 이 발생한다.")
    void nullMemberCreateMessageFailTest() {
        // given
        Space space = Space.of("개발팀");

        // when
        AbstractObjectAssert<?, CustomException> extracting = assertThatThrownBy(
                () -> Message.of("안녕하세요", null, space))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> (CustomException) ex);

        // then
        extracting.satisfies(ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        });
    }

    @Test
    @DisplayName("space 가 없을 시 Message 엔티티를 생성하면 CustomException 이 발생한다.")
    void nullSpaceCreateMessageFailTest() {
        // given
        Member member = Member.of("username", "password", "nickname");

        // when
        AbstractObjectAssert<?, CustomException> extracting = assertThatThrownBy(
                () -> Message.of("안녕하세요", member, null))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> (CustomException) ex);

        // then
        extracting.satisfies(ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SPACE_NOT_FOUND);
        });
    }
}
