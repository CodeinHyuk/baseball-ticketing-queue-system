# 2026-07-15 Project Setup Design

This document details the initial setup of the `baseball-ticketing-queue-system` project using Spring Boot, Spring WebFlux, Gradle, and Reactive Redis.

## Technical Specifications

### Tech Stack
- **Language**: Java 17
- **Framework**: Spring Boot 3.3.1
- **Build Tool**: Gradle (Groovy DSL)
- **Primary Dependencies**:
  - `org.springframework.boot:spring-boot-starter-webflux`
  - `org.springframework.boot:spring-boot-starter-data-redis-reactive`
  - `org.projectlombok:lombok`
  - `com.github.codemonkeyyips:embedded-redis:0.9.0` (for Windows-compatible local testing)
  - `org.springframework.boot:spring-boot-starter-test`
  - `io.projectreactor:reactor-test`

### Package Structure (`com.baseball.queue`)
We use a domain-driven / feature-based package structure to cleanly separate the queue state transitions:

- `com.baseball.queue`
  - `QueueApplication.java` (Spring Boot Main Class)
  - `global`
    - `config` (Redis Configuration, Embedded Redis Configuration)
    - `error` (Global Exception Handlers)
  - `waiting`
    - `controller` (Waiting Queue Web APIs)
    - `service` (Waiting Queue business logic using Redis Sorted Set)
    - `repository` (Reactive Redis connection & commands helper)
  - `active`
    - `controller` (Active Session Web APIs)
    - `service` (Active Queue transition and token validation)
    - `repository` (Active token storage & validation helper)

## Verification Plan
1. Check that the project compiles cleanly using `./gradlew build` (or similar compile tasks).
2. Verify that Embedded Redis starts correctly on Windows and that the Spring Boot Application runs.
