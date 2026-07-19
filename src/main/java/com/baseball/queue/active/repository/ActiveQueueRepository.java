package com.baseball.queue.active.repository;

import com.baseball.queue.global.redis.QueueRedisKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ActiveQueueRepository {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private static final DefaultRedisScript<Long> ACTIVE_CREDENTIAL_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            return redis.call('EXISTS', KEYS[2])
            """, Long.class);

    public Mono<Boolean> hasActiveCredential(String userId, String queueCredential) {
        return reactiveRedisTemplate.execute(
                        ACTIVE_CREDENTIAL_SCRIPT,
                        List.of(QueueRedisKeys.queueCredentialKey(userId), QueueRedisKeys.activeUserKey(userId)),
                        List.of(queueCredential))
                .single(0L)
                .map(result -> result == 1L);
    }
}
