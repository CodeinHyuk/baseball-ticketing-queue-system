# ⚾ 야구 티켓팅 접속자 대기열 시스템 (Baseball Ticketing Queue System)

본 프로젝트는 대규모 트래픽 분출이 발생하는 야구 티켓팅 환경을 가상하여, 순간적인 대용량 트래픽(평소 대비 100배 이상) 진입 시 백엔드 핵심 시스템의 장애를 방지하고 진입 속도를 안정적으로 제어할 수 있도록 설계된 **Spring WebFlux 및 Redis 기반의 리액티브 대기열 아키텍처**입니다. 

테스팅 및 포트폴리오용으로 구축되었으며, 스레드 차단(Blocking)을 최소화하는 비동기 이벤트 루프 모델과 Redis의 인메모리 Sorted Set을 융합하여 고성능·저비용 대기열 제어 엔진을 구현했습니다.

---

## 🛠️ Tech Stack & 핵심 기술

*   **Language**: Java 17
*   **Framework**: Spring Boot 3.x, Spring WebFlux (Reactive Streams)
*   **Data Store**: Spring Data Reactive Redis (Lettuce 드라이버 기반 비동기 파이프라이닝)
*   **Authentication**: JSON Web Token (jjwt - HS256)
*   **Testing & Validation**: JUnit 5, Reactor Test (`StepVerifier`), Docker Redis Alpine Environment (로컬/테스트 독립 가동)
*   **Performance Testing**: k6 Load Testing

---

## 🏗️ 시스템 아키텍처 및 데이터 흐름

기존의 `Thread-per-request` 모델(Spring MVC)은 수만 명의 대기 사용자가 몰릴 경우 수많은 스레드 컨텍스트 스위칭 비용과 메모리 고갈로 서버가 무너지기 쉽습니다. 본 시스템은 단일 혹은 소수의 이벤트 루프 스레드만으로 수많은 커넥션을 처리하는 **Spring WebFlux** 구조를 채택하여 인프라 비용을 대폭 절감하고 대규모 연결을 유지합니다.

```
[클라이언트 진입]
       │
       ▼  (POST /api/v1/queue/register)
┌────────────────────────────────────────────────────────┐
│ 1. 대기열 등록 및 순번 조회                               │
│    - Redis Sorted Set (Key: queue:baseball:waiting)    │
│    - Score: 진입 타임스탬프 (FIFO 보장)                   │
└───────────────────────┬────────────────────────────────┘
                        │
                        ▼ (주기적 GET /api/v1/queue/rank 요청)
┌────────────────────────────────────────────────────────┐
│ 2. 대기 상태 유지 및 하트비트 갱신                         │
│    - Redis ZSET (Key: queue:baseball:heartbeat)        │
│    - Scheduler가 30초간 무응답 고스트 유저 자동 이탈 처리   │
└───────────────────────┬────────────────────────────────┘
                        │
                        ▼ (3초마다 Scheduler에 의한 100명씩 popMin 연산)
┌────────────────────────────────────────────────────────┐
│ 3. 활성 상태 전환 (Active User)                         │
│    - Redis String (Key: queue:baseball:active:{userId})│
│    - 10분간 유효한 TTL 설정                              │
└───────────────────────┬────────────────────────────────┘
                        │
                        ▼ (GET /api/v1/queue/active/token)
┌────────────────────────────────────────────────────────┐
│ 4. 검증 완료 및 JWT 진입 토큰 발급                         │
│    - 티켓팅 본 서버 통과용 일회성 암호화 토큰 제공         │
└────────────────────────────────────────────────────────┘
```

### 1. FIFO 대기열 관리 (`Redis Sorted Set`)
*   사용자가 대기열에 진입하면 요청 시점의 Unix 타임스탬프를 `Score`로, 사용자 ID를 `Value`로 하여 `queue:baseball:waiting` ZSET에 저장합니다. 중복 진입 방지를 위해 기존 랭크 존재 여부를 먼저 Reactive하게 조회 후 적재합니다.

