package com.baseball.queue.waiting.service;

import com.baseball.queue.global.config.QueueProperties;
import com.baseball.queue.global.redis.QueueRedisKeys;
import com.baseball.queue.waiting.repository.WaitingQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.time.Instant;

@SpringBootTest
@ActiveProfiles("test")
class WaitingQueueServiceTest {

    @Autowired
    private WaitingQueueService waitingQueueService;

    @Autowired
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Autowired
    private WaitingQueueRepository waitingQueueRepository;

    @Autowired
    private QueueProperties queueProperties;

    @BeforeEach
    void cleanQueue() {
        reactiveRedisTemplate.delete(QueueRedisKeys.WAITING_QUEUE, QueueRedisKeys.HEARTBEAT).block();
    }

    @Test
    @DisplayName("유저가 처음 대기열에 등록하면 정상적으로 순번(0순위)과 대기열 크기가 반환된다")
    void registerQueueSuccess() {
        String userId = "user_1";
        var response = waitingQueueService.registerQueue(userId).block();

        assert response.userId().equals(userId);
        assert response.rank() == 0L;
        assert response.estimatedWaitingCount() == 1L;
        assert response.queueCredential() != null;
        assert reactiveRedisTemplate.opsForZSet().score(QueueRedisKeys.HEARTBEAT, userId).block() != null;
        assert response.queueCredential().equals(reactiveRedisTemplate.opsForValue()
                .get(QueueRedisKeys.queueCredentialKey(userId)).block());
    }

    @Test
    @DisplayName("중복 등록 요청 시 기존 등록 정보를 유지하며 순번이 밀리지 않는다")
    void registerQueueDuplicate() {
        String user1 = "user_1";
        String user2 = "user_2";

        var firstRegistration = waitingQueueService.registerQueue(user1).block();
        Double initialScore = reactiveRedisTemplate.opsForZSet().score(QueueRedisKeys.WAITING_QUEUE, user1).block();
        waitingQueueService.registerQueue(user2).block();

        var response = waitingQueueService.registerQueue(user1).block();

        assert response.userId().equals(user1);
        assert response.rank() == 0L;
        assert response.estimatedWaitingCount() == 2L;
        assert response.queueCredential() == null;
        assert initialScore.equals(reactiveRedisTemplate.opsForZSet()
                .score(QueueRedisKeys.WAITING_QUEUE, user1).block());
        assert firstRegistration.queueCredential().equals(reactiveRedisTemplate.opsForValue()
                .get(QueueRedisKeys.queueCredentialKey(user1)).block());
    }

    @Test
    @DisplayName("대기열에 없는 사용자가 순번을 조회하면 -1을 리턴한다")
    void checkRankNotExists() {
        String nonActiveUser = "ghost_user";

        var response = waitingQueueService.checkRank(nonActiveUser).block();

        assert response.userId().equals(nonActiveUser);
        assert response.rank() == -1L;
        assert reactiveRedisTemplate.opsForZSet()
                .score(QueueRedisKeys.HEARTBEAT, nonActiveUser).block() == null;
    }

    @Test
    @DisplayName("대기열에서 이탈(dropout) 처리하면 대기열에서 정상 삭제된다")
    void dropoutSuccess() {
        String userId = "leave_user";
        waitingQueueService.registerQueue(userId).block();

        waitingQueueService.dropout(userId).block();
        var response = waitingQueueService.checkRank(userId).block();

        assert response.rank() == -1L;
        assert reactiveRedisTemplate.opsForZSet()
                .score(QueueRedisKeys.HEARTBEAT, userId).block() == null;
        assert reactiveRedisTemplate.opsForValue()
                .get(QueueRedisKeys.queueCredentialKey(userId)).block() == null;
    }

