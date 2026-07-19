package com.baseball.queue.active.controller;

import com.baseball.queue.active.dto.ActiveTokenRequest;
import com.baseball.queue.active.dto.ActiveTokenResponse;
import com.baseball.queue.active.service.ActiveQueueService;
import com.baseball.queue.global.util.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/queue/active")
@RequiredArgsConstructor
public class ActiveQueueController {

    private final ActiveQueueService activeQueueService;
    private final JwtProvider jwtProvider;

    @PostMapping("/token")
    public Mono<ResponseEntity<ActiveTokenResponse>> getActiveToken(@RequestBody ActiveTokenRequest request) {
        if (request.userId() == null || request.userId().isBlank()
                || request.queueCredential() == null || request.queueCredential().isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return activeQueueService.exchangeActiveCredential(request.userId(), request.queueCredential())
                .map(isActive -> isActive
                        ? ResponseEntity.ok(new ActiveTokenResponse(jwtProvider.generateActiveToken(request.userId())))
                        : ResponseEntity.status(403).build());
    }
}
