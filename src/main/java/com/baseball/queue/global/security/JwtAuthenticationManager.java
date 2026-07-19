package com.baseball.queue.global.security;

import com.baseball.queue.global.util.JwtProvider;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtProvider jwtProvider;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = String.valueOf(authentication.getCredentials());
        try {
            JwtProvider.TokenClaims claims = jwtProvider.validate(token);
            return Mono.just(new UsernamePasswordAuthenticationToken(
                    claims.userId(),
                    token,
                    List.of(new SimpleGrantedAuthority("ROLE_" + claims.role()))));
        } catch (JwtException | IllegalArgumentException e) {
            return Mono.error(new BadCredentialsException("Invalid active token", e));
        }
    }
}
