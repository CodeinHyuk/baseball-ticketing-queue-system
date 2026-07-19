package com.baseball.queue.waiting.scheduler;

import com.baseball.queue.global.config.QueueProperties;
import com.baseball.queue.waiting.repository.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatScheduler {

    private final WaitingQueueRepository waitingQueueRepository;
    private final QueueProperties queueProperties;

    @Scheduled(fixedDelayString = "${queue.heartbeat.cleanup-interval-ms}")
    public void removeGhostUsers() {
        long thresholdTime = Instant.now().toEpochMilli() - queueProperties.getHeartbeat().getTimeoutMs();

        waitingQueueRepository.removeExpiredWaitingUsers(
                        thresholdTime,
                        queueProperties.getHeartbeat().getCleanupBatchSize())
                .filter(removed -> removed > 0)
                .doOnNext(removed -> log.info("고스트 유저 {}명 삭제 완료", removed))
                .doOnError(e -> log.error("고스트 유저 정리 중 에러 발생", e))
                .subscribe();
    }
}
