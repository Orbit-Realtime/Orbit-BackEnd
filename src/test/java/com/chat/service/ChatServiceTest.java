package com.chat.service;

import com.chat.entity.*;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.*;
import com.chat.repository.dtos.MemberUnreadCount;
import com.chat.service.dtos.ChatHistory;
import com.chat.service.dtos.ChatHistoryResponse;
import com.chat.service.dtos.SaveChatData;
import com.chat.socket.manager.ChatRoomManager;
import com.chat.utils.consts.SessionConst;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class ChatServiceTest {

    @Autowired
    private ChatService chatService;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private ChatReadRepository chatReadRepository;
    @Autowired
    private TestDataFixture fixture;
    @Autowired
    private ChatRoomManager chatRoomManager;

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

        ChatRead chatReadSender = chatReadRepository.findBy(savedChatId, sender.getId());
        assertThat(chatReadSender.getIsRead()).isTrue();

        ChatRead chatReadReceiver1 = chatReadRepository.findBy(savedChatId, receiver1.getId());
        assertThat(chatReadReceiver1.getIsRead()).isFalse();

        ChatRead chatReadReceiver2 = chatReadRepository.findBy(savedChatId, receiver2.getId());
        assertThat(chatReadReceiver2.getIsRead()).isFalse();
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
        ChatHistoryResponse response = chatService.findChatHistory(chatRoomId, firstMember.getId());

        // then
        List<ChatHistory> messages = response.getMessages();
        assertThat(messages).hasSize(3);

        // lastReadChatId: 조회 전 firstMember가 마지막으로 읽은 메시지 = 자신이 보낸 firstChat
        assertThat(response.getLastReadChatId()).isEqualTo(firstChatId);

        ChatHistory firstChat = messages.get(0);
        assertThat(firstChat.getChatId()).isEqualTo(firstChatId);
        assertThat(firstChat.getSenderId()).isEqualTo(firstMember.getId());
        assertThat(firstChat.getUnreadMemberCount()).isEqualTo(1L);

        // updateUnreadChatReadsToRead 이후 count → firstMember 읽음 제외한 값
        ChatHistory secondChat = messages.get(1);
        assertThat(secondChat.getUnreadMemberCount()).isEqualTo(1L);

        ChatHistory thirdChat = messages.get(2);
        assertThat(thirdChat.getUnreadMemberCount()).isEqualTo(1L);

        // firstMember의 미읽음이 0인지 확인 — 배치 쿼리 사용
        List<MemberUnreadCount> remainingUnread = chatReadRepository
                .findUnReadCountsBy(chatRoomId, List.of(firstMember.getId()));
        assertThat(remainingUnread).isEmpty();

        assertThat(firstChat.getMessage()).isEqualTo("message");
    }

    @Test
    @DisplayName("채팅방에 접속 중인 수신자는 메시지 저장 시 isRead=true로 저장된다.")
    void saveChatTest_receiverInRoom_isReadTrue() {
        // given
        Member sender = fixture.savedMemberBy("sender");
        Member receiverInRoom = fixture.savedMemberBy("receiverInRoom");
        Member receiverNotInRoom = fixture.savedMemberBy("receiverNotInRoom");

        ChatRoom chatRoom = fixture.savedChatRoomBy("title", List.of(sender, receiverInRoom, receiverNotInRoom));
        Long chatRoomId = chatRoom.getId();

        // receiverInRoom을 ChatRoomManager에 등록 (방에 접속 중인 상태 시뮬레이션)
        WebSocketSession mockSession = mock(WebSocketSession.class);
        given(mockSession.getAttributes()).willReturn(Map.of(SessionConst.SESSION_ID, receiverInRoom.getId()));
        chatRoomManager.addSessionToRoom(mockSession, chatRoomId);

        // when
        Long savedChatId = chatService.saveChat(sender.getId(), chatRoomId, "hello");

        // then
        ChatRead senderRead = chatReadRepository.findBy(savedChatId, sender.getId());
        assertThat(senderRead.getIsRead()).isTrue();

        // 방에 접속 중인 수신자 → isRead=true
        ChatRead receiverInRoomRead = chatReadRepository.findBy(savedChatId, receiverInRoom.getId());
        assertThat(receiverInRoomRead.getIsRead()).isTrue();

        // 방에 없는 수신자 → isRead=false
        ChatRead receiverNotInRoomRead = chatReadRepository.findBy(savedChatId, receiverNotInRoom.getId());
        assertThat(receiverNotInRoomRead.getIsRead()).isFalse();
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
        ChatHistoryResponse response = chatService.findChatHistory(chatRoom.getId(), firstMember.getId());

        // then
        assertThat(response.getMessages()).isEmpty();
        assertThat(response.getLastReadChatId()).isNull();
    }
}