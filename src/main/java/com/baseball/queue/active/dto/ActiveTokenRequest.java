package com.baseball.queue.active.dto;

public record ActiveTokenRequest(
        String userId,
        String queueCredential
) {
}
