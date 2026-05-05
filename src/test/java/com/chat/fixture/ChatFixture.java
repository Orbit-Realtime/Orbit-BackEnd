package com.chat.fixture;

import com.chat.api.Result;
import com.chat.entity.SpaceMember;
import com.chat.repository.SpaceMemberRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
public class ChatFixture {

    @Autowired
    private SpaceMemberRepository spaceMemberRepository;

    public ResponseEntity<Result> requestChatHistory(Long chatRoomId, String sessionId, int port) {

        TestRestTemplate restTemplate = new TestRestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "JSESSIONID=" + sessionId);
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        URI uri = UriComponentsBuilder
                .fromUriString("http://localhost:{port}/api/chats")
                .queryParam("chatRoomId", chatRoomId)
                .build(port);

        return restTemplate.exchange(
                uri,
                HttpMethod.GET,
                requestEntity,
                Result.class
        );
    }

    @Transactional(readOnly = true)
    public SpaceMember reload(Long roomId, Long memberId) {
        return spaceMemberRepository.findChatRoomBy(roomId, memberId);
    }
}
