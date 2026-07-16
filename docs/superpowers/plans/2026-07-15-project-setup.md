# Project Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Set up the basic Spring Boot WebFlux and Reactive Redis project structure with Groovy Gradle build configuration and Windows-compatible Embedded Redis.

**Architecture:** Create base package directories for `global`, `waiting`, and `active` domains. Configure Embedded Redis to run on `local` and `test` profiles to enable easy local and automated testing on Windows.

**Tech Stack:** Java 17, Spring Boot 3.3.1, Spring WebFlux, Reactive Redis, Lombok, Embedded Redis (codemonkeyyips fork)

---

### Task 1: Gradle Build Configuration

**Files:**
- Create: `build.gradle`
- Create: `settings.gradle`

- [ ] **Step 1: Create settings.gradle**
Create the `settings.gradle` file in the root directory to define the project name.
```groovy
rootProject.name = 'baseball-ticketing-queue-system'
```

- [ ] **Step 2: Create build.gradle**
Create the `build.gradle` file in the root directory with dependencies for Spring Boot 3.3.1, WebFlux, Reactive Redis, Lombok, and Embedded Redis (from JitPack repository to ensure Windows Java 17 compatibility).
```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.3.1'
    id 'io.spring.dependency-management' version '1.1.5'
}

group = 'com.baseball'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

configurations {
    compileOnly {
        extendsFrom annotationProcessor
    }
}

repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-data-redis-reactive'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    
    // Windows compatible embedded redis for testing and local run
    implementation 'com.github.codemonkeyyips:embedded-redis:0.9.0'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'io.projectreactor:reactor-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Commit settings & build gradle (if auto_commit enabled)**
Check `.agent/config.yml` for `auto_commit` setting. If `auto_commit: false`, skip commit and print: "Skipping commit (auto_commit: false)."

---

### Task 2: Directory Structure and Application Main

**Files:**
- Create: `src/main/resources/application.yml`
- Create: `src/main/java/com/baseball/queue/QueueApplication.java`

- [ ] **Step 1: Create src/main/resources/application.yml**
Create `application.yml` with basic configuration for Spring application name and Redis connection.
```yaml
spring:
  application:
    name: baseball-ticketing-queue-system
  data:
    redis:
      host: localhost
      port: 6379
```

- [ ] **Step 2: Create com.baseball.queue.QueueApplication**
Create the main boot application class at `src/main/java/com/baseball/queue/QueueApplication.java`.
```java
package com.baseball.queue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class QueueApplication {
    public static void main(String[] args) {
        SpringApplication.run(QueueApplication.class, args);
    }
}
```

- [ ] **Step 3: Create Package Directories**
Create empty directories for `global`, `waiting`, and `active` domains.
Directories:
  - `src/main/java/com/baseball/queue/global/config`
  - `src/main/java/com/baseball/queue/waiting/controller`
  - `src/main/java/com/baseball/queue/waiting/service`
  - `src/main/java/com/baseball/queue/waiting/repository`
  - `src/main/java/com/baseball/queue/active/controller`
  - `src/main/java/com/baseball/queue/active/service`
  - `src/main/java/com/baseball/queue/active/repository`

- [ ] **Step 4: Commit application configuration (if auto_commit enabled)**
Check `.agent/config.yml` for `auto_commit` setting. If `auto_commit: false`, skip commit and print: "Skipping commit (auto_commit: false)."

---

### Task 3: Redis Configuration and Application Test

**Files:**
- Create: `src/main/java/com/baseball/queue/global/config/EmbeddedRedisConfig.java`
- Create: `src/main/java/com/baseball/queue/global/config/RedisConfig.java`
- Create: `src/test/java/com/baseball/queue/QueueApplicationTests.java`

- [ ] **Step 1: Create EmbeddedRedisConfig.java**
Create `EmbeddedRedisConfig.java` to start an embedded Redis on startup for `local` and `test` profiles.
```java
package com.baseball.queue.global.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import redis.embedded.RedisServer;

import java.io.IOException;

@Configuration
@Profile({"local", "test"})
public class EmbeddedRedisConfig {

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    private RedisServer redisServer;

    @PostConstruct
    public void startRedis() throws IOException {
        redisServer = RedisServer.builder()
                .port(redisPort)
                .setting("maxmemory 128M")
                .build();
        redisServer.start();
    }

    @PreDestroy
    public void stopRedis() throws IOException {
        if (redisServer != null && redisServer.isActive()) {
            redisServer.stop();
        }
    }
}
```

- [ ] **Step 2: Create RedisConfig.java**
Create `RedisConfig.java` for defining `ReactiveRedisTemplate` bean.
```java
package com.baseball.queue.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.reactive.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        StringRedisSerializer valueSerializer = new StringRedisSerializer();

        RedisSerializationContext<String, String> serializationContext = RedisSerializationContext
                .<String, String>newSerializationContext(keySerializer)
                .value(valueSerializer)
                .hashKey(keySerializer)
                .hashValue(valueSerializer)
                .build();

        return new ReactiveRedisTemplate<>(factory, serializationContext);
    }
}
```

- [ ] **Step 3: Create QueueApplicationTests.java**
Create the basic boot context load test at `src/test/java/com/baseball/queue/QueueApplicationTests.java`.
```java
package com.baseball.queue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class QueueApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 4: Run the Context Load Test**
Run: `./gradlew test` (or `gradlew.bat test` on Windows)
Expected: Tests pass, showing successful Context Load and Redis startup.

- [ ] **Step 5: Commit config and tests (if auto_commit enabled)**
Check `.agent/config.yml` for `auto_commit` setting. If `auto_commit: false`, skip commit and print: "Skipping commit (auto_commit: false)."
