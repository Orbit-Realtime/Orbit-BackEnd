package com.chat.entity;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import org.assertj.core.api.AbstractObjectAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscussionTest {

    @Test
    @DisplayName("Discussion 엔티티를 생성한다.")
    void createDiscussionTest() {
        // given
        Member member = Member.of("username", "password", "nickname");
        Space space = Space.of("개발팀");
        Message rootMessage = Message.of("안녕하세요", member, space);

        // when
        Discussion discussion = Discussion.of(rootMessage);

        // then
        assertThat(discussion.getRootMessage()).isEqualTo(rootMessage);
    }

    @Test
    @DisplayName("rootMessage 가 없을 시 Discussion 엔티티를 생성하면 CustomException 이 발생한다.")
    void nullRootMessageCreateDiscussionFailTest() {
        // when
        AbstractObjectAssert<?, CustomException> extracting = assertThatThrownBy(
                () -> Discussion.of(null))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> (CustomException) ex);

        // then
        extracting.satisfies(ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MESSAGE_NOT_FOUND);
        });
    }
}
