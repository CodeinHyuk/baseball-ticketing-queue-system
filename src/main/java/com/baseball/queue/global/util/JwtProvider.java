package com.baseball.queue.global.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtProvider {

    private final Key key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private final long TOKEN_VALIDITY_MS = 10 * 60 * 1000;

    // 활성 유저용 임시 진입 토큰 발급
    public String generateActiveToken(String userId) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + TOKEN_VALIDITY_MS);

        return Jwts.builder()
                .setSubject(userId)
                .claim("role", "ACTIVE_USER")
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key)
                .compact();
    }

    // 토큰 검증 및 userId 추출
    public String validateAndGetUserId(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }
}