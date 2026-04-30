package com.chat.service;

import com.chat.entity.*;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.*;
import com.chat.service.dtos.ChatHistory;
import com.chat.service.dtos.ChatHistoryResponse;
import com.chat.service.dtos.SaveChatData;
import com.chat.socket.event.PublishReadEvent;
import com.chat.socket.manager.ChatRoomManager;
import com.chat.utils.consts.SessionConst;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@Transactional
@SpringBootTest
@RecordApplicationEvents
class ChatServiceTest {

    @Autowired
    private ChatService chatService;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private ChatRoomParticipantRepository chatRoomParticipantRepository;
    @Autowired
    private TestDataFixture fixture;
    @Autowired
    private ChatRoomManager chatRoomManager;
    @Autowired
    private EntityManager em;

    @AfterEach
    void tearDown() {
        chatRoomManager.clearAll();
    }

    @Test
    @DisplayName("채팅 메시지를 저장한다.")
    void saveChatTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver1 = fixture.savedMemberBy("receiver1");
        Member receiver2 = fixture.savedMemberBy("receiver2");

        List<Member> participants = new ArrayList<>();
        participants.add(sender);
        participants.add(receiver1);
        participants.add(receiver2);

        ChatRoom chatRoom = fixture.savedChatRoomBy("title", participants);

        String message = "message";

        // when
        Long savedChatId = chatService.saveChat(sender.getId(), chatRoom.getId(), message);

