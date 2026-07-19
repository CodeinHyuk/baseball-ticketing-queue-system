# Global Agent Rules & Project Context

## 1. Project Overview
- **Name**: baseball-ticketing-queue-system
- **Description**: Spring WebFlux 및 Redis 기반 야구 티켓팅 접속자 대기열 시스템
- **Goal**: 티켓 오픈 시 발생하는 순간적인 대규모 트래픽으로부터 후속 시스템을 보호하고, 사용자 진입 속도를 제어하는 대기열 아키텍처 구축
- **Core Stack**: Java 17, Spring Boot 3.x, Spring WebFlux (Reactive), Spring Data Reactive Redis

---

## 2. Agent Workflow Rules (Superpowers & CodeGraph)
- **Think & CodeGraph First**: 코드를 수정하거나 새로운 기능을 개발하기 전에, 반드시 `CodeGraph`를 통해 현재 클래스 구조, 데이터 흐름, 의존성 지도를 먼저 파악하고 구현 계획을 수립하세요.
- **Surgical Changes**: 기존 코드를 수정할 때는 불필요한 라인까지 광범위하게 수정하지 말고, 목표하는 비즈니스 로직만 정밀하게 타격하여 수정하세요.
- **Write Tests First (TDD)**: 새로운 비즈니스 로직(예: 대기열 진입 조건, 만료 시간 처리)을 작성할 때는 가능한 한 비동기 테스트 코드(`StepVerifier` 활용)를 먼저 설계하세요.

---

## 3. Technology-Specific Guidelines (Crucial ⚠️)

### A. Spring WebFlux (Reactive Programming)
- **Never Block**: Reactive Stream(Flux/Mono) 체인 내부에서 절대 블로킹 API(예: `Thread.sleep()`, `RestTemplate`, 일반 JDBC 라이브러리 호출 등)를 사용하지 마세요. 
- **Return Reactive Types**: 네트워크, Redis 등 비동기 I/O가 포함된 Controller와 Service 경로에서는 `Mono<T>` 또는 `Flux<T>`를 반환하세요. 순수 계산이나 설정 조회처럼 즉시 완료되는 내부 로직을 불필요하게 Reactive 타입으로 감싸지 마세요.
- **Log Responsibly**: 리액티브 흐름 내에서 로깅할 때는 동기식 로거 대신 `.doOnNext()`, `.doOnError()` 등의 스트림 연산자 내부에서 로깅을 수행하세요.

### B. Redis Sorted Set (Queue Management)
- **Sorted Set Usage**: 대기열은 Redis의 Sorted Set(ZSET)을 사용합니다.
  - **Key**: `queue:baseball:waiting` (대기열 목록)
  - **Score**: 요청이 들어온 시간의 타임스탬프 (`System.currentTimeMillis()`)
  - **Value**: 유저 고유 식별자 (예: UUID 또는 유저 ID)
- **Token / Active Queue**: 대기열에서 통과하여 실제 티켓팅 페이지로 진입 가능한 유저들은 활성화 키(`queue:baseball:active`)에 보관하거나 적절한 TTL(만료 시간)을 가진 토큰으로 관리하세요.

---

## 4. Definition of Done (검증 가이드)
작업을 완료했다고 선언하기 전에 아래 체크리스트를 자체적으로 검증하세요.
1. 작성한 코드에 빨간 줄(컴파일 에러)이 없는지 확인했는가?
2. WebFlux의 흐름이 끊기지 않고 Reactive Stream이 올바르게 리턴되었는가?
3. Redis 연결 설정 및 대기열 조회 시 예외 처리(Exception Handling)가 구현되었는가?

## 5. Agent Tools Guideline
- **Superpowers Active**: 이 프로젝트는 Superpowers 에이전트 기반으로 구동됩니다. 자율적으로 서브 에이전트(Sub-agent)를 띄워 개발하거나, 계획 수립 및 디버깅을 적극적으로 수행하세요.
- **CodeGraph Engine**: 전체 코드 의존성과 흐름은 항상 로컬에 구축된 `.codegraph` 분석 데이터를 기반으로 매핑하여 컨텍스트를 파악하세요.