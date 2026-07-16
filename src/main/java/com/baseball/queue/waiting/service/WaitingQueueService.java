package com.baseball.queue.waiting.service;

import com.baseball.queue.waiting.dto.WaitingRankResponse;
import com.baseball.queue.waiting.repository.WaitingQueueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    private final WaitingQueueRepository waitingQueueRepository;
    private static final String WAITING_QUEUE_KEY = "queue:baseball:waiting";

    public Mono<WaitingRankResponse> registerQueue(String userId) {
        long currentTimestamp = Instant.now().toEpochMilli();

        // 중복 요청 방지: 이미 랭크가 존재하는지 확인 후, 없다면 대기열에 진입
        return waitingQueueRepository.getRank(WAITING_QUEUE_KEY, userId)
                .switchIfEmpty(Mono.defer(
                        () -> waitingQueueRepository.addToWaitingQueue(WAITING_QUEUE_KEY, userId, currentTimestamp)
                                .then(waitingQueueRepository.getRank(WAITING_QUEUE_KEY, userId))))
                .flatMap(rank -> waitingQueueRepository.getQueueSize(WAITING_QUEUE_KEY)
                        .map(size -> new WaitingRankResponse(userId, rank, size)));
    }

    public Mono<WaitingRankResponse> checkRank(String userId) {
        long currentTimestamp = Instant.now().toEpochMilli();

        // 랭크 조회와 동시에 Heartbeat 갱신 수행
        return waitingQueueRepository.updateHeartbeat(userId, currentTimestamp)
                .then(waitingQueueRepository.getRank(WAITING_QUEUE_KEY, userId))
                .defaultIfEmpty(-1L)
                .flatMap(rank -> waitingQueueRepository.getQueueSize(WAITING_QUEUE_KEY)
                        .map(size -> new WaitingRankResponse(userId, rank, size)));
    }

    public Mono<Void> dropout(String userId) {
        return waitingQueueRepository.removeFromWaitingQueue(WAITING_QUEUE_KEY, userId)
                .then();
    }
}
