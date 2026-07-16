package com.baseball.queue.waiting.scheduler;

import com.baseball.queue.waiting.repository.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatScheduler {

    private final WaitingQueueRepository waitingQueueRepository;
    private static final String WAITING_QUEUE_KEY = "queue:baseball:waiting";
    private static final long HEARTBEAT_TIMEOUT_MS = 30000; // 30초

    // 10초마다 고스트 유저 감지 및 제거 실행
    @Scheduled(fixedDelay = 10000)
    public void removeGhostUsers() {
        long thresholdTime = Instant.now().toEpochMilli() - HEARTBEAT_TIMEOUT_MS;

        waitingQueueRepository.findExpiredUsers(thresholdTime)
                .collectList()
                .filter(users -> !users.isEmpty())
                .flatMap(users -> processGhostUsersRemoval(users, thresholdTime))
                .doOnError(e -> log.error("고스트 유저 정리 중 에러 발생", e))
                .subscribe(); // 스케줄러 내 비동기 실행 트리거
    }

    // 대기열 및 Heartbeat ZSET에서 유저 동시 삭제 처리
    private Mono<Void> processGhostUsersRemoval(List<String> users, long thresholdTime) {
        String[] userIds = users.toArray(new String[0]);

        return waitingQueueRepository.removeMultipleFromWaitingQueue(WAITING_QUEUE_KEY, userIds)
                .then(waitingQueueRepository.removeExpiredHeartbeats(thresholdTime))
                .doOnSuccess(v -> log.info("고스트 유저 {}명 삭제 완료", users.size()))
                .then();
    }
}