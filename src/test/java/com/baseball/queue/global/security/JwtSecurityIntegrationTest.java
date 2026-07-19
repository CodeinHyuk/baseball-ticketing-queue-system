package com.baseball.queue.global.security;

import com.baseball.queue.active.dto.ActiveTokenRequest;
import com.baseball.queue.active.dto.ActiveTokenResponse;
import com.baseball.queue.global.config.JwtProperties;
import com.baseball.queue.global.redis.QueueRedisKeys;
import com.baseball.queue.global.util.JwtProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Date;

@SpringBootTest
@ActiveProfiles("test")
class JwtSecurityIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JwtProperties jwtProperties;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
    }

    @Test
    @DisplayName("활성 credential로 발급한 JWT는 active Redis 키가 없어도 후속 보호 API에 접근한다")
    void validJwtAccessesProtectedApiWithoutActiveRedisLookup() {
        String userId = "active_user";
        String credential = "credential-value";
        reactiveRedisTemplate.opsForValue().set(QueueRedisKeys.queueCredentialKey(userId), credential).block();
        reactiveRedisTemplate.opsForValue().set(QueueRedisKeys.activeUserKey(userId), "true").block();

        ActiveTokenResponse response = webTestClient.post()
                .uri("/api/v1/queue/active/token")
                .bodyValue(new ActiveTokenRequest(userId, credential))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ActiveTokenResponse.class)
                .returnResult()
                .getResponseBody();

        reactiveRedisTemplate.delete(QueueRedisKeys.activeUserKey(userId)).block();

        webTestClient.get()
                .uri("/api/v1/follow-up/access")
                .headers(headers -> headers.setBearerAuth(response.token()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo(userId)
                .jsonPath("$.status").isEqualTo("GRANTED");
    }

    @Test
    @DisplayName("보호 API는 JWT가 없거나 변조되었거나 만료되면 접근을 차단한다")
    void missingTamperedAndExpiredTokensAreUnauthorized() {
        String validToken = jwtProvider.generateActiveToken("active_user");

        webTestClient.get().uri("/api/v1/follow-up/access")
                .exchange().expectStatus().isUnauthorized();

        webTestClient.get().uri("/api/v1/follow-up/access")
                .headers(headers -> headers.setBearerAuth(validToken + "tampered"))
                .exchange().expectStatus().isUnauthorized();

        webTestClient.get().uri("/api/v1/follow-up/access")
                .headers(headers -> headers.setBearerAuth(createToken("ACTIVE_USER", -120_000, -60_000)))
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("서명은 유효하지만 ACTIVE_USER 역할이 아닌 토큰은 보호 API에서 거부된다")
    void insufficientRoleTokenIsForbidden() {
        webTestClient.get().uri("/api/v1/follow-up/access")
                .headers(headers -> headers.setBearerAuth(createToken("WAITING_USER", 0, 60_000)))
                .exchange().expectStatus().isForbidden();
    }

    @Test
    @DisplayName("active 상태여도 queueCredential이 일치하지 않으면 JWT를 발급하지 않는다")
    void tokenExchangeRejectsInvalidCredential() {
        String userId = "credential_user";
        reactiveRedisTemplate.opsForValue().set(QueueRedisKeys.queueCredentialKey(userId), "correct").block();
        reactiveRedisTemplate.opsForValue().set(QueueRedisKeys.activeUserKey(userId), "true").block();

        webTestClient.post()
                .uri("/api/v1/queue/active/token")
                .bodyValue(new ActiveTokenRequest(userId, "incorrect"))
                .exchange()
                .expectStatus().isForbidden();
    }

    private String createToken(String role, long issuedAtOffsetMs, long expiresAtOffsetMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject("test_user")
                .setIssuer(jwtProperties.getIssuer())
                .setAudience(jwtProperties.getAudience())
                .claim("role", role)
                .setIssuedAt(new Date(now + issuedAtOffsetMs))
                .setExpiration(new Date(now + expiresAtOffsetMs))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret())))
                .compact();
    }
}
