package com.chat.socket.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringBootTest
class WebsocketSessionManagerTest {

    @Autowired
    private WebsocketSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager.clearAll();
    }

    @Test
    @DisplayName("세션을 추가하고 memberId 로 조회한다.")
    void getSessionTest() {
        // given
        Long memberId = 1L;
        WebSocketSession mockSession = mock(WebSocketSession.class);

        // when
        sessionManager.addSession(memberId, mockSession);
        Collection<WebSocketSession> sessions = sessionManager.getSessionBy(memberId);

        // then
        assertThat(sessions).isNotEmpty();
        assertThat(sessions).contains(mockSession);
    }

    @Test
    @DisplayName("세션을 삭제하면 조회 시 빈 컬렉션을 반환한다.")
    void removeSessionTest() {
        // given
        Long memberId = 1L;
        WebSocketSession mockSession = mock(WebSocketSession.class);
        sessionManager.addSession(memberId, mockSession);

        // when
        sessionManager.removeSession(memberId, mockSession);
        Collection<WebSocketSession> sessions = sessionManager.getSessionBy(memberId);

        // then
        assertThat(sessions).isEmpty();
    }

    @Test
    @DisplayName("동일 memberId 에 여러 세션을 추가하면 모두 저장된다.")
    void addMultipleSessionsForSameMemberTest() {
        // given
        Long memberId = 1L;
        WebSocketSession sessionA = mock(WebSocketSession.class);
        WebSocketSession sessionB = mock(WebSocketSession.class);

        // when
        sessionManager.addSession(memberId, sessionA);
        sessionManager.addSession(memberId, sessionB);

        // then
        Collection<WebSocketSession> sessions = sessionManager.getSessionBy(memberId);
        assertThat(sessions).hasSize(2);
        assertThat(sessions).contains(sessionA, sessionB);
    }

    @Test
    @DisplayName("특정 세션 하나만 제거해도 나머지 세션은 유지된다.")
    void removeOneSessionKeepsOtherSessionTest() {
        // given
        Long memberId = 1L;
        WebSocketSession sessionA = mock(WebSocketSession.class);
        WebSocketSession sessionB = mock(WebSocketSession.class);
        sessionManager.addSession(memberId, sessionA);
        sessionManager.addSession(memberId, sessionB);

        // when
        sessionManager.removeSession(memberId, sessionA);

        // then
        Collection<WebSocketSession> sessions = sessionManager.getSessionBy(memberId);
        assertThat(sessions).hasSize(1);
        assertThat(sessions).contains(sessionB);
        assertThat(sessions).doesNotContain(sessionA);
    }

    @Test
    @DisplayName("마지막 세션을 제거하면 해당 memberId 의 엔트리가 삭제된다.")
    void removeLastSessionClearsEntryTest() {
        // given
        Long memberId = 1L;
        WebSocketSession mockSession = mock(WebSocketSession.class);
        sessionManager.addSession(memberId, mockSession);

        // when
        sessionManager.removeSession(memberId, mockSession);

        // then
        assertThat(sessionManager.getSessionBy(memberId)).isEmpty();
    }

    @Test
    @DisplayName("ConcurrentHashMap 기반의 세션 관리가 다중 스레드 환경에서도 안전하게 동작한다.")
    void concurrentAccessTest() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        IntStream.rangeClosed(1, threadCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    Long memberId = (long) i;
                    WebSocketSession session = mock(WebSocketSession.class);

                    // 여러 스레드에서 동시에 add, get, remove 수행
                    sessionManager.addSession(memberId, session);
                    Collection<WebSocketSession> found = sessionManager.getSessionBy(memberId);
                    assertThat(found).contains(session);

                    sessionManager.removeSession(memberId, session);
                    Collection<WebSocketSession> removed = sessionManager.getSessionBy(memberId);
                    assertThat(removed).isEmpty();
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await(); // 모든 스레드가 종료될 때까지 대기
        executorService.shutdown();

        // 최종적으로 activeMemberSessions 는 비어 있어야 함
        assertThat(sessionManager.getSessionBy(1L)).isEmpty();
        assertThat(sessionManager.getSessionBy(100L)).isEmpty();
    }

    @Test
    @DisplayName("대량의 동시 요청에서도 ConcurrentHashMap 기반 세션 관리가 안정적으로 동작하고 성능을 보장한다.")
    void concurrentPerformanceTest() throws InterruptedException {
        int threadCount = 200;   // 동시에 실행할 스레드 수
        int iterations = 1000;   // 각 스레드에서 반복 실행 횟수
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.nanoTime();

        IntStream.rangeClosed(1, threadCount).forEach(i -> {
            executorService.submit(() -> {
                try {
                    Long memberId = (long) i;
                    for (int j = 0; j < iterations; j++) {
                        WebSocketSession session = mock(WebSocketSession.class);
                        sessionManager.addSession(memberId, session);

                        Collection<WebSocketSession> found = sessionManager.getSessionBy(memberId);
                        assertThat(found).contains(session);

                        sessionManager.removeSession(memberId, session);
                        Collection<WebSocketSession> removed = sessionManager.getSessionBy(memberId);
                        assertThat(removed).isEmpty();
                    }
                } finally {
                    latch.countDown();
                }
            });
        });

        latch.await();
        executorService.shutdown();

        long endTime = System.nanoTime();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        long totalOps = (long) threadCount * iterations * 3; // add/get/remove = 3 ops

        System.out.printf("총 실행 시간: %d ms%n", durationMs);
        System.out.printf("총 연산 수: %d ops%n", totalOps);
        System.out.printf("초당 처리량: %d ops/sec%n", (totalOps * 1000) / durationMs);

        // 최종적으로 Map 은 비어 있어야 함
        assertThat(sessionManager.getSessionBy(1L)).isEmpty();
        assertThat(sessionManager.getSessionBy((long) threadCount)).isEmpty();
    }
}
