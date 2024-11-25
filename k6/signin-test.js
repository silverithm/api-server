import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

export const options = {
    scenarios: {
        stress: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '1m', target: 50 },     // 1분동안 50명까지
                { duration: '1m', target: 100 },    // 1분동안 100명까지
                { duration: '1m', target: 0 },      // 1분동안 0명으로
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],
    },
};

export default function() {
    const payload = JSON.stringify({
        email: "ggprgrkjh@naver.com",
        password: "rlawnfpr12"
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const response = http.post('https://silverithm.site/api/v1/signin', payload, params);

    // 응답 내용 로깅
    console.log(`Response: ${response.body}`);

    const success = check(response, {
        'status is 200': r => r.status === 200,
        'has response body': r => r.body.length > 0,
        'response time OK': r => r.timings.duration < 500,
    });

    if (!success) {
        console.log(`Failed Response: Status=${response.status}, Body=${response.body}`);
    }

    sleep(1);
}