package com.chat.repository;

import com.chat.entity.Space;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@Transactional
@SpringBootTest
class ChatRoomRepositoryTest {

    @Autowired
    private SpaceRepository spaceRepository;

    @Test
    @DisplayName("채팅방 제목을 지정한 채팅방을 저장한다.")
    void saveTestByTitle() {
        // given
        String title = "title";
        Space chatRoom = Space.of(title);

        // when
        Space savedChatRoom = spaceRepository.save(chatRoom);

        // then
        assertThat(savedChatRoom.getId()).isNotNull();
        assertThat(savedChatRoom.getTitle()).isEqualTo(title);
    }
}