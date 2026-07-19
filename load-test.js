import http from 'k6/http';
import { check, sleep } from 'k6';

// 테스트 목표 및 임계값 설정
export const options = {
    stages: [
        { duration: '10s', target: 50 }, // 10초 동안 가상 유저를 50명까지 점진적 증가
        { duration: '30s', target: 300 }, // 30초 동안 300명의 유저 유지 (최대 부하 구간)
        { duration: '10s', target: 0 },   // 10초 동안 유저를 0명으로 서서히 감소
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],   // 전체 요청 중 실패율 1% 미만 목표
        http_req_duration: ['p(95)<200'], // 하위 95%의 응답 속도가 200ms 미만 목표
    },
};

export default function () {
    // 고유한 가상 유저 ID 생성
    const userId = `user_${__VU}_${__ITER}`;
    const baseUrl = 'http://localhost:8080/api/v1/queue';

    // Step 1: 대기열 진입 요청 (POST)
    const registerPayload = JSON.stringify({ userId: userId });
    const registerHeaders = { headers: { 'Content-Type': 'application/json' } };
    const registerRes = http.post(`${baseUrl}/register`, registerPayload, registerHeaders);

    check(registerRes, {
        'Register Status is 200': (r) => r.status === 200,
    });

    sleep(1); // 실제 유저의 행동 패턴을 모방하기 위한 1초 대기

    // Step 2: 내 대기 순번 조회 및 하트비트 갱신 (GET)
    const rankRes = http.get(`${baseUrl}/rank?userId=${userId}`);

    check(rankRes, {
        'Rank Check Status is 200': (r) => r.status === 200,
    });

    sleep(1);
}