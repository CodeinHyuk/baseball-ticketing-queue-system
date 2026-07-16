package com.baseball.queue.waiting.controller;

import com.baseball.queue.waiting.dto.WaitingRankResponse;
import com.baseball.queue.waiting.dto.WaitingRegisterRequest;
import com.baseball.queue.waiting.service.WaitingQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/queue")
@RequiredArgsConstructor
public class WaitingQueueController {

    private final WaitingQueueService waitingQueueService;

    @PostMapping("/register")
    public Mono<WaitingRankResponse> register(@RequestBody WaitingRegisterRequest request) {
        return waitingQueueService.registerQueue(request.userId());
    }

    @GetMapping("/rank")
    public Mono<WaitingRankResponse> checkRank(@RequestParam String userId) {
        return waitingQueueService.checkRank(userId);
    }
    
    @DeleteMapping("/dropout")
    public Mono<Void> dropout(@RequestParam String userId) {
        return waitingQueueService.dropout(userId);
    }
}
