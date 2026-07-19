package com.baseball.queue.global.util;

import com.baseball.queue.global.config.JwtProperties;
import com.baseball.queue.global.config.QueueProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtProvider {

    private static final String ACTIVE_USER_ROLE = "ACTIVE_USER";
    private static final Set<String> ALLOWED_ROLES = Set.of(ACTIVE_USER_ROLE, "WAITING_USER");

    private final JwtProperties jwtProperties;
    private final QueueProperties queueProperties;
    private Key key;

    @PostConstruct
    void initializeKey() {
        if (jwtProperties.getSecret() == null || jwtProperties.getSecret().isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be configured");
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET must be Base64 encoded", e);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 256 bits");
        }
        key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateActiveToken(String userId) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + queueProperties.getAdmission().getActiveTtlMs());

        return Jwts.builder()
                .setSubject(userId)
                .setIssuer(jwtProperties.getIssuer())
                .setAudience(jwtProperties.getAudience())
                .claim("role", ACTIVE_USER_ROLE)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key)
                .compact();
    }

    public TokenClaims validate(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        if (!jwtProperties.getIssuer().equals(claims.getIssuer())
                || !jwtProperties.getAudience().equals(claims.getAudience())) {
            throw new JwtException("Unexpected token issuer or audience");
        }

        String role = claims.get("role", String.class);
        if (claims.getSubject() == null || !ALLOWED_ROLES.contains(role)) {
            throw new JwtException("Invalid token claims");
        }
        return new TokenClaims(claims.getSubject(), role);
    }

    public record TokenClaims(String userId, String role) {
    }
}
