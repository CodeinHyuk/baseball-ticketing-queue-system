package com.baseball.queue.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "queue")
public class QueueProperties {

    private Heartbeat heartbeat = new Heartbeat();
    private Admission admission = new Admission();

    @Getter
    @Setter
    public static class Heartbeat {
        private long timeoutMs = 30_000;
        private long cleanupIntervalMs = 10_000;
        private long cleanupBatchSize = 500;
    }

    @Getter
    @Setter
    public static class Admission {
        private long intervalMs = 3_000;
        private long batchSize = 100;
        private long activeTtlMs = 600_000;
    }
}
