package com.baseball.queue.waiting.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class WaitingQueueRepository {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    // ZADD: 스코어(timestamp)와 함께 유저 등록
    public Mono<Boolean> addToWaitingQueue(String queueKey, String userId, long timestamp) {
        return reactiveRedisTemplate.opsForZSet().add(queueKey, userId, timestamp);
    }

    // ZRANK: 유저의 0-based 순번 조회
    public Mono<Long> getRank(String queueKey, String userId) {
        return reactiveRedisTemplate.opsForZSet().rank(queueKey, userId);
    }

    // ZCARD: 전체 대기열 크기 조회
    public Mono<Long> getQueueSize(String queueKey) {
        return reactiveRedisTemplate.opsForZSet().size(queueKey);
    }

    // ZREM: 대기열에서 이탈 처리
    public Mono<Long> removeFromWaitingQueue(String queueKey, String userId) {
        return reactiveRedisTemplate.opsForZSet().remove(queueKey, userId);
    }

    // pollMin: Redis의 ZPOPMIN을 사용하여 가장 우선순위가 높은 요소 n개(배치 사이즈)를 즉시 추출 (없으면 빈 Flux)
    public Flux<ZSetOperations.TypedTuple<String>> popMin(String queueKey, long count) {
        return reactiveRedisTemplate.opsForZSet().popMin(queueKey, count);
    }

    private static final String HEARTBEAT_KEY = "queue:baseball:heartbeat";

    // 유저의 마지막 활동 시간 갱신
    public Mono<Boolean> updateHeartbeat(String userId, long timestamp) {
        return reactiveRedisTemplate.opsForZSet().add(HEARTBEAT_KEY, userId, timestamp);
    }

    // 임계값 이전의 타임스탬프를 가진 만료된 유저 조회
    public Flux<String> findExpiredUsers(long thresholdTimestamp) {
        return reactiveRedisTemplate.opsForZSet()
                .rangeByScore(HEARTBEAT_KEY,
                        org.springframework.data.domain.Range.closed(0.0, (double) thresholdTimestamp));
    }

    // 만료된 유저들의 Heartbeat 데이터 일괄 삭제
    public Mono<Long> removeExpiredHeartbeats(long thresholdTimestamp) {
        return reactiveRedisTemplate.opsForZSet()
                .removeRangeByScore(HEARTBEAT_KEY,
                        org.springframework.data.domain.Range.closed(0.0, (double) thresholdTimestamp));
    }

    // 대기열에서 다수의 유저 일괄 삭제
    public Mono<Long> removeMultipleFromWaitingQueue(String queueKey, String... userIds) {
        if (userIds.length == 0)
            return Mono.just(0L);
        return reactiveRedisTemplate.opsForZSet().remove(queueKey, (Object[]) userIds);
    }
}
