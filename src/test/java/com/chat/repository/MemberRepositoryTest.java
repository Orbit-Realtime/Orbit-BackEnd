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
    @DisplayName("사용자 ID 가 존재하는지 조회한다.")
    void existsByUsernameTest() {
        // given
        String existUsername = "existUsername";
        Member member = createMemberBy(existUsername);

        // when
        boolean isExist = memberRepository.existsByUsername(existUsername);

        // then
        assertThat(isExist).isTrue();
    }

    @Test
    @DisplayName("사용자 ID 를 이용해 사용자 정보를 조회한다.")
    void findByUsernameTest() {
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
    @DisplayName("채팅방 ID 를 이용해 참여한 사용자 ID 를 조회한다.")
    void findMemberIdsInTest() {
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