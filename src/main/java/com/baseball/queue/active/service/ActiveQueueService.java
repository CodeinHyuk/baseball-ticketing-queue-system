package com.baseball.queue.active.service;

import com.baseball.queue.active.repository.ActiveQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ActiveQueueService {

    private final ActiveQueueRepository activeQueueRepository;

    public Mono<Boolean> exchangeActiveCredential(String userId, String queueCredential) {
        return activeQueueRepository.hasActiveCredential(userId, queueCredential);
    }
}
