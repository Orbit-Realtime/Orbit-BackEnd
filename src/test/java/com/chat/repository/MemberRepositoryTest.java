package com.chat.repository;

import com.chat.entity.Space;
import com.chat.entity.SpaceMember;
import com.chat.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class MemberRepositoryTest {
    
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private SpaceRepository spaceRepository;
    @Autowired
    private SpaceMemberRepository spaceMemberRepository;
    
    @Test
    @DisplayName("등록된 username은 existsByUsername이 true를 반환한다.")
    void 등록된_username은_existsByUsername이_true를_반환한다() {
        // given
        String existUsername = "existUsername";
        createMemberBy(existUsername);

        // when
        boolean isExist = memberRepository.existsByUsername(existUsername);

        // then
        assertThat(isExist).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 username은 existsByUsername이 false를 반환한다.")
    void 존재하지_않는_username은_existsByUsername이_false를_반환한다() {
        // when
        boolean result = memberRepository.existsByUsername("ghost");

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("등록된 username으로 조회하면 해당 Member를 반환한다.")
    void 등록된_username으로_조회하면_해당_Member를_반환한다() {
        // given
        String username = "username";
        Member member = createMemberBy(username);

        // when
        Optional<Member> findMemberOptional = memberRepository.findByUsername(username);

        // then
        assertThat(findMemberOptional).isNotEmpty();
        assertThat(findMemberOptional.get()).isEqualTo(member);
        assertThat(findMemberOptional.get().getId()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 username으로 조회하면 Optional.empty를 반환한다.")
    void 존재하지_않는_username으로_조회하면_Optional_empty를_반환한다() {
        // when
        Optional<Member> result = memberRepository.findByUsername("ghost");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("spaceId로 참여한 전체 memberId 목록을 조회한다.")
    void spaceId로_참여한_전체_memberId_목록을_조회한다() {
        // given
        Member firstMember = createMemberBy("first");
        Member secondMember = createMemberBy("second");
        Member thirdMember = createMemberBy("third");

        Space savedChatRoom = spaceRepository.save(Space.of("title"));

        spaceMemberRepository.save(SpaceMember.of(firstMember, savedChatRoom));
        spaceMemberRepository.save(SpaceMember.of(secondMember, savedChatRoom));
        spaceMemberRepository.save(SpaceMember.of(thirdMember, savedChatRoom));

        // when
        List<Long> memberIds = memberRepository.findMemberIdsIn(savedChatRoom.getId());

        // then
        assertThat(memberIds)
                .hasSize(3)
                .containsExactlyInAnyOrder(firstMember.getId(), secondMember.getId(), thirdMember.getId());
    }

    private Member createMemberBy(String username) {
        String commonPassword = "password";
        Member member = Member.of(username, commonPassword, username);
        return memberRepository.save(member);
    }
}