package com.baseball.queue.waiting.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

@SpringBootTest
@ActiveProfiles("test")
class WaitingQueueServiceTest {

    @Autowired
    private WaitingQueueService waitingQueueService;

    @Autowired
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    private static final String WAITING_QUEUE_KEY = "queue:baseball:waiting";
    private static final String HEARTBEAT_KEY = "queue:baseball:heartbeat"; // Heartbeat 키 추가

    @BeforeEach
    void cleanQueue() {
        reactiveRedisTemplate.delete(WAITING_QUEUE_KEY, HEARTBEAT_KEY).block();
    }

    @Test
    @DisplayName("유저가 처음 대기열에 등록하면 정상적으로 순번(0순위)과 대기열 크기가 반환된다")
    void registerQueueSuccess() {
        String userId = "user_1";

        StepVerifier.create(waitingQueueService.registerQueue(userId))
                .assertNext(response -> {
                    assert response.userId().equals(userId);
                    assert response.rank() == 0L;
                    assert response.estimatedWaitingCount() == 1L;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("중복 등록 요청 시 기존 등록 정보를 유지하며 순번이 밀리지 않는다")
    void registerQueueDuplicate() {
        String user1 = "user_1";
        String user2 = "user_2";

        waitingQueueService.registerQueue(user1).block();
        waitingQueueService.registerQueue(user2).block();

        StepVerifier.create(waitingQueueService.registerQueue(user1))
                .assertNext(response -> {
                    assert response.userId().equals(user1);
                    assert response.rank() == 0L;
                    assert response.estimatedWaitingCount() == 2L;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("대기열에 없는 사용자가 순번을 조회하면 -1을 리턴한다")
    void checkRankNotExists() {
        String nonActiveUser = "ghost_user";

        StepVerifier.create(waitingQueueService.checkRank(nonActiveUser))
                .assertNext(response -> {
                    assert response.userId().equals(nonActiveUser);
                    assert response.rank() == -1L;
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("대기열에서 이탈(dropout) 처리하면 대기열에서 정상 삭제된다")
    void dropoutSuccess() {
        String userId = "leave_user";
        waitingQueueService.registerQueue(userId).block();

        StepVerifier.create(waitingQueueService.dropout(userId)
                .then(waitingQueueService.checkRank(userId)))
                .assertNext(response -> {
                    assert response.rank() == -1L;
                })
                .verifyComplete();
    }
}