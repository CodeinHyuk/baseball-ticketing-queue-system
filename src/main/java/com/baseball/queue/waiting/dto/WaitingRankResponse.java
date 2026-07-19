package com.baseball.queue.waiting.dto;

public record WaitingRankResponse(
    String userId,
    Long rank,
    Long estimatedWaitingCount,
    String queueCredential
) {
}