        // then
        Chat chat = chatRepository.findById(savedChatId).get();
        assertThat(chat.getMessage()).isEqualTo(message);
        assertThat(chat.getChatRoom()).isEqualTo(chatRoom);
        assertThat(chat.getMember()).isEqualTo(sender);
    }

    @Test
    @DisplayName("특정 채팅에 대한 상세정보를 조회한다.")
    void findChatDataTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver1 = fixture.savedMemberBy("receiver1");
        Member receiver2 = fixture.savedMemberBy("receiver2");

        List<Member> participants = new ArrayList<>();
        participants.add(sender);
        participants.add(receiver1);
        participants.add(receiver2);

        ChatRoom chatRoom = fixture.savedChatRoomBy("title", participants);

        String message = "message";

        Long savedChatId = chatService.saveChat(sender.getId(), chatRoom.getId(), message);

        // when
        SaveChatData chatData = chatService.findChatData(savedChatId);

        // then
        assertThat(chatData.getChatId()).isEqualTo(savedChatId);
        assertThat(chatData.getUnreadMemberCount()).isEqualTo(2);
        assertThat(chatData.getCreatedDate()).isNotNull();
    }

    @Test
    @DisplayName("채팅방의 채팅목록을 조회한다.")
    void findChatHistoryTest() {
        // given
        Member firstMember = fixture.savedMemberBy("firstMember");
        Member secondMember = fixture.savedMemberBy("secondMember");
        Member thirdMember = fixture.savedMemberBy("thirdMember");

        List<Member> participants = new ArrayList<>();
        participants.add(firstMember);
        participants.add(secondMember);
        participants.add(thirdMember);

        ChatRoom chatRoom = fixture.savedChatRoomBy("title", participants);
        Long chatRoomId = chatRoom.getId();

        Long firstChatId = chatService.saveChat(firstMember.getId(), chatRoomId, "message");
        chatService.saveChat(secondMember.getId(), chatRoomId, "secondMessage");
        chatService.saveChat(secondMember.getId(), chatRoomId, "thirdMessage");

        // when
        ChatHistoryResponse response = chatService.findChatHistory(chatRoomId, firstMember.getId(), null);

        // then
        List<ChatHistory> messages = response.getMessages();
        assertThat(messages).hasSize(3);

        // lastReadChatId: 조회 전 firstMember가 마지막으로 읽은 메시지 = 자신이 보낸 firstChat
        assertThat(response.getLastReadChatId()).isEqualTo(firstChatId);

        ChatHistory firstChat = messages.get(0);
        assertThat(firstChat.getChatId()).isEqualTo(firstChatId);
        assertThat(firstChat.getSenderId()).isEqualTo(firstMember.getId());
        assertThat(firstChat.getUnreadMemberCount()).isEqualTo(1L);

        // cursor 갱신 이후 count → firstMember 읽음 제외한 값
        ChatHistory secondChat = messages.get(1);
        assertThat(secondChat.getUnreadMemberCount()).isEqualTo(1L);

        ChatHistory thirdChat = messages.get(2);
        assertThat(thirdChat.getUnreadMemberCount()).isEqualTo(1L);

        assertThat(firstChat.getMessage()).isEqualTo("message");
    }

    @Test
    @DisplayName("채팅방에 메시지가 없으면 빈 리스트를 반환한다.")
    void findChatHistoryEmptyTest() {
        // given
        Member firstMember = fixture.savedMemberBy("firstMember");
        Member secondMember = fixture.savedMemberBy("secondMember");

        List<Member> participants = new ArrayList<>();
        participants.add(firstMember);
        participants.add(secondMember);

        ChatRoom chatRoom = fixture.savedChatRoomBy("title", participants);

        // when
        ChatHistoryResponse response = chatService.findChatHistory(chatRoom.getId(), firstMember.getId(), null);

        // then
        assertThat(response.getMessages()).isEmpty();
        assertThat(response.getLastReadChatId()).isNull();
    }

    @Test
    @DisplayName("채팅방 재입장 시 이미 모두 읽은 상태면 미읽음이 남지 않는다.")
    void findChatHistory_재입장시_미읽음이_남지_않는다() {
        // given
        Member firstMember = fixture.savedMemberBy("firstMember");
        Member secondMember = fixture.savedMemberBy("secondMember");

        ChatRoom chatRoom = fixture.savedChatRoomBy("title", List.of(firstMember, secondMember));
        Long chatRoomId = chatRoom.getId();

        chatService.saveChat(firstMember.getId(), chatRoomId, "message");

        // 첫 번째 입장: secondMember의 미읽음 1개 → 읽음 처리
        chatService.findChatHistory(chatRoomId, secondMember.getId(), null);

        em.flush(); em.clear();
        Long cursorAfterFirst = chatRoomParticipantRepository
                .findLastReadChatIdBy(secondMember.getId(), chatRoomId);
        assertThat(cursorAfterFirst).isNotNull();

        // when: 재입장 — 이미 모두 읽음 처리된 상태
        chatService.findChatHistory(chatRoomId, secondMember.getId(), null);

        // then: 재입장 후에도 미읽음 없음
        em.flush(); em.clear();
        Long cursorAfterSecond = chatRoomParticipantRepository
                .findLastReadChatIdBy(secondMember.getId(), chatRoomId);
        assertThat(cursorAfterSecond).isEqualTo(cursorAfterFirst);
    }

    @Test
    @DisplayName("읽지 않은 메시지가 있으면 PublishReadEvent가 발행된다.")
    void findChatHistory_unreadExists_publishesReadEvent(ApplicationEvents events) {
        // given
        Member firstMember = fixture.savedMemberBy("firstMember");
        Member secondMember = fixture.savedMemberBy("secondMember");

        ChatRoom chatRoom = fixture.savedChatRoomBy("title", List.of(firstMember, secondMember));
        Long chatRoomId = chatRoom.getId();

        Long firstChatId = chatService.saveChat(firstMember.getId(), chatRoomId, "message");

        // when: secondMember 입장 — 미읽음 1개 존재
        chatService.findChatHistory(chatRoomId, secondMember.getId(), null);

        // then: PublishReadEvent 1건 발행, 필드값 검증
        List<PublishReadEvent> publishedEvents = events.stream(PublishReadEvent.class).toList();
        assertThat(publishedEvents).hasSize(1);

        PublishReadEvent event = publishedEvents.get(0);
        assertThat(event.getMemberId()).isEqualTo(secondMember.getId());
        assertThat(event.getChatRoomId()).isEqualTo(chatRoomId);
        assertThat(event.getLastReadChatId()).isNull(); // 이전에 읽은 기록 없음
        // 읽음 처리 시 나(secondMember)에게만 UPDATE_CHAT_ROOM을 보내야 함
        assertThat(event.getUpdatesByMemberId()).hasSize(1);
        assertThat(event.getUpdatesByMemberId()).containsKey(secondMember.getId());
        assertThat(event.getUpdatesByMemberId()).doesNotContainKey(firstMember.getId());
    }

    @Test
    @DisplayName("초기 진입 시 메시지가 PAGE_SIZE를 초과하면 hasMore=true를 반환한다.")
    void findChatHistory_initialLoad_hasMoreTrueTest() {
        // given
        Member member = fixture.savedMemberBy("member");
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", List.of(member));
        Long chatRoomId = chatRoom.getId();

        for (int i = 0; i < 31; i++) {
            chatService.saveChat(member.getId(), chatRoomId, "message" + i);
        }

        // when
        ChatHistoryResponse response = chatService.findChatHistory(chatRoomId, member.getId(), null);

        // then
        assertThat(response.getMessages()).hasSize(30);
        assertThat(response.isHasMore()).isTrue();
    }

    @Test
    @DisplayName("초기 진입 시 메시지가 PAGE_SIZE 이하면 hasMore=false를 반환한다.")
    void findChatHistory_initialLoad_hasMoreFalseTest() {
        // given
        Member member = fixture.savedMemberBy("member");
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", List.of(member));
        Long chatRoomId = chatRoom.getId();

        for (int i = 0; i < 5; i++) {
            chatService.saveChat(member.getId(), chatRoomId, "message" + i);
        }

        // when
        ChatHistoryResponse response = chatService.findChatHistory(chatRoomId, member.getId(), null);

        // then
        assertThat(response.getMessages()).hasSize(5);
        assertThat(response.isHasMore()).isFalse();
    }

    @Test
    @DisplayName("초기 진입 시 메시지는 오래된 순으로 반환된다.")
    void findChatHistory_initialLoad_messagesInAscendingOrderTest() {
        // given
        Member member = fixture.savedMemberBy("member");
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", List.of(member));
        Long chatRoomId = chatRoom.getId();

        Long firstChatId = chatService.saveChat(member.getId(), chatRoomId, "first");
        Long secondChatId = chatService.saveChat(member.getId(), chatRoomId, "second");
        Long thirdChatId = chatService.saveChat(member.getId(), chatRoomId, "third");

        // when
        ChatHistoryResponse response = chatService.findChatHistory(chatRoomId, member.getId(), null);

        // then
        List<ChatHistory> messages = response.getMessages();
        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).getChatId()).isEqualTo(firstChatId);
        assertThat(messages.get(1).getChatId()).isEqualTo(secondChatId);
        assertThat(messages.get(2).getChatId()).isEqualTo(thirdChatId);
    }

    @Test
    @DisplayName("커서 기반 조회는 beforeChatId보다 오래된 메시지를 오래된 순으로 반환한다.")
    void findChatHistory_cursorLoad_returnsMessagesBeforeCursorTest() {
        // given
        Member member = fixture.savedMemberBy("member");
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", List.of(member));
        Long chatRoomId = chatRoom.getId();

        Long firstChatId = chatService.saveChat(member.getId(), chatRoomId, "first");
        Long secondChatId = chatService.saveChat(member.getId(), chatRoomId, "second");
        Long thirdChatId = chatService.saveChat(member.getId(), chatRoomId, "third");

        // when: thirdChatId를 커서로 → first, second만 반환
        ChatHistoryResponse response = chatService.findChatHistory(chatRoomId, member.getId(), thirdChatId);

        // then
        List<ChatHistory> messages = response.getMessages();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getChatId()).isEqualTo(firstChatId);
        assertThat(messages.get(1).getChatId()).isEqualTo(secondChatId);
    }

    @Test
    @DisplayName("커서 기반 조회 시 읽음 처리를 수행하지 않는다.")
    void findChatHistory_cursorLoad_doesNotUpdateReadStatusTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver = fixture.savedMemberBy("receiver");
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", List.of(sender, receiver));
        Long chatRoomId = chatRoom.getId();

        chatService.saveChat(sender.getId(), chatRoomId, "first");
        chatService.saveChat(sender.getId(), chatRoomId, "second");
        Long thirdChatId = chatService.saveChat(sender.getId(), chatRoomId, "third");

        // when: receiver가 커서로 이전 메시지 조회
        chatService.findChatHistory(chatRoomId, receiver.getId(), thirdChatId);

        // then: receiver의 미읽음 상태 그대로 (읽음 처리 안 됨)
        em.flush(); em.clear();
        Long receiverCursor = chatRoomParticipantRepository
                .findLastReadChatIdBy(receiver.getId(), chatRoomId);
        assertThat(receiverCursor).isNull();
    }

    @Test
    @DisplayName("커서 기반 조회 시 lastReadChatId는 null을 반환한다.")
    void findChatHistory_cursorLoad_lastReadChatIdIsNullTest() {
        // given
        Member member = fixture.savedMemberBy("member");
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", List.of(member));
        Long chatRoomId = chatRoom.getId();

        chatService.saveChat(member.getId(), chatRoomId, "first");
        Long secondChatId = chatService.saveChat(member.getId(), chatRoomId, "second");

        // when
        ChatHistoryResponse response = chatService.findChatHistory(chatRoomId, member.getId(), secondChatId);

        // then
        assertThat(response.getLastReadChatId()).isNull();
    }

    @Test
    @DisplayName("커서 이전 메시지가 PAGE_SIZE를 초과하면 hasMore=true를 반환한다.")
    void findChatHistory_cursorLoad_hasMoreTrueTest() {
        // given
        Member member = fixture.savedMemberBy("member");
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", List.of(member));
        Long chatRoomId = chatRoom.getId();

        // 32개 저장 후 마지막 id를 커서로 → 이전 31개 존재 → hasMore=true
        Long cursorChatId = null;
        for (int i = 0; i < 32; i++) {
            cursorChatId = chatService.saveChat(member.getId(), chatRoomId, "message" + i);
        }

        // when
        ChatHistoryResponse response = chatService.findChatHistory(chatRoomId, member.getId(), cursorChatId);

        // then
        assertThat(response.getMessages()).hasSize(30);
        assertThat(response.isHasMore()).isTrue();
    }

    @Test
    @DisplayName("커서 이전 메시지가 없으면 빈 리스트와 hasMore=false를 반환한다.")
    void findChatHistory_cursorLoad_noPreviousMessages_returnsEmptyTest() {
        // given
        Member member = fixture.savedMemberBy("member");
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", List.of(member));
        Long chatRoomId = chatRoom.getId();

        Long firstChatId = chatService.saveChat(member.getId(), chatRoomId, "only message");

        // when: 첫 번째 메시지를 커서로 → 이전 메시지 없음
        ChatHistoryResponse response = chatService.findChatHistory(chatRoomId, member.getId(), firstChatId);

        // then
        assertThat(response.getMessages()).isEmpty();
        assertThat(response.isHasMore()).isFalse();
    }

    @Test
    @DisplayName("읽지 않은 메시지가 없으면 PublishReadEvent가 발행되지 않는다.")
    void findChatHistory_noUnread_doesNotPublishReadEvent(ApplicationEvents events) {
        // given
        Member firstMember = fixture.savedMemberBy("firstMember");
        Member secondMember = fixture.savedMemberBy("secondMember");

        ChatRoom chatRoom = fixture.savedChatRoomBy("title", List.of(firstMember, secondMember));
        Long chatRoomId = chatRoom.getId();

        chatService.saveChat(firstMember.getId(), chatRoomId, "message");

        // 첫 번째 입장: 미읽음 읽음 처리 → 이벤트 발행됨
        chatService.findChatHistory(chatRoomId, secondMember.getId(), null);

        // when: 재입장 — 미읽음 없음
        chatService.findChatHistory(chatRoomId, secondMember.getId(), null);

        // then: 두 번 호출했지만 이벤트는 첫 번째 호출에서만 1건 발행
        long eventCount = events.stream(PublishReadEvent.class).count();
        assertThat(eventCount).isEqualTo(1);
    }

    @Test
    @DisplayName("메시지 전송 시 sender의 lastReadChatId가 저장된 chatId로 갱신된다.")
    void saveChat_senderCursorUpdatedTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver = fixture.savedMemberBy("receiver");
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", List.of(sender, receiver));

        // when
        Long savedChatId = chatService.saveChat(sender.getId(), chatRoom.getId(), "hello");
        em.clear();

        // then
        ChatRoomParticipant senderParticipant = chatRoomParticipantRepository
                .findChatRoomBy(chatRoom.getId(), sender.getId());
        assertThat(senderParticipant.getLastReadChatId()).isEqualTo(savedChatId);
    }

    @Test
    @DisplayName("메시지 전송 시 receiver의 lastReadChatId는 갱신되지 않는다.")
    void saveChat_receiverCursorNotUpdatedTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver = fixture.savedMemberBy("receiver");
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", List.of(sender, receiver));

        // when
        chatService.saveChat(sender.getId(), chatRoom.getId(), "hello");
        em.clear();

        // then
        ChatRoomParticipant receiverParticipant = chatRoomParticipantRepository
                .findChatRoomBy(chatRoom.getId(), receiver.getId());
        assertThat(receiverParticipant.getLastReadChatId()).isNull();
    }

    @Test
    @DisplayName("초기 history 조회 시 조회자의 lastReadChatId가 최신 chatId로 갱신된다.")
    void findChatHistory_initialLoad_cursorUpdatedTest() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", List.of(me, other));
        Long chatRoomId = chatRoom.getId();

        chatService.saveChat(other.getId(), chatRoomId, "first");
        Long latestChatId = chatService.saveChat(other.getId(), chatRoomId, "second");

        // when
        chatService.findChatHistory(chatRoomId, me.getId(), null);
        em.clear();

        // then
        ChatRoomParticipant participant = chatRoomParticipantRepository
                .findChatRoomBy(chatRoomId, me.getId());
        assertThat(participant.getLastReadChatId()).isEqualTo(latestChatId);
    }

    @Test
    @DisplayName("페이지네이션 history 조회 시 lastReadChatId는 갱신되지 않는다.")
    void findChatHistory_pagination_cursorNotUpdatedTest() {
        // given
        Member me = fixture.savedMemberBy("me");
        Member other = fixture.savedMemberBy("other");
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", List.of(me, other));
        Long chatRoomId = chatRoom.getId();

        // other가 메시지 2개 전송 → me는 receiver, cursor 갱신 없음
        chatService.saveChat(other.getId(), chatRoomId, "first");
        Long secondChatId = chatService.saveChat(other.getId(), chatRoomId, "second");

        // 초기 조회로 me cursor를 secondChatId로 세팅
        chatService.findChatHistory(chatRoomId, me.getId(), null);
        em.clear();

        // other가 세 번째 메시지 전송 → me cursor: secondChatId 유지 (receiver)
        Long thirdChatId = chatService.saveChat(other.getId(), chatRoomId, "third");
        em.clear();

        // when: beforeChatId != null 페이지네이션 조회
        chatService.findChatHistory(chatRoomId, me.getId(), thirdChatId);
        em.clear();

        // then: cursor는 secondChatId 그대로 (thirdChatId로 진행하지 않음)
        ChatRoomParticipant participant = chatRoomParticipantRepository
                .findChatRoomBy(chatRoomId, me.getId());
        assertThat(participant.getLastReadChatId()).isEqualTo(secondChatId);
    }

    @Test
    @DisplayName("빈 채팅방 조회 시 lastReadChatId는 null 그대로다.")
    void findChatHistory_emptyRoom_cursorNotUpdatedTest() {
        // given
        Member me = fixture.savedMemberBy("me");
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", List.of(me));

        // when
        chatService.findChatHistory(chatRoom.getId(), me.getId(), null);
        em.clear();

        // then
        ChatRoomParticipant participant = chatRoomParticipantRepository
                .findChatRoomBy(chatRoom.getId(), me.getId());
        assertThat(participant.getLastReadChatId()).isNull();
    }

    @Test
    @DisplayName("메시지 전송 시 ROOM_ACTIVE 상태인 수신자의 cursor가 갱신된다.")
    void saveChat_activeRoomReceiverCursorUpdatedTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member activeReceiver = fixture.savedMemberBy("activeReceiver");
        Member inactiveReceiver = fixture.savedMemberBy("inactiveReceiver");
        ChatRoom chatRoom = fixture.savedChatRoomBy("room",
                List.of(sender, activeReceiver, inactiveReceiver));

        // activeReceiver: 세션 등록 + 방 입장 (ENTER_ROOM으로 자동 active 설정)
        WebSocketSession mockSession = mock(WebSocketSession.class);
        given(mockSession.getId()).willReturn("session-active");
        given(mockSession.getAttributes())
                .willReturn(Map.of(SessionConst.SESSION_ID, activeReceiver.getId()));
        chatRoomManager.registerSession(mockSession);
        chatRoomManager.addSessionToRoom(mockSession, chatRoom.getId());

        // when
        Long savedChatId = chatService.saveChat(sender.getId(), chatRoom.getId(), "hello");
        em.clear();

        // then: activeReceiver → cursor 갱신됨
        ChatRoomParticipant activeParticipant = chatRoomParticipantRepository
                .findChatRoomBy(chatRoom.getId(), activeReceiver.getId());
        assertThat(activeParticipant.getLastReadChatId()).isEqualTo(savedChatId);

        // inactiveReceiver → cursor 갱신 안 됨
        ChatRoomParticipant inactiveParticipant = chatRoomParticipantRepository
                .findChatRoomBy(chatRoom.getId(), inactiveReceiver.getId());
        assertThat(inactiveParticipant.getLastReadChatId()).isNull();
    }

    @Test
    @DisplayName("메시지 전송 시 방에 접속 중이어도 ROOM_ACTIVE 상태가 아니면 cursor가 갱신되지 않는다.")
    void saveChat_inRoomButInactiveReceiverCursorNotUpdatedTest() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member inRoomReceiver = fixture.savedMemberBy("inRoomReceiver");
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", List.of(sender, inRoomReceiver));

        // 방 입장(ENTER_ROOM) 후 즉시 ROOM_INACTIVE로 inactive 전환
        WebSocketSession mockSession = mock(WebSocketSession.class);
        given(mockSession.getId()).willReturn("session-in-room");
        given(mockSession.getAttributes())
                .willReturn(Map.of(SessionConst.SESSION_ID, inRoomReceiver.getId()));
        chatRoomManager.registerSession(mockSession);
        chatRoomManager.addSessionToRoom(mockSession, chatRoom.getId());        // auto-activate
        chatRoomManager.deactivateRoom(mockSession.getId(), chatRoom.getId());  // ROOM_INACTIVE

        // when
        chatService.saveChat(sender.getId(), chatRoom.getId(), "hello");
        em.clear();

        // then: cursor 갱신 없음
        ChatRoomParticipant participant = chatRoomParticipantRepository
                .findChatRoomBy(chatRoom.getId(), inRoomReceiver.getId());
        assertThat(participant.getLastReadChatId()).isNull();
    }

    @Test
    @DisplayName("수신자 첫 입장 시 lastReadChatId는 null이다.")
    void findChatHistory_firstEnter_lastReadChatIdIsNull() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver = fixture.savedMemberBy("receiver");
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", List.of(sender, receiver));
        Long chatRoomId = chatRoom.getId();

        chatService.saveChat(sender.getId(), chatRoomId, "first");
        chatService.saveChat(sender.getId(), chatRoomId, "second");

        // when
        ChatHistoryResponse response = chatService.findChatHistory(chatRoomId,
                receiver.getId(), null);

        // then
        assertThat(response.getLastReadChatId()).isNull();
    }

    @Test
    @DisplayName("두 번째 입장 시 lastReadChatId는 직전 입장에서 갱신된 cursor 값이다.")
    void findChatHistory_secondEnter_lastReadChatIdEqualsPreUpdateCursor() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member me = fixture.savedMemberBy("me");
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", List.of(sender, me));
        Long chatRoomId = chatRoom.getId();

        chatService.saveChat(sender.getId(), chatRoomId, "first");
        Long secondChatId = chatService.saveChat(sender.getId(), chatRoomId, "second");

        // 첫 입장: cursor가 secondChatId로 갱신됨
        chatService.findChatHistory(chatRoomId, me.getId(), null);

        // 새 메시지 도착
        chatService.saveChat(sender.getId(), chatRoomId, "third");

        // when: 두 번째 입장
        ChatHistoryResponse response = chatService.findChatHistory(chatRoomId, me.getId(),
                null);

        // then: cursor 갱신 이전 값 = 첫 입장 때 갱신된 secondChatId
        assertThat(response.getLastReadChatId()).isEqualTo(secondChatId);
    }

    @Test
    @DisplayName("발신자 재입장 시 lastReadChatId는 자신이 마지막으로 보낸 메시지 ID이다.")
    void findChatHistory_senderReenter_lastReadChatIdEqualsSentMessageCursor() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiver = fixture.savedMemberBy("receiver");
        ChatRoom chatRoom = fixture.savedChatRoomBy("room", List.of(sender, receiver));
        Long chatRoomId = chatRoom.getId();

        Long sentChatId = chatService.saveChat(sender.getId(), chatRoomId, "hello");
        chatService.saveChat(receiver.getId(), chatRoomId, "reply"); // sender cursor 변화 없음

        // when
        ChatHistoryResponse response = chatService.findChatHistory(chatRoomId, sender.getId(),
                null);

        // then: saveChat에서 갱신된 cursor = sentChatId
        assertThat(response.getLastReadChatId()).isEqualTo(sentChatId);
    }
}