### 2. 고스트 유저 감지 메커니즘 (`Heartbeat Pattern`)
*   사용자가 대기 페이지에서 이탈(브라우저 종료, 탭 닫기 등)하여 무의미하게 대기열 자리를 차지하는 '고스트 유저' 문제를 해결하기 위해 **Heartbeat Scheduler**를 도입했습니다.
*   사용자가 랭크 조회를 보낼 때마다 `queue:baseball:heartbeat`에 최신 타임스탬프를 갱신합니다.
*   `HeartbeatScheduler`는 10초마다 구동되며 현재 시간 기준 30초 이전까지 하트비트가 없는 사용자를 찾아 대기열과 하트비트 내역에서 일괄 삭제(`ZREM`)하여 실질 대기열의 무결성을 유지합니다.

### 3. 처리량 스로틀링 (`Active Queue Transition`)
*   `QueueScheduler`가 3초 주기(`fixedDelay = 3000`)로 가동되며, `ZPOPMIN` 연산을 통해 대기열 최상위 유저를 설정된 배치 사이즈(기본 100명)만큼 꺼내어 활성 유저 저장소(`queue:baseball:active:{userId}`)로 이동시키며 10분(`ACTIVE_TTL`)의 만료 시간을 부여합니다. 이를 통해 타깃 시스템이 감당 가능한 트래픽 속도를 완벽히 제어합니다.

---

## 🔌 API 명세서 (API Specification)

| HTTP Method | Endpoint | Description | Request Body / Param | Response Example |
| :--- | :--- | :--- | :--- | :--- |
| **POST** | `/api/v1/queue/register` | 대기열 최초 등록 및 상태 반환 | `{"userId": "user_10"}` | `{"userId":"user_10","rank":0,"estimatedWaitingCount":1}` |
| **GET** | `/api/v1/queue/rank` | 현재 대기 순번 조회 및 하트비트 갱신 | `?userId=user_10` | `{"userId":"user_10","rank":23,"estimatedWaitingCount":145}` |
| **GET** | `/api/v1/queue/active/token` | 활성 유저 확인 및 진입용 JWT 토큰 발급 | `?userId=user_10` | `{"token": "eyJhbGciOiJIUzI1NiJ9..."}` (미활성 시 403) |
| **DELETE** | `/api/v1/queue/dropout` | 대기열 사용자 자진 이탈 처리 | `?userId=user_10` | `Void (HTTP 200 OK)` |

---

## ⚡ 부하 테스트 결과 분석 (k6 Load Test)

시스템의 한계를 측정하고 안정성을 검증하기 위해 오픈소스 부하 테스트 도구인 `k6`를 활용하여 스트레스 테스트를 수행했습니다.

### 📊 테스트 시나리오 설정
*   **가상 사용자 (VUs)**: 최대 300명의 VU가 동시 루핑 수행
*   **테스트 기간**: 총 50초 지속 (3개의 램프업/다운 스테이지)
*   **주요 검증 지표**: `p(95)` 응답 시간 200ms 이하 유지율, HTTP 에러율 1% 미만

### 📈 k6 테스트 콘솔 출력 로그
```text
PS C:\Users\USER\development\baseball-ticketing-queue-system> k6 run load-test.js

          /\      Grafana   /‾‾/
     /\  /  \     |\  __   /  /
    /  \/    \    | |/ /  /   ‾‾\
   /          \   |   (  |  (‾)  |
  / __________ \  |_|\_\  \_____/ 


     execution: local
        script: load-test.js
        output: -

     scenarios: (100.00%) 1 scenario, 300 max VUs, 1m20s max duration (incl. graceful stop):
              * default: Up to 300 looping VUs for 50s over 3 stages (gracefulRampDown: 30s, gracefulStop: 30s)



  █ THRESHOLDS

    http_req_duration
    ✓ 'p(95)<200' p(95)=11.54ms

    http_req_failed
    ✓ 'rate<0.01' rate=0.00%


  █ TOTAL RESULTS

    checks_total.......: 7238    140.212439/s
    checks_succeeded...: 100.00% 7238 out of 7238
    checks_failed......: 0.00%   0 out of 7238

    ✓ Register Status is 200
    ✓ Rank Check Status is 200

    HTTP
    http_req_duration..............: avg=7.36ms min=1.07ms med=6.02ms max=344.5ms p(90)=9.65ms p(95)=11.54ms
      { expected_response:true }...: avg=7.36ms min=1.07ms med=6.02ms max=344.5ms p(90)=9.65ms p(95)=11.54ms
    http_req_failed................: 0.00%  0 out of 7238
    http_reqs......................: 7238   140.212439/s

    EXECUTION
    iteration_duration.............: avg=2.01s  min=2s      med=2.01s  max=2.36s    p(90)=2.01s   p(95)=2.02s
    iterations.....................: 3619   70.10622/s
    vus............................: 7      min=5         max=299
    vus_max........................: 300    min=300       max=300

    NETWORK
    data_received..................: 966 kB 19 kB/s
    data_sent......................: 982 kB 19 kB/s


running (0m51.6s), 000/300 VUs, 3619 complete and 0 interrupted iterations
default ✓ [======================================] 000/300 VUs  50s
```

