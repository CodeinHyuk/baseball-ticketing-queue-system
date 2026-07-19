package com.baseball.queue.followup.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/follow-up")
public class FollowUpAccessController {

    @GetMapping("/access")
    public Mono<Map<String, String>> access(@AuthenticationPrincipal String userId) {
        return Mono.just(Map.of("userId", userId, "status", "GRANTED"));
    }
}
