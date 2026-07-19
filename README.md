# ⚾ 야구 티켓팅 접속자 대기열 시스템

Redis Sorted Set과 Spring WebFlux를 사용해 티켓 오픈 시점의 동시 접속자를 순차적으로 활성 상태로 전환하는 대기열 시스템입니다. 이 프로젝트는 대기열 상태의 일관성, 비정상 이탈 사용자 정리, 그리고 대기열 통과 후 보호 API 접근 제어에 초점을 뒀습니다.

## 기술 스택

- Java 17, Gradle
- Spring Boot 3.3, Spring WebFlux, Spring Security
- Spring Data Reactive Redis, Redis
- JWT (JJWT, HS256)
- JUnit 5, Reactor Test, k6

## 동작 흐름

1. `POST /api/v1/queue/register`가 waiting ZSET과 heartbeat ZSET을 원자적으로 생성합니다.
2. 순번 조회가 성공한 대기 사용자만 Heartbeat를 갱신합니다.
3. Heartbeat 스케줄러는 마지막 Heartbeat가 설정된 유효 시간보다 오래된 사용자를 제한된 배치로 정리합니다.
4. 입장 스케줄러는 설정된 배치 수만큼 waiting 사용자를 active 상태로 원자적으로 전환합니다.
5. active 상태와 등록 시 발급된 `queueCredential`을 확인한 뒤 10분 JWT를 발급합니다.
6. `/api/v1/follow-up/**` 보호 API는 Redis active 상태를 재조회하지 않고 JWT의 서명, 만료, issuer, audience, role을 로컬에서 검증합니다.

## Redis 상태와 일관성

| 상태 | Redis Key | 설명 |
| --- | --- | --- |
| 대기열 | `queue:baseball:waiting` | 사용자 ID와 최초 등록 시각을 저장하는 ZSET |
| Heartbeat | `queue:baseball:heartbeat` | 실제 대기 중인 사용자의 마지막 순번 조회 시각을 저장하는 ZSET |
| 활성 상태 | `queue:baseball:active:{userId}` | 활성 사용자 여부를 나타내는 TTL String Key |
| 대기열 자격 | `queue:baseball:credential:{userId}` | 활성 JWT 교환에 필요한 추측 불가능한 자격 값 |

등록, Heartbeat 갱신, 이탈, 만료 사용자 정리, 활성 전환은 Redis Lua Script로 처리합니다. 따라서 만료 사용자 목록을 읽은 뒤 별도 요청으로 삭제하던 이전 방식의 경쟁 조건을 피합니다.

### Heartbeat 정책

- 기본 Heartbeat 유효 시간: 30초
- 기본 정리 주기: 10초
- 기본 정리 배치 크기: 500명

기본 설정에서는 **30초 이상 Heartbeat가 갱신되지 않은 사용자**를 다음 정리 주기에 제거합니다. 스케줄 방식이므로 마지막 Heartbeat 이후 실제 제거 시점은 약 30~40초 범위가 될 수 있으며, 30초 이내 제거를 보장하지 않습니다.

### 입장 처리 정책

- 기본 입장 주기: 3초
- 기본 입장 배치: 100명
- active 상태 및 JWT 만료: 10분

위 처리량은 단일 애플리케이션 인스턴스의 기본 설정입니다. 다중 인스턴스 환경에서 전체 시스템의 입장 처리량을 하나로 제한하려면 분산 스케줄 제어가 추가로 필요합니다.

## API

| Method | Endpoint | 설명 |
| --- | --- | --- |
| POST | `/api/v1/queue/register` | 대기열 최초 등록. 최초 등록에만 `queueCredential`을 반환 |
| GET | `/api/v1/queue/rank?userId={userId}` | 순번 조회와 Heartbeat 갱신 |
| DELETE | `/api/v1/queue/dropout?userId={userId}` | waiting, heartbeat, credential 상태를 함께 제거 |
| POST | `/api/v1/queue/active/token` | active 상태와 `queueCredential`을 검증해 JWT 발급 |
| GET | `/api/v1/follow-up/access` | `ACTIVE_USER` JWT가 필요한 예시 보호 API |

### 등록

```http
POST /api/v1/queue/register
Content-Type: application/json

{"userId":"user_10"}
```

```json
{
  "userId": "user_10",
  "rank": 0,
  "estimatedWaitingCount": 1,
  "queueCredential": "b4c7..."
}
```

`queueCredential`은 최초 등록 응답에서만 반환됩니다. 클라이언트는 이를 안전하게 보관해 active JWT 교환에 사용해야 합니다. 이 프로젝트는 별도 사용자 로그인 시스템을 포함하지 않으므로, 실제 서비스에서는 사용자 인증 주체와 대기열 사용자를 연결하는 인증 계층이 추가로 필요합니다.

### 활성 JWT 교환

```http
POST /api/v1/queue/active/token
Content-Type: application/json

{
  "userId": "user_10",
  "queueCredential": "b4c7..."
}
```

active 상태가 아니거나 자격 값이 일치하지 않으면 `403 Forbidden`을 반환합니다.

### 보호 API 호출

```http
GET /api/v1/follow-up/access
Authorization: Bearer {active-jwt}
```

JWT에는 subject, `ACTIVE_USER` role, issuer, audience, issued-at, expiration이 포함됩니다. 보호 API는 active Redis Key를 다시 조회하지 않으므로, JWT가 발급된 뒤 active Key가 삭제되어도 유효 기간 내 토큰 자체는 검증 가능합니다.

## 설정

```yaml
queue:
  heartbeat:
    timeout-ms: 30000
    cleanup-interval-ms: 10000
    cleanup-batch-size: 500
  admission:
    interval-ms: 3000
    batch-size: 100
    active-ttl-ms: 600000
```

JWT 비밀키는 저장소에 넣지 않습니다. 운영 또는 로컬 실행 시 256비트 이상 Base64 인코딩 키를 `JWT_SECRET` 환경 변수로 제공해야 합니다.

```powershell
$env:JWT_SECRET = '<Base64-encoded-32-byte-or-longer-secret>'
./gradlew bootRun
```

## 테스트

```bash
./gradlew test
```

테스트는 다음을 검증합니다.

- 최초 등록 시 waiting, heartbeat, credential 상태 생성
- 중복 등록 시 최초 waiting score 유지
- 대기열에 없는 사용자의 Heartbeat 미생성
- 이탈 시 waiting, heartbeat, credential 동시 삭제
- 만료/비만료 사용자의 배치 정리
- Heartbeat 갱신 직후 정리 시 정상 사용자 유지
- active 전환 시 waiting과 heartbeat 제거
- 유효, 누락, 변조, 만료, 권한 부족 JWT의 보호 API 동작
- active Key 삭제 후에도 후속 보호 API가 Redis active 상태에 의존하지 않는지

## 한계와 후속 작업

- 사용자 로그인/신원 검증 도메인은 포함하지 않습니다. `queueCredential`은 대기열 자격을 위한 소유 증명이며 실제 사용자 인증을 대체하지 않습니다.
- 다중 인스턴스 환경에서 전체 입장량을 단일 값으로 제한하려면 Redis 분산 락 또는 리더 선출이 필요합니다.
- JWT 발급 후 즉시 강제 철회가 필요한 요구사항에는 별도 deny-list 또는 짧은 만료 시간 정책이 필요합니다.
- 메모리 사용량, Redis Read 감소율, 예상 대기시간 오차, 평균 응답시간은 현재 재현 가능한 비교 측정 결과가 없으므로 수치로 주장하지 않습니다.
