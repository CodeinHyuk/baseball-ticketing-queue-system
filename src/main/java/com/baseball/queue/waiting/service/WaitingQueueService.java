package com.baseball.queue.waiting.service;

import com.baseball.queue.waiting.dto.WaitingRankResponse;
import com.baseball.queue.global.redis.QueueRedisKeys;
import com.baseball.queue.waiting.repository.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    private final WaitingQueueRepository waitingQueueRepository;
    public Mono<WaitingRankResponse> registerQueue(String userId) {
        long currentTimestamp = Instant.now().toEpochMilli();
        String queueCredential = UUID.randomUUID().toString();

        return waitingQueueRepository.registerWaitingUser(userId, currentTimestamp, queueCredential)
                .flatMap(created -> waitingQueueRepository.getRank(QueueRedisKeys.WAITING_QUEUE, userId)
                        .flatMap(rank -> waitingQueueRepository.getQueueSize(QueueRedisKeys.WAITING_QUEUE)
                                .map(size -> new WaitingRankResponse(
                                        userId,
                                        rank,
                                        size,
                                        created ? queueCredential : null))));
    }

    public Mono<WaitingRankResponse> checkRank(String userId) {
        long currentTimestamp = Instant.now().toEpochMilli();

        return waitingQueueRepository.refreshHeartbeatIfWaiting(userId, currentTimestamp)
                .then(waitingQueueRepository.getRank(QueueRedisKeys.WAITING_QUEUE, userId))
                .defaultIfEmpty(-1L)
                .flatMap(rank -> waitingQueueRepository.getQueueSize(QueueRedisKeys.WAITING_QUEUE)
                        .map(size -> new WaitingRankResponse(userId, rank, size, null)));
    }

    public Mono<Void> dropout(String userId) {
        return waitingQueueRepository.removeWaitingUser(userId)
                .then();
    }
}
