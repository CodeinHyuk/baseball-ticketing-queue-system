package com.baseball.queue.active.scheduler;

import com.baseball.queue.global.config.QueueProperties;
import com.baseball.queue.waiting.repository.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QueueScheduler {

    private final WaitingQueueRepository waitingQueueRepository;
    private final QueueProperties queueProperties;

    @Scheduled(fixedDelayString = "${queue.admission.interval-ms}")
    public void allowUsersToEnter() {
        waitingQueueRepository.promoteWaitingUsers(
                        queueProperties.getAdmission().getBatchSize(),
                        queueProperties.getAdmission().getActiveTtlMs())
                .subscribe();
    }
}