    @Test
    @DisplayName("만료되지 않은 사용자는 Heartbeat 정리 대상에서 제외된다")
    void cleanupKeepsRecentHeartbeat() {
        String userId = "recent_user";
        long now = Instant.now().toEpochMilli();
        waitingQueueRepository.registerWaitingUser(userId, now, "credential").block();

        Long removed = waitingQueueRepository.removeExpiredWaitingUsers(
                now - queueProperties.getHeartbeat().getTimeoutMs(),
                queueProperties.getHeartbeat().getCleanupBatchSize()).block();

        assert removed == 0L;
        assert reactiveRedisTemplate.opsForZSet().rank(QueueRedisKeys.WAITING_QUEUE, userId).block() != null;
    }

    @Test
    @DisplayName("만료 사용자는 제한된 배치 정리에서 waiting과 heartbeat가 함께 제거된다")
    void cleanupRemovesExpiredWaitingUser() {
        String userId = "expired_user";
        long now = Instant.now().toEpochMilli();
        long expiredAt = now - queueProperties.getHeartbeat().getTimeoutMs() - 1;
        waitingQueueRepository.registerWaitingUser(userId, expiredAt, "credential").block();

        Long removed = waitingQueueRepository.removeExpiredWaitingUsers(
                now - queueProperties.getHeartbeat().getTimeoutMs(), 1).block();

        assert removed == 1L;
        assert reactiveRedisTemplate.opsForZSet().rank(QueueRedisKeys.WAITING_QUEUE, userId).block() == null;
        assert reactiveRedisTemplate.opsForZSet().score(QueueRedisKeys.HEARTBEAT, userId).block() == null;
    }

    @Test
    @DisplayName("만료 사용자 정리는 설정된 배치 크기까지만 처리한다")
    void cleanupHonorsBatchSize() {
        long now = Instant.now().toEpochMilli();
        long expiredAt = now - queueProperties.getHeartbeat().getTimeoutMs() - 1;
        waitingQueueRepository.registerWaitingUser("expired_user_1", expiredAt, "credential_1").block();
        waitingQueueRepository.registerWaitingUser("expired_user_2", expiredAt, "credential_2").block();

        Long removed = waitingQueueRepository.removeExpiredWaitingUsers(
                now - queueProperties.getHeartbeat().getTimeoutMs(), 1).block();

        assert removed == 1L;
        assert reactiveRedisTemplate.opsForZSet().size(QueueRedisKeys.WAITING_QUEUE).block() == 1L;
        assert reactiveRedisTemplate.opsForZSet().size(QueueRedisKeys.HEARTBEAT).block() == 1L;
    }

    @Test
    @DisplayName("Heartbeat가 정리 직전에 갱신되면 원자적 정리에서 대기 사용자가 유지된다")
    void heartbeatRefreshBeforeCleanupKeepsWaitingUser() {
        String userId = "racing_user";
        long now = Instant.now().toEpochMilli();
        waitingQueueRepository.registerWaitingUser(
                userId,
                now - queueProperties.getHeartbeat().getTimeoutMs() - 1,
                "credential").block();

        assert waitingQueueRepository.refreshHeartbeatIfWaiting(userId, now).block();
        Long removed = waitingQueueRepository.removeExpiredWaitingUsers(
                now - queueProperties.getHeartbeat().getTimeoutMs(),
                queueProperties.getHeartbeat().getCleanupBatchSize()).block();

        assert removed == 0L;
        assert reactiveRedisTemplate.opsForZSet().rank(QueueRedisKeys.WAITING_QUEUE, userId).block() != null;
    }

    @Test
    @DisplayName("활성 전환 시 waiting과 heartbeat가 함께 제거되고 active 상태가 생성된다")
    void promotionMovesUserFromWaitingToActive() {
        String userId = "promoted_user";
        waitingQueueService.registerQueue(userId).block();

        Long promoted = waitingQueueRepository.promoteWaitingUsers(1,
                queueProperties.getAdmission().getActiveTtlMs()).block();

        assert promoted == 1L;
        assert reactiveRedisTemplate.opsForZSet().rank(QueueRedisKeys.WAITING_QUEUE, userId).block() == null;
        assert reactiveRedisTemplate.opsForZSet().score(QueueRedisKeys.HEARTBEAT, userId).block() == null;
        assert "true".equals(reactiveRedisTemplate.opsForValue().get(QueueRedisKeys.activeUserKey(userId)).block());
    }
}
