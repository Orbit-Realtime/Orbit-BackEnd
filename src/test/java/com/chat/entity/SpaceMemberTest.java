package com.chat.entity;

import com.chat.exception.CustomException;
import com.chat.exception.ErrorCode;
import org.assertj.core.api.AbstractObjectAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpaceMemberTest {

    @Test
    @DisplayName("SpaceMember 엔티티를 생성한다.")
    void createSpaceMemberTest() {
        // given
        Member member = Member.of("username", "password", "nickname");
        Space space = Space.of("개발팀");

        // when
        SpaceMember spaceMember = SpaceMember.of(member, space);

        // then
        assertThat(spaceMember.getMember()).isEqualTo(member);
        assertThat(spaceMember.getSpace()).isEqualTo(space);
    }

    @Test
    @DisplayName("member 가 없을 시 SpaceMember 엔티티를 생성하면 CustomException 이 발생한다.")
    void nullMemberCreateSpaceMemberFailTest() {
        // given
        Space space = Space.of("개발팀");

        // when
        AbstractObjectAssert<?, CustomException> extracting = assertThatThrownBy(
                () -> SpaceMember.of(null, space))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> (CustomException) ex);

        // then
        extracting.satisfies(ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        });
    }

    @Test
    @DisplayName("space 가 없을 시 SpaceMember 엔티티를 생성하면 CustomException 이 발생한다.")
    void nullSpaceCreateSpaceMemberFailTest() {
        // given
        Member member = Member.of("username", "password", "nickname");

        // when
        AbstractObjectAssert<?, CustomException> extracting = assertThatThrownBy(
                () -> SpaceMember.of(member, null))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> (CustomException) ex);

        // then
        extracting.satisfies(ex -> {
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SPACE_NOT_FOUND);
        });
    }
}
