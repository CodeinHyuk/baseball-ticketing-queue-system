package com.baseball.queue.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationManager jwtAuthenticationManager;

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/api/v1/follow-up/**").hasRole("ACTIVE_USER")
                        .anyExchange().permitAll())
                .addFilterAt(jwtAuthenticationWebFilter(), SecurityWebFiltersOrder.AUTHENTICATION)
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((exchange, e) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        })
                        .accessDeniedHandler((exchange, e) -> {
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        }))
                .build();
    }

    private AuthenticationWebFilter jwtAuthenticationWebFilter() {
        AuthenticationWebFilter filter = new AuthenticationWebFilter(jwtAuthenticationManager);
        filter.setRequiresAuthenticationMatcher(
                new PathPatternParserServerWebExchangeMatcher("/api/v1/follow-up/**"));
        filter.setServerAuthenticationConverter(exchange -> {
            String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                return Mono.empty();
            }
            String token = authorization.substring("Bearer ".length()).trim();
            if (token.isEmpty()) {
                return Mono.empty();
            }
            return Mono.just(new UsernamePasswordAuthenticationToken(null, token));
        });
        filter.setAuthenticationFailureHandler(new ServerAuthenticationEntryPointFailureHandler(
                (exchange, e) -> {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }));
        return filter;
    }
}
