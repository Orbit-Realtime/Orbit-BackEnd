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
    @DisplayName("채팅방 ID 를 이용해 마지막 채팅 정보를 조회한다.")
    void findLastMessageByTest() {
        // given
        String firstUser = "first";
        Member firstMember = createMember(firstUser);
        String secondUser = "second";
        Member secondMember = createMember(secondUser);

        String title = "title";
        Space chatRoom = createSpaceBy(title);

        String firstMessage = "first";
        Message firstChat = Message.of(firstMessage, firstMember, chatRoom);
        messageRepository.save(firstChat);

        String secondMessage = "second";
        Message secondChat = Message.of(secondMessage, secondMember, chatRoom);
        messageRepository.save(secondChat);

        Pageable limitOne = createLimitOne();

        // when
        List<Message> lastChatArray = messageRepository.findLastMessageBy(chatRoom.getId(), limitOne);

        // then
        assertThat(lastChatArray).hasSize(1);
        assertThat(lastChatArray.get(0)).isEqualTo(secondChat);
    }

    @Test
    @DisplayName("여러 채팅방의 마지막 메시지를 일괄 조회한다.")
    void findLastMessagesByMultipleRoomsTest() {
        // given
        Member member = createMember("user");

        Space firstRoom = createSpaceBy("firstRoom");
        Space secondRoom = createSpaceBy("secondRoom");

        messageRepository.save(Message.of("first-1", member, firstRoom));
        Message lastOfFirst = messageRepository.save(Message.of("first-2", member, firstRoom));

        Message lastOfSecond = messageRepository.save(Message.of("second-1", member, secondRoom));

        // when
        List<Message> lastChats = messageRepository
                .findLastMessagesBy(List.of(firstRoom.getId(), secondRoom.getId()));

        // then
        assertThat(lastChats).hasSize(2);
        assertThat(lastChats)
                .extracting(Message::getId)
                .containsExactlyInAnyOrder(lastOfFirst.getId(), lastOfSecond.getId());
    }

    @Test
    @DisplayName("메시지가 없는 채팅방은 마지막 메시지 일괄 조회 결과에 포함되지 않는다.")
    void findLastMessagesBy_emptyRoomNotIncludedTest() {
        // given
        Member member = createMember("user");

        Space roomWithChat = createSpaceBy("roomWithChat");
        Space emptyRoom = createSpaceBy("emptyRoom");

        Message chat = messageRepository.save(Message.of("message", member, roomWithChat));

        // when
        List<Message> lastChats = messageRepository
                .findLastMessagesBy(List.of(roomWithChat.getId(), emptyRoom.getId()));

        // then
        assertThat(lastChats).hasSize(1);
        assertThat(lastChats.get(0).getId()).isEqualTo(chat.getId());
    }

    @Test
    @DisplayName("채팅방 ID로 최신 메시지를 id 내림차순으로 조회한다.")
    void findLatestMessagesTest() {
        // given
        Member member = createMember("user");
        Space chatRoom = createSpaceBy("room");

        Message first = messageRepository.save(Message.of("first", member, chatRoom));
        Message second = messageRepository.save(Message.of("second", member, chatRoom));
        Message third = messageRepository.save(Message.of("third", member, chatRoom));

        Pageable limit2 = PageRequest.of(0, 2);

        // when
        List<Message> result = messageRepository.findLatestMessages(chatRoom.getId(), limit2);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(third);
        assertThat(result.get(1)).isEqualTo(second);
    }

    @Test
    @DisplayName("최신 메시지 조회 시 다른 채팅방의 메시지는 포함되지 않는다.")
    void findLatestMessages_doesNotIncludeOtherRoomsTest() {
        // given
        Member member = createMember("user");
        Space targetRoom = createSpaceBy("target");
        Space otherRoom = createSpaceBy("other");

        Message targetChat = messageRepository.save(Message.of("target message", member, targetRoom));
        messageRepository.save(Message.of("other message", member, otherRoom));

        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Message> result = messageRepository.findLatestMessages(targetRoom.getId(), limit10);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(targetChat);
    }

    @Test
    @DisplayName("메시지가 없는 채팅방의 최신 메시지 조회는 빈 리스트를 반환한다.")
    void findLatestMessages_emptyRoom_returnsEmptyListTest() {
        // given
        Space emptyRoom = createSpaceBy("empty");
        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Message> result = messageRepository.findLatestMessages(emptyRoom.getId(), limit10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("커서 id보다 작은 메시지를 id 내림차순으로 조회한다.")
    void findMessagesBeforeIdTest() {
        // given
        Member member = createMember("user");
        Space chatRoom = createSpaceBy("room");

        Message first = messageRepository.save(Message.of("first", member, chatRoom));
        Message second = messageRepository.save(Message.of("second", member, chatRoom));
        Message third = messageRepository.save(Message.of("third", member, chatRoom));

        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Message> result = messageRepository.findMessagesBeforeId(chatRoom.getId(), third.getId(), limit10);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(second);
        assertThat(result.get(1)).isEqualTo(first);
    }

    @Test
    @DisplayName("커서 id보다 오래된 메시지가 없으면 빈 리스트를 반환한다.")
    void findMessagesBeforeId_noPreviousMessages_returnsEmptyTest() {
        // given
        Member member = createMember("user");
        Space chatRoom = createSpaceBy("room");

        Message firstChat = messageRepository.save(Message.of("only message", member, chatRoom));

        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Message> result = messageRepository.findMessagesBeforeId(chatRoom.getId(), firstChat.getId(), limit10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("커서 기반 조회는 Pageable size 만큼만 반환한다.")
    void findMessagesBeforeId_respectsPageableLimitTest() {
        // given
        Member member = createMember("user");
        Space chatRoom = createSpaceBy("room");

        Message first = messageRepository.save(Message.of("first", member, chatRoom));
        Message second = messageRepository.save(Message.of("second", member, chatRoom));
        Message third = messageRepository.save(Message.of("third", member, chatRoom));
        Message fourth = messageRepository.save(Message.of("fourth", member, chatRoom));
        Message fifth = messageRepository.save(Message.of("fifth", member, chatRoom));

        Pageable limit2 = PageRequest.of(0, 2);

        // when
        List<Message> result = messageRepository.findMessagesBeforeId(chatRoom.getId(), fifth.getId(), limit2);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(fourth);
        assertThat(result.get(1)).isEqualTo(third);
    }

    @Test
    @DisplayName("커서 기반 조회 시 다른 채팅방의 메시지는 포함되지 않는다.")
    void findMessagesBeforeId_doesNotIncludeOtherRoomsTest() {
        // given
        Member member = createMember("user");
        Space targetRoom = createSpaceBy("target");
        Space otherRoom = createSpaceBy("other");

        messageRepository.save(Message.of("other message", member, otherRoom));
        Message targetFirst = messageRepository.save(Message.of("target first", member, targetRoom));
        Message targetSecond = messageRepository.save(Message.of("target second", member, targetRoom));

        Pageable limit10 = PageRequest.of(0, 10);

        // when
        List<Message> result = messageRepository.findMessagesBeforeId(
                targetRoom.getId(), targetSecond.getId(), limit10);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(targetFirst);
    }

    @Test
    @DisplayName("메시지가 있는 채팅방의 최신 chatId를 반환한다.")
    void findLastMessageIdBy_returnsLatestChatIdTest() {
        // given
        Member member = createMember("user");
        Space chatRoom = createSpaceBy("room");

        messageRepository.save(Message.of("first", member, chatRoom));
        Message latest = messageRepository.save(Message.of("second", member, chatRoom));

        // when
        Optional<Long> result = messageRepository.findLastMessageIdBy(chatRoom.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(latest.getId());
    }

    @Test
    @DisplayName("메시지가 없는 채팅방은 Optional.empty를 반환한다.")
    void findLastMessageIdBy_emptyRoom_returnsEmptyTest() {
        // given
        Space emptyRoom = createSpaceBy("empty");

        // when
        Optional<Long> result = messageRepository.findLastMessageIdBy(emptyRoom.getId());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("다른 채팅방의 메시지는 findLastMessageIdBy 결과에 포함되지 않는다.")
    void findLastMessageIdBy_doesNotIncludeOtherRoomsTest() {
        // given
        Member member = createMember("user");
        Space targetRoom = createSpaceBy("target");
        Space otherRoom = createSpaceBy("other");

        Message targetChat = messageRepository.save(Message.of("target", member, targetRoom));
        messageRepository.save(Message.of("other", member, otherRoom));
        messageRepository.save(Message.of("other2", member, otherRoom));

        // when
        Optional<Long> result = messageRepository.findLastMessageIdBy(targetRoom.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(targetChat.getId());
    }

    private Member createMember(String username) {
        String commonPassword = "password";
        Member member = Member.of(username, commonPassword, username);
        return memberRepository.save(member);
    }

    private Space createSpaceBy(String title) {
        Space chatRoom = Space.of(title);
        return spaceRepository.save(chatRoom);
    }

    private Pageable createLimitOne() {
        return PageRequest.of(0, 1);
    }
}
