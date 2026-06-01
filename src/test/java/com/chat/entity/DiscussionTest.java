package com.chat.entity;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscussionTest {

    @Test
    @DisplayName("rootMessage가 null이면 Discussion 생성 시 MESSAGE_NOT_FOUND 예외가 발생한다.")
    void rootMessage가_null이면_Discussion_생성_시_MESSAGE_NOT_FOUND_예외가_발생한다() {
        assertThatThrownBy(() -> Discussion.of(null))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.MESSAGE_NOT_FOUND);
    }
}
