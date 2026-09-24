import http from 'k6/http';
import { check, sleep } from 'k6';

// Usage:
//   k6 run -e SHORT_CODE=abc123 k6/load-test.js
//   k6 run -e SHORT_CODE=abc123 -e BASE_URL=http://<ec2-ip>:8080 k6/load-test.js
//
// Run this TWICE for your "with vs without cache" numbers:
//   1. Right after creating the link + hitting it once (warms the Redis cache) -> "with cache"
//   2. After FLUSHALL on Redis (docker exec -it <redis-container> redis-cli FLUSHALL),
//      hitting a fresh link each iteration so every request is a cache miss -> "without cache"

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SHORT_CODE = __ENV.SHORT_CODE || 'REPLACE_ME';

export const options = {
  scenarios: {
    ramping_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 50 },   // ramp up to 50 concurrent users
        { duration: '40s', target: 50 },   // hold steady - this is your main measurement window
        { duration: '10s', target: 0 },    // ramp down
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<1000'], // fails the run if p95 > 1s; adjust as needed
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const res = http.get(`${BASE_URL}/r/${SHORT_CODE}`, {
    redirects: 0, // don't follow the 302; we're measuring the redirect response itself
  });

  check(res, {
    'status is 302': (r) => r.status === 302,
  });

  sleep(0.1);
}
