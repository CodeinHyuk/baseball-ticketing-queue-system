package com.baseball.queue.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    private String secret;
    private String issuer = "baseball-ticketing-queue";
    private String audience = "baseball-ticketing-follow-up";
}
