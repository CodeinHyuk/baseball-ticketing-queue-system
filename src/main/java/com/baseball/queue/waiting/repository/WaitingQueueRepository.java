package com.baseball.queue.waiting.repository;

import lombok.RequiredArgsConstructor;
import com.baseball.queue.global.redis.QueueRedisKeys;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Mono;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class WaitingQueueRepository {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    private static final DefaultRedisScript<Long> REGISTER_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('ZSCORE', KEYS[1], ARGV[1]) then
                redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1])
                return 0
            end
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
            redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1])
            redis.call('SET', KEYS[3], ARGV[3])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> REFRESH_HEARTBEAT_SCRIPT = new DefaultRedisScript<>("""
            if not redis.call('ZSCORE', KEYS[1], ARGV[1]) then
                return 0
            end
            redis.call('ZADD', KEYS[2], ARGV[2], ARGV[1])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> REMOVE_USER_SCRIPT = new DefaultRedisScript<>("""
            local removed = redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            redis.call('DEL', KEYS[3])
            return removed
            """, Long.class);

    private static final DefaultRedisScript<Long> REMOVE_EXPIRED_USERS_SCRIPT = new DefaultRedisScript<>("""
            local users = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2])
            local removed = 0
            for _, userId in ipairs(users) do
                redis.call('ZREM', KEYS[2], userId)
                if redis.call('ZREM', KEYS[1], userId) == 1 then
                    removed = removed + 1
                end
                redis.call('DEL', ARGV[3] .. userId)
            end
            return removed
            """, Long.class);

    private static final DefaultRedisScript<Long> PROMOTE_WAITING_USERS_SCRIPT = new DefaultRedisScript<>("""
            local users = redis.call('ZRANGE', KEYS[1], 0, tonumber(ARGV[1]) - 1)
            local promoted = 0
            for _, userId in ipairs(users) do
                if redis.call('ZREM', KEYS[1], userId) == 1 then
                    redis.call('ZREM', KEYS[2], userId)
                    redis.call('SET', ARGV[2] .. userId, 'true', 'PX', ARGV[4])
                    redis.call('PEXPIRE', ARGV[3] .. userId, ARGV[4])
                    promoted = promoted + 1
                end
            end
            return promoted
            """, Long.class);

    // ZRANK: 유저의 0-based 순번 조회
    public Mono<Long> getRank(String queueKey, String userId) {
        return reactiveRedisTemplate.opsForZSet().rank(queueKey, userId);
    }

    // ZCARD: 전체 대기열 크기 조회
    public Mono<Long> getQueueSize(String queueKey) {
        return reactiveRedisTemplate.opsForZSet().size(queueKey);
    }

    public Mono<Boolean> registerWaitingUser(String userId, long timestamp, String queueCredential) {
        return execute(REGISTER_SCRIPT,
                List.of(QueueRedisKeys.WAITING_QUEUE, QueueRedisKeys.HEARTBEAT, QueueRedisKeys.queueCredentialKey(userId)),
                List.of(userId, Long.toString(timestamp), queueCredential))
                .map(created -> created == 1L);
    }

    public Mono<Boolean> refreshHeartbeatIfWaiting(String userId, long timestamp) {
        return execute(REFRESH_HEARTBEAT_SCRIPT,
                List.of(QueueRedisKeys.WAITING_QUEUE, QueueRedisKeys.HEARTBEAT),
                List.of(userId, Long.toString(timestamp)))
                .map(updated -> updated == 1L);
    }

    public Mono<Long> removeWaitingUser(String userId) {
        return execute(REMOVE_USER_SCRIPT,
                List.of(QueueRedisKeys.WAITING_QUEUE, QueueRedisKeys.HEARTBEAT, QueueRedisKeys.queueCredentialKey(userId)),
                List.of(userId));
    }

    public Mono<Long> removeExpiredWaitingUsers(long thresholdTimestamp, long batchSize) {
        return execute(REMOVE_EXPIRED_USERS_SCRIPT,
                List.of(QueueRedisKeys.WAITING_QUEUE, QueueRedisKeys.HEARTBEAT),
                List.of(Long.toString(thresholdTimestamp), Long.toString(batchSize), QueueRedisKeys.queueCredentialPrefix()));
    }

    public Mono<Long> promoteWaitingUsers(long batchSize, long activeTtlMs) {
        return execute(PROMOTE_WAITING_USERS_SCRIPT,
                List.of(QueueRedisKeys.WAITING_QUEUE, QueueRedisKeys.HEARTBEAT),
                List.of(
                        Long.toString(batchSize),
                        QueueRedisKeys.activeUserPrefix(),
                        QueueRedisKeys.queueCredentialPrefix(),
                        Long.toString(activeTtlMs)));
    }

    private Mono<Long> execute(DefaultRedisScript<Long> script, List<String> keys, List<String> arguments) {
        return reactiveRedisTemplate.execute(script, keys, arguments)
                .single(0L);
    }
}
