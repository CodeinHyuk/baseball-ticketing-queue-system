package com.baseball.queue.active.scheduler;

import com.baseball.queue.active.repository.ActiveQueueRepository;
import com.baseball.queue.waiting.repository.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private final WaitingQueueRepository waitingQueueRepository;
    private final ActiveQueueRepository activeQueueRepository;

    private static final String WAITING_QUEUE_KEY = "queue:baseball:waiting";
    private static final long ALLOW_COUNT_PER_TICK = 100;
    private static final Duration ACTIVE_TTL = Duration.ofMinutes(10);

    @Scheduled(fixedDelay = 3000)
    public void allowUsersToEnter() {
        waitingQueueRepository.popMin(WAITING_QUEUE_KEY, ALLOW_COUNT_PER_TICK)
                .flatMap(tuple -> activeQueueRepository.saveActiveUser(tuple.getValue(), ACTIVE_TTL))
                .subscribe();
    }
}