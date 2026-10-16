import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const apiKey = __ENV.API_KEY;
const requestRate = Number(__ENV.CREATE_RATE || 20);
const duration = __ENV.CREATE_DURATION || '30s';

if (!apiKey) {
  throw new Error('Set API_KEY to a dedicated API key with an unused creation bucket');
}

const created = new Counter('created_urls');
const rateLimited = new Counter('rate_limited_creates');
const unexpectedResponses = new Counter('unexpected_create_responses');

export const options = {
  scenarios: {
    url_creation: {
      executor: 'constant-arrival-rate',
      rate: requestRate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: 10,
      maxVUs: 100,
    },
  },
};

export default function () {
  const response = http.post(
    `${baseUrl}/api/urls`,
    JSON.stringify({ originalUrl: 'https://example.com/k6-load-test' }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-API-Key': apiKey,
      },
    },
  );

  const expected = response.status === 201 || response.status === 429;
  check(response, {
    'creation returns HTTP 201 or rate limit 429': () => expected,
  });

  if (response.status === 201) {
    created.add(1);
  } else if (response.status === 429) {
    rateLimited.add(1);
  } else {
    unexpectedResponses.add(1);
  }
}

function metricValues(data, name) {
  return data.metrics[name] ? data.metrics[name].values : {};
}

export function handleSummary(data) {
  const summary = {
    requestsPerSecond: metricValues(data, 'http_reqs').rate || 0,
    p95LatencyMs: metricValues(data, 'http_req_duration')['p(95)'] || 0,
    created201: metricValues(data, 'created_urls').count || 0,
    rateLimited429: metricValues(data, 'rate_limited_creates').count || 0,
    unexpectedResponses: metricValues(data, 'unexpected_create_responses').count || 0,
  };
  return { stdout: `${JSON.stringify(summary, null, 2)}\n` };
}
