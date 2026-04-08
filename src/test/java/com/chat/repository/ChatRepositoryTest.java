package com.chat.repository;

import com.chat.entity.Chat;
import com.chat.entity.ChatRoom;
import com.chat.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class ChatRepositoryTest {

    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("채팅 정보를 저장한다.")
    void saveTest() {
        // given
        String username = "username";
        Member savedMember = createMember(username);

        String title = "title";
        ChatRoom chatRoom = ChatRoom.of(title);
        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);

        String message = "message";
        Chat chat = new Chat(message, savedMember, savedChatRoom);

        // when
        Chat savedChat = chatRepository.save(chat);

        // then
        assertThat(savedChat.getId()).isNotNull();
        assertThat(savedChat.getMessage()).isEqualTo(message);
        assertThat(savedChat.getMember()).isEqualTo(savedMember);
        assertThat(savedChat.getChatRoom()).isEqualTo(savedChatRoom);
    }

    @Test
    @DisplayName("채팅방 ID 를 이용해 마지막 채팅 정보를 조회한다.")
    void findLastChatByTest() {
        // given
        String firstUser = "first";
        Member firstMember = createMember(firstUser);
        String secondUser = "second";
        Member secondMember = createMember(secondUser);

        String title = "title";
        ChatRoom chatRoom = createChatRoom(title);

        String firstMessage = "first";
        Chat firstChat = new Chat(firstMessage, firstMember, chatRoom);
        chatRepository.save(firstChat);

        String secondMessage = "second";
        Chat secondChat = new Chat(secondMessage, secondMember, chatRoom);
        chatRepository.save(secondChat);

        Pageable limitOne = createLimitOne();

        // when
        List<Chat> lastChatArray = chatRepository.findLastChatBy(chatRoom.getId(), limitOne);

        // then
        assertThat(lastChatArray).hasSize(1);
        assertThat(lastChatArray.get(0)).isEqualTo(secondChat);
    }

    @Test
    @DisplayName("채팅방 ID 를 이용해 채팅 정보를 조회힌다.")
    void findChatHistoryTest() {
        // given
        String firstUser = "first";
        Member firstMember = createMember(firstUser);
        String secondUser = "second";
        Member secondMember = createMember(secondUser);

        String title = "title";
        ChatRoom chatRoom = createChatRoom(title);

        String firstMessage = "first";
        Chat firstChat = new Chat(firstMessage, firstMember, chatRoom);
        chatRepository.save(firstChat);

        String secondMessage = "second";
        Chat secondChat = new Chat(secondMessage, secondMember, chatRoom);
        chatRepository.save(secondChat);

        // when
        List<Chat> chatHistory = chatRepository.findChatHistory(chatRoom.getId());

        // then
        assertThat(chatHistory).hasSize(2);
        assertThat(chatHistory.get(0)).isEqualTo(firstChat);
        assertThat(chatHistory.get(1)).isEqualTo(secondChat);
    }

    @Test
    @DisplayName("여러 채팅방의 마지막 메시지를 일괄 조회한다.")
    void findLastChatsByMultipleRoomsTest() {
        // given
        Member member = createMember("user");

        ChatRoom firstRoom = createChatRoom("firstRoom");
        ChatRoom secondRoom = createChatRoom("secondRoom");

        chatRepository.save(new Chat("first-1", member, firstRoom));
        Chat lastOfFirst = chatRepository.save(new Chat("first-2", member, firstRoom));

        Chat lastOfSecond = chatRepository.save(new Chat("second-1", member, secondRoom));

        // when
        List<Chat> lastChats = chatRepository
                .findLastChatsBy(List.of(firstRoom.getId(), secondRoom.getId()));

        // then
        assertThat(lastChats).hasSize(2);
        assertThat(lastChats)
                .extracting(Chat::getId)
                .containsExactlyInAnyOrder(lastOfFirst.getId(), lastOfSecond.getId());
    }

    @Test
    @DisplayName("메시지가 없는 채팅방은 마지막 메시지 일괄 조회 결과에 포함되지 않는다.")
    void findLastChatsBy_emptyRoomNotIncludedTest() {
        // given
        Member member = createMember("user");

        ChatRoom roomWithChat = createChatRoom("roomWithChat");
        ChatRoom emptyRoom = createChatRoom("emptyRoom");

        Chat chat = chatRepository.save(new Chat("message", member, roomWithChat));

        // when
        List<Chat> lastChats = chatRepository
                .findLastChatsBy(List.of(roomWithChat.getId(), emptyRoom.getId()));

        // then
        assertThat(lastChats).hasSize(1);
        assertThat(lastChats.get(0).getId()).isEqualTo(chat.getId());
    }

    @Test
    @DisplayName("채팅방 ID로 최신 메시지를 id 내림차순으로 조회한다.")
    void findLatestChatsTest() {
        // given
        Member member = createMember("user");
        ChatRoom chatRoom = createChatRoom("room");

        Chat first = chatRepository.save(new Chat("first", member, chatRoom));
        Chat second = chatRepository.save(new Chat("second", member, chatRoom));
        Chat third = chatRepository.save(new Chat("third", member, chatRoom));

        Pageable limit2 = PageRequest.of(0, 2);

        // when
        List<Chat> result = chatRepository.findLatestChats(chatRoom.getId(), limit2);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(third);
        assertThat(result.get(1)).isEqualTo(second);
    }

    @Test
    @DisplayName("최신 메시지 조회 시 다른 채팅방의 메시지는 포함되지 않는다.")
    void findLatestChats_doesNotIncludeOtherRoomsTest() {
        // given
        Member member = createMember("user");
        ChatRoom targetRoom = createChatRoom("target");
        ChatRoom otherRoom = createChatRoom("other");

        Chat targetChat = chatRepository.save(new Chat("target message", member, targetRoom));
        chatRepository.save(new Chat("other message", member, otherRoom));

        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Chat> result = chatRepository.findLatestChats(targetRoom.getId(), limit10);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(targetChat);
    }

    @Test
    @DisplayName("메시지가 없는 채팅방의 최신 메시지 조회는 빈 리스트를 반환한다.")
    void findLatestChats_emptyRoom_returnsEmptyListTest() {
        // given
        ChatRoom emptyRoom = createChatRoom("empty");
        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Chat> result = chatRepository.findLatestChats(emptyRoom.getId(), limit10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("커서 id보다 작은 메시지를 id 내림차순으로 조회한다.")
    void findChatsBeforeIdTest() {
        // given
        Member member = createMember("user");
        ChatRoom chatRoom = createChatRoom("room");

        Chat first = chatRepository.save(new Chat("first", member, chatRoom));
        Chat second = chatRepository.save(new Chat("second", member, chatRoom));
        Chat third = chatRepository.save(new Chat("third", member, chatRoom));

        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Chat> result = chatRepository.findChatsBeforeId(chatRoom.getId(), third.getId(), limit10);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(second);
        assertThat(result.get(1)).isEqualTo(first);
    }

    @Test
    @DisplayName("커서 id보다 오래된 메시지가 없으면 빈 리스트를 반환한다.")
    void findChatsBeforeId_noPreviousMessages_returnsEmptyTest() {
        // given
        Member member = createMember("user");
        ChatRoom chatRoom = createChatRoom("room");

        Chat firstChat = chatRepository.save(new Chat("only message", member, chatRoom));

        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Chat> result = chatRepository.findChatsBeforeId(chatRoom.getId(), firstChat.getId(), limit10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("커서 기반 조회는 Pageable size 만큼만 반환한다.")
    void findChatsBeforeId_respectsPageableLimitTest() {
        // given
        Member member = createMember("user");
        ChatRoom chatRoom = createChatRoom("room");

        Chat first = chatRepository.save(new Chat("first", member, chatRoom));
        Chat second = chatRepository.save(new Chat("second", member, chatRoom));
        Chat third = chatRepository.save(new Chat("third", member, chatRoom));
        Chat fourth = chatRepository.save(new Chat("fourth", member, chatRoom));
        Chat fifth = chatRepository.save(new Chat("fifth", member, chatRoom));

        Pageable limit2 = PageRequest.of(0, 2);

        // when
        List<Chat> result = chatRepository.findChatsBeforeId(chatRoom.getId(), fifth.getId(), limit2);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(fourth);
        assertThat(result.get(1)).isEqualTo(third);
    }

    @Test
    @DisplayName("커서 기반 조회 시 다른 채팅방의 메시지는 포함되지 않는다.")
    void findChatsBeforeId_doesNotIncludeOtherRoomsTest() {
        // given
        Member member = createMember("user");
        ChatRoom targetRoom = createChatRoom("target");
        ChatRoom otherRoom = createChatRoom("other");

        chatRepository.save(new Chat("other message", member, otherRoom));
        Chat targetFirst = chatRepository.save(new Chat("target first", member, targetRoom));
        Chat targetSecond = chatRepository.save(new Chat("target second", member, targetRoom));

        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Chat> result = chatRepository.findChatsBeforeId(
                targetRoom.getId(), targetSecond.getId(), limit10);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(targetFirst);
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

    private Pageable createLimitOne() {
        return PageRequest.of(0, 1);
    }
}