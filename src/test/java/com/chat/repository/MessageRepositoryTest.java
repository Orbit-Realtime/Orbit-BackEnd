package com.chat.repository;

import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.entity.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class MessageRepositoryTest {

    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private SpaceRepository spaceRepository;
    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("채팅 정보를 저장한다.")
    void saveTest() {
        // given
        String username = "username";
        Member savedMember = createMember(username);

        String title = "title";
        Space chatRoom = Space.of(title);
        Space savedChatRoom = spaceRepository.save(chatRoom);

        String message = "message";
        Message chat = new Message(message, savedMember, savedChatRoom);

        // when
        Message savedChat = messageRepository.save(chat);

        // then
        assertThat(savedChat.getId()).isNotNull();
        assertThat(savedChat.getMessage()).isEqualTo(message);
        assertThat(savedChat.getMember()).isEqualTo(savedMember);
        assertThat(savedChat.getSpace()).isEqualTo(savedChatRoom);
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
        Space chatRoom = createChatRoom(title);

        String firstMessage = "first";
        Message firstChat = new Message(firstMessage, firstMember, chatRoom);
        messageRepository.save(firstChat);

        String secondMessage = "second";
        Message secondChat = new Message(secondMessage, secondMember, chatRoom);
        messageRepository.save(secondChat);

        Pageable limitOne = createLimitOne();

        // when
        List<Message> lastChatArray = messageRepository.findLastChatBy(chatRoom.getId(), limitOne);

        // then
        assertThat(lastChatArray).hasSize(1);
        assertThat(lastChatArray.get(0)).isEqualTo(secondChat);
    }

    @Test
    @DisplayName("여러 채팅방의 마지막 메시지를 일괄 조회한다.")
    void findLastChatsByMultipleRoomsTest() {
        // given
        Member member = createMember("user");

        Space firstRoom = createChatRoom("firstRoom");
        Space secondRoom = createChatRoom("secondRoom");

        messageRepository.save(new Message("first-1", member, firstRoom));
        Message lastOfFirst = messageRepository.save(new Message("first-2", member, firstRoom));

        Message lastOfSecond = messageRepository.save(new Message("second-1", member, secondRoom));

        // when
        List<Message> lastChats = messageRepository
                .findLastChatsBy(List.of(firstRoom.getId(), secondRoom.getId()));

        // then
        assertThat(lastChats).hasSize(2);
        assertThat(lastChats)
                .extracting(Message::getId)
                .containsExactlyInAnyOrder(lastOfFirst.getId(), lastOfSecond.getId());
    }

    @Test
    @DisplayName("메시지가 없는 채팅방은 마지막 메시지 일괄 조회 결과에 포함되지 않는다.")
    void findLastChatsBy_emptyRoomNotIncludedTest() {
        // given
        Member member = createMember("user");

        Space roomWithChat = createChatRoom("roomWithChat");
        Space emptyRoom = createChatRoom("emptyRoom");

        Message chat = messageRepository.save(new Message("message", member, roomWithChat));

        // when
        List<Message> lastChats = messageRepository
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
        Space chatRoom = createChatRoom("room");

        Message first = messageRepository.save(new Message("first", member, chatRoom));
        Message second = messageRepository.save(new Message("second", member, chatRoom));
        Message third = messageRepository.save(new Message("third", member, chatRoom));

        Pageable limit2 = PageRequest.of(0, 2);

        // when
        List<Message> result = messageRepository.findLatestChats(chatRoom.getId(), limit2);

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
        Space targetRoom = createChatRoom("target");
        Space otherRoom = createChatRoom("other");

        Message targetChat = messageRepository.save(new Message("target message", member, targetRoom));
        messageRepository.save(new Message("other message", member, otherRoom));

        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Message> result = messageRepository.findLatestChats(targetRoom.getId(), limit10);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(targetChat);
    }

    @Test
    @DisplayName("메시지가 없는 채팅방의 최신 메시지 조회는 빈 리스트를 반환한다.")
    void findLatestChats_emptyRoom_returnsEmptyListTest() {
        // given
        Space emptyRoom = createChatRoom("empty");
        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Message> result = messageRepository.findLatestChats(emptyRoom.getId(), limit10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("커서 id보다 작은 메시지를 id 내림차순으로 조회한다.")
    void findChatsBeforeIdTest() {
        // given
        Member member = createMember("user");
        Space chatRoom = createChatRoom("room");

        Message first = messageRepository.save(new Message("first", member, chatRoom));
        Message second = messageRepository.save(new Message("second", member, chatRoom));
        Message third = messageRepository.save(new Message("third", member, chatRoom));

        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Message> result = messageRepository.findChatsBeforeId(chatRoom.getId(), third.getId(), limit10);

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
        Space chatRoom = createChatRoom("room");

        Message firstChat = messageRepository.save(new Message("only message", member, chatRoom));

        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Message> result = messageRepository.findChatsBeforeId(chatRoom.getId(), firstChat.getId(), limit10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("커서 기반 조회는 Pageable size 만큼만 반환한다.")
    void findChatsBeforeId_respectsPageableLimitTest() {
        // given
        Member member = createMember("user");
        Space chatRoom = createChatRoom("room");

        Message first = messageRepository.save(new Message("first", member, chatRoom));
        Message second = messageRepository.save(new Message("second", member, chatRoom));
        Message third = messageRepository.save(new Message("third", member, chatRoom));
        Message fourth = messageRepository.save(new Message("fourth", member, chatRoom));
        Message fifth = messageRepository.save(new Message("fifth", member, chatRoom));

        Pageable limit2 = PageRequest.of(0, 2);

        // when
        List<Message> result = messageRepository.findChatsBeforeId(chatRoom.getId(), fifth.getId(), limit2);

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
        Space targetRoom = createChatRoom("target");
        Space otherRoom = createChatRoom("other");

        messageRepository.save(new Message("other message", member, otherRoom));
        Message targetFirst = messageRepository.save(new Message("target first", member, targetRoom));
        Message targetSecond = messageRepository.save(new Message("target second", member, targetRoom));

        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Message> result = messageRepository.findChatsBeforeId(
                targetRoom.getId(), targetSecond.getId(), limit10);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(targetFirst);
    }

    @Test
    @DisplayName("메시지가 있는 채팅방의 최신 chatId를 반환한다.")
    void findLastChatIdBy_returnsLatestChatIdTest() {
        // given
        Member member = createMember("user");
        Space chatRoom = createChatRoom("room");

        messageRepository.save(new Message("first", member, chatRoom));
        Message latest = messageRepository.save(new Message("second", member, chatRoom));

        // when
        Optional<Long> result = messageRepository.findLastChatIdBy(chatRoom.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(latest.getId());
    }

    @Test
    @DisplayName("메시지가 없는 채팅방은 Optional.empty를 반환한다.")
    void findLastChatIdBy_emptyRoom_returnsEmptyTest() {
        // given
        Space emptyRoom = createChatRoom("empty");

        // when
        Optional<Long> result = messageRepository.findLastChatIdBy(emptyRoom.getId());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("다른 채팅방의 메시지는 findLastChatIdBy 결과에 포함되지 않는다.")
    void findLastChatIdBy_doesNotIncludeOtherRoomsTest() {
        // given
        Member member = createMember("user");
        Space targetRoom = createChatRoom("target");
        Space otherRoom = createChatRoom("other");

        Message targetChat = messageRepository.save(new Message("target", member, targetRoom));
        messageRepository.save(new Message("other", member, otherRoom));
        messageRepository.save(new Message("other2", member, otherRoom));

        // when
        Optional<Long> result = messageRepository.findLastChatIdBy(targetRoom.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(targetChat.getId());
    }

    private Member createMember(String username) {
        String commonPassword = "password";
        Member member = Member.of(username, commonPassword, username);
        return memberRepository.save(member);
    }

    private Space createChatRoom(String title) {
        Space chatRoom = Space.of(title);
        return spaceRepository.save(chatRoom);
    }

    private Pageable createLimitOne() {
        return PageRequest.of(0, 1);
    }
}
