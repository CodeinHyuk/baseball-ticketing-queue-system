package com.baseball.queue.active.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class ActiveQueueRepository {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private static final String ACTIVE_KEY_PREFIX = "queue:baseball:active:";

    public Mono<Boolean> saveActiveUser(String userId, Duration ttl) {
        return reactiveRedisTemplate.opsForValue()
                .set(ACTIVE_KEY_PREFIX + userId, "true", ttl);
    }

    public Mono<Boolean> isActiveUser(String userId) {
        return reactiveRedisTemplate.hasKey(ACTIVE_KEY_PREFIX + userId);
    }
}