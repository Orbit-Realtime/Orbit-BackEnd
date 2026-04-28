package com.chat.repository;

import com.chat.entity.Chat;
import com.chat.entity.ChatRead;
import com.chat.entity.ChatRoom;
import com.chat.entity.Member;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.dtos.MemberUnreadCount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class ChatReadRepositoryTest {

    @Autowired
    private ChatReadRepository chatReadRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private TestDataFixture fixture;

    @Test
    @DisplayName("채팅읽음 정보를 저장한다.")
    void saveTest() {
        // given
        String username = "username";
        Member savedMember = createMember(username);

        String title = "title";
        ChatRoom savedChatRoom = createChatRoom(title);

        String message = "message";
        Chat savedChat = createChat(message, savedMember, savedChatRoom);

        boolean isRead = true;
        ChatRead chatRead = new ChatRead(isRead, savedMember, savedChat);

        // when
        ChatRead savedChatRead = chatReadRepository.save(chatRead);

        // then
        assertThat(savedChatRead.getIsRead()).isTrue();
        assertThat(savedChatRead.getId()).isNotNull();
        assertThat(savedChatRead.getMember()).isEqualTo(savedMember);
        assertThat(savedChatRead.getChat()).isEqualTo(savedChat);
    }
    
    @Test
    @DisplayName("채팅 ID 와 사용자 ID 를 이용해 채팅읽음 정보를 조회한다.")
    void findByChatIdAndMemberIdTest() {
        // given
        String username = "username";
        Member member = createMember(username);

        String title = "title";
        ChatRoom chatRoom = createChatRoom(title);

        String message = "message";
        Chat chat = createChat(message, member, chatRoom);

        boolean isRead = true;
        ChatRead chatRead = new ChatRead(isRead, member, chat);
        ChatRead savedChatRead = chatReadRepository.save(chatRead);

        // when
        ChatRead findChatRead
                = chatReadRepository.findBy(chat.getId(), member.getId());

        // then
        assertThat(findChatRead).isEqualTo(savedChatRead);
    }

    @Test
    @DisplayName("Bulk Update 로 채팅방의 읽지 않은 메시지를 읽음 처리한다")
    void updateUnreadChatReadsToReadTest() {
        // given
        Member firstMember = fixture.savedMemberBy("firstMember");
        Member secondMember = fixture.savedMemberBy("secondMember");
        Member thirdMember = fixture.savedMemberBy("thirdMember");

        ChatRoom chatRoom = fixture.savedSimpleChatRoom("title");

        Chat firstChat = fixture.savedSimpleChat("message", firstMember, chatRoom);
        chatReadRepository.save(new ChatRead(true, firstMember, firstChat));
        chatReadRepository.save(new ChatRead(true, secondMember, firstChat));
        chatReadRepository.save(new ChatRead(false, thirdMember, firstChat));

        Chat secondChat = fixture.savedSimpleChat("message", firstMember, chatRoom);
        chatReadRepository.save(new ChatRead(true, firstMember, secondChat));
        chatReadRepository.save(new ChatRead(true, secondMember, secondChat));
        chatReadRepository.save(new ChatRead(false, thirdMember, secondChat));

        Chat thirdChat = fixture.savedSimpleChat("message", secondMember, chatRoom);
        chatReadRepository.save(new ChatRead(true, firstMember, thirdChat));
        chatReadRepository.save(new ChatRead(true, secondMember, thirdChat));
        chatReadRepository.save(new ChatRead(false, thirdMember, thirdChat));

        Chat fourthChat = fixture.savedSimpleChat("message", secondMember, chatRoom);
        chatReadRepository.save(new ChatRead(false, firstMember, fourthChat));
        chatReadRepository.save(new ChatRead(true, secondMember, fourthChat));
        chatReadRepository.save(new ChatRead(false, thirdMember, fourthChat));

        // when
        chatReadRepository.updateUnreadChatReadsToRead(thirdMember.getId(), chatRoom.getId());

        // then
        List<MemberUnreadCount> unReadCounts = chatReadRepository
                .findUnReadCountsBy(chatRoom.getId(), List.of(thirdMember.getId()));
        assertThat(unReadCounts).isEmpty();
    }
    
    @Test
    @DisplayName("채팅방 멤버별 미읽음 수를 한 번에 일괄 조회한다.")
    void findUnReadCountsByMembersTest() {
        // given
        Member first = createMember("first");
        Member second = createMember("second");
        Member third = createMember("third");

        ChatRoom chatRoom = createChatRoom("title");

        Chat firstChat = createChat("msg1", first, chatRoom);
        Chat secondChat = createChat("msg2", first, chatRoom);

        // first: 모두 읽음
        chatReadRepository.save(new ChatRead(true, first, firstChat));
        chatReadRepository.save(new ChatRead(true, first, secondChat));

        // second: 2개 미읽음
        chatReadRepository.save(new ChatRead(false, second, firstChat));
        chatReadRepository.save(new ChatRead(false, second, secondChat));

        // third: 1개 미읽음
        chatReadRepository.save(new ChatRead(true, third, firstChat));
        chatReadRepository.save(new ChatRead(false, third, secondChat));

        // when
        List<MemberUnreadCount> result = chatReadRepository
                .findUnReadCountsBy(chatRoom.getId(), List.of(first.getId(), second.getId(), third.getId()));

        // then
        Map<Long, Long> unreadMap = result.stream()
                .collect(Collectors.toMap(MemberUnreadCount::getMemberId, MemberUnreadCount::getUnreadMemberCount));

        // 미읽음 없는 first는 결과에 포함되지 않음
        assertThat(unreadMap).doesNotContainKey(first.getId());
        assertThat(unreadMap.get(second.getId())).isEqualTo(2L);
        assertThat(unreadMap.get(third.getId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("미읽음 메시지가 없는 멤버는 일괄 조회 결과에 포함되지 않는다.")
    void findUnReadCountsBy_allReadMemberNotIncludedTest() {
        // given
        Member first = createMember("first");
        Member second = createMember("second");

        ChatRoom chatRoom = createChatRoom("title");
        Chat chat = createChat("msg", first, chatRoom);

        chatReadRepository.save(new ChatRead(true, first, chat));
        chatReadRepository.save(new ChatRead(true, second, chat));

        // when
        List<MemberUnreadCount> result = chatReadRepository
                .findUnReadCountsBy(chatRoom.getId(), List.of(first.getId(), second.getId()));

        // then
        assertThat(result).isEmpty();
    }

    private Member createMember(String username) {
        String commonPassword = "password";
        Member member = Member.of(username, commonPassword, username);
        return memberRepository.save(member);
    }
    
    private ChatRoom createChatRoom(String title) {
        ChatRoom chatRoom = ChatRoom.of(title);
        return chatRoomRepository.save(chatRoom);
    }
    
    private Chat createChat(String message, Member member, ChatRoom chatRoom) {
        Chat chat = new Chat(message, member, chatRoom);
        return chatRepository.save(chat);
    }
}