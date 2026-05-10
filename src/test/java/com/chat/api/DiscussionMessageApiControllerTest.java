package com.chat.api;

import com.chat.entity.Discussion;
import com.chat.entity.Member;
import com.chat.entity.Message;
import com.chat.entity.Space;
import com.chat.fixture.TestDataFixture;
import com.chat.repository.DiscussionRepository;
import com.chat.service.DiscussionMessageService;
import com.chat.utils.consts.SessionConst;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DiscussionMessageApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TestDataFixture fixture;
    @Autowired private DiscussionRepository discussionRepository;
    @Autowired private DiscussionMessageService discussionMessageService;

    @AfterEach
    void tearDown() {
        fixture.deleteAllData();
    }

    @Test
    @DisplayName("Space 참여자는 DiscussionMessage 목록 조회 API에서 200과 메시지 목록을 받는다")
    void Space_참여자는_DiscussionMessage_목록_조회_API에서_200과_메시지_목록을_받는다() throws Exception {
        // given
        Member member = fixture.savedMemberBy("member");
        Space space = fixture.savedChatRoomBy("space", List.of(member));
        Message message = fixture.savedSimpleChat("내용", member, space);
        Discussion discussion = discussionRepository.save(Discussion.of(message));
        discussionMessageService.saveDiscussionMessage(discussion.getId(), member.getId(), "첫 번째 답글");

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.SESSION_ID, member.getId());

        // when & then
        mockMvc.perform(get("/api/discussions/{discussionId}/messages", discussion.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].content").value("첫 번째 답글"))
                .andExpect(jsonPath("$.data[0].discussionId").value(discussion.getId()))
                .andExpect(jsonPath("$.data[0].senderId").value(member.getId()));
    }
}
