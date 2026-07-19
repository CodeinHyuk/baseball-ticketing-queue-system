package com.baseball.queue.global.redis;

public final class QueueRedisKeys {

    public static final String WAITING_QUEUE = "queue:baseball:waiting";
    public static final String HEARTBEAT = "queue:baseball:heartbeat";
    private static final String ACTIVE_PREFIX = "queue:baseball:active:";
    private static final String CREDENTIAL_PREFIX = "queue:baseball:credential:";

    private QueueRedisKeys() {
    }

    public static String activeUserKey(String userId) {
        return ACTIVE_PREFIX + userId;
    }

    public static String queueCredentialKey(String userId) {
        return CREDENTIAL_PREFIX + userId;
    }

    public static String activeUserPrefix() {
        return ACTIVE_PREFIX;
    }

    public static String queueCredentialPrefix() {
        return CREDENTIAL_PREFIX;
    }
}
