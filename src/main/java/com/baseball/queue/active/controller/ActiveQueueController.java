package com.baseball.queue.active.controller;

import com.baseball.queue.active.service.ActiveQueueService;
import com.baseball.queue.global.util.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/queue/active")
@RequiredArgsConstructor
public class ActiveQueueController {

    private final ActiveQueueService activeQueueService;
    private final JwtProvider jwtProvider;

    // Redis 활성 상태 확인 후 JWT 발급
    @GetMapping("/token")
    public Mono<ResponseEntity<Map<String, String>>> getActiveToken(@RequestParam String userId) {
        return activeQueueService.checkActiveStatus(userId)
                .flatMap(isActive -> {
                    if (isActive) {
                        String token = jwtProvider.generateActiveToken(userId);
                        return Mono.just(ResponseEntity.ok(Map.of("token", token)));
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                });
    }
}