### 🧐 테스트 결과 해석 및 시사점

1.  **압도적인 응답 성능 (Low Latency)**
    *   총 7,238건의 비동기 HTTP 요청을 소화하는 동안 **평균 응답 시간은 단 7.36ms**에 불과했습니다.
    *   특히 상위 95%의 요청을 나타내는 **`p(95)` 수치가 11.54ms**로 측정되어 설정한 성능 임계치(200ms) 대비 17배 이상 빠른 매우 뛰어난 응답 일관성을 증명했습니다.
2.  **결함률 0.00% (High Reliability)**
    *   대기열 진입(`register`) 및 순위 조회(`rank`) 요청 전체에서 단 하나의 실패도 없이 **100% 성공률**을 기록했습니다. 
    *   WebFlux 리액티브 스트림(`Mono`/`Flux`) 체인 내부에 블로킹 요소를 철저히 배제하고, Redis와의 통신 역시 non-blocking 커넥션을 활용했기 때문에 급격한 스레드 고갈 현상 없이 부하를 안정적으로 격리해 냈습니다.
3.  **효율적인 처리 메커니즘**
    *   Netty의 비동기 I/O 이벤트 루프 메커니즘 덕분에 최소한의 스레드 환경에서도 초당 140건(140.21 req/s) 이상의 대기열 랭킹 트래킹 연산이 병목 없이 가볍게 처리됨을 확인하였습니다.

---

## 🚀 시작 가이드 (Quick Start)

### Prerequisites
*   Java 17 JDK 이상
*   Docker Desktop (로컬 개발용 Redis 구동용)

### 1. Repository Clone 및 빌드
```bash
git clone [https://github.com/CodeinHyuk/baseball-ticketing-queue-system.git](https://github.com/CodeinHyuk/baseball-ticketing-queue-system.git)
cd baseball-ticketing-queue-system
./gradlew clean build
```

### 2. 로컬 Redis 컨테이너 및 어플리케이션 실행
```bash
# Docker를 통한 Redis 독립 환경 실행
docker run -d --name local-redis -p 6379:6379 redis:alpine

# Spring Boot 어플리케이션 실행
./gradlew bootRun
```

### 3. 단위 및 통합 테스트 실행
```bash
# StepVerifier를 통한 비동기 리액티브 흐름 검증 테스트 포함
./gradlew test
```

---

## 🔒 핵심 비즈니스 룰 및 검증 (Definition of Done)
1.  **Never Block**: 리액티브 체인 내부(`Service`, `Repository`, `Controller`)에서는 절대 `Thread.sleep()`이나 전통적인 동기식 Blocking API를 호출하지 않습니다.
2.  **Reactive Architecture**: 모든 엔드포인트와 내부 컴포넌트는 비동기 리액티브 타입을 보장하기 위해 `Mono<T>` 또는 `Flux<T>`를 명확히 반환합니다.
3.  **Idempotency & Session Protection**: 동일 유저의 중복 진입 요청에 대해 최초 시점의 랭크를 그대로 유지하며, 유효 만료 시간(Heartbeat 30초, Active Session 10분)을 정밀 제어하여 부정 진입 및 자원 낭비를 완벽 차단합니다.