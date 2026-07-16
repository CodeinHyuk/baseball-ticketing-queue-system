package com.baseball.queue.active.service;

import com.baseball.queue.active.repository.ActiveQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ActiveQueueService {

    private final ActiveQueueRepository activeQueueRepository;

    public Mono<Boolean> checkActiveStatus(String userId) {
        return activeQueueRepository.isActiveUser(userId);
    }
}