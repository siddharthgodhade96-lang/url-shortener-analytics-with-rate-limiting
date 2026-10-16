import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const apiKey = __ENV.API_KEY;
const capacity = Number(__ENV.BUCKET_CAPACITY || 10);
const refillRate = Number(__ENV.BUCKET_REFILL_RATE || (10 / 60));
const burstSize = capacity + Number(__ENV.BURST_EXTRA_REQUESTS || 2);

if (!apiKey) {
  throw new Error('Set API_KEY to a dedicated API key whose URL-creation bucket is full');
}
if (!Number.isInteger(capacity) || capacity < 1 || !Number.isFinite(refillRate) || refillRate <= 0) {
  throw new Error('BUCKET_CAPACITY must be a positive integer and BUCKET_REFILL_RATE must be positive');
}

const burstAccepted = new Counter('burst_accepted_201');
const burstRejected = new Counter('burst_rejected_429');
const refillAccepted = new Counter('refill_accepted_201');
const refillRejected = new Counter('refill_rejected_429');
const unexpectedResponses = new Counter('unexpected_burst_responses');

export const options = {
  scenarios: {
    token_bucket_burst: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '2m',
    },
  },
};

function createParams() {
  return {
    headers: {
      'Content-Type': 'application/json',
      'X-API-Key': apiKey,
    },
  };
}

function recordResponse(response, stage) {
  const expected = response.status === 201 || response.status === 429;
  check(response, {
    [`${stage} request returns HTTP 201 or 429`]: () => expected,
  });

  if (response.status === 201) {
    (stage === 'burst' ? burstAccepted : refillAccepted).add(1);
  } else if (response.status === 429) {
    (stage === 'burst' ? burstRejected : refillRejected).add(1);
  } else {
    unexpectedResponses.add(1);
  }
}

export default function () {
  const url = `${baseUrl}/api/urls`;
  const body = JSON.stringify({ originalUrl: 'https://example.com/k6-burst-test' });
  const burst = http.batch(
    Array.from({ length: burstSize }, () => ['POST', url, body, createParams()]),
  );
  burst.forEach((response) => recordResponse(response, 'burst'));

  sleep((1 / refillRate) + 0.5);

  const refilled = http.post(url, body, createParams());
  recordResponse(refilled, 'refill');
}

function metricValues(data, name) {
  return data.metrics[name] ? data.metrics[name].values : {};
}

export function handleSummary(data) {
  const summary = {
    requestsPerSecond: metricValues(data, 'http_reqs').rate || 0,
    p95LatencyMs: metricValues(data, 'http_req_duration')['p(95)'] || 0,
    burstAccepted201: metricValues(data, 'burst_accepted_201').count || 0,
    burstRejected429: metricValues(data, 'burst_rejected_429').count || 0,
    afterRefillAccepted201: metricValues(data, 'refill_accepted_201').count || 0,
    afterRefillRejected429: metricValues(data, 'refill_rejected_429').count || 0,
    unexpectedResponses: metricValues(data, 'unexpected_burst_responses').count || 0,
  };
  return { stdout: `${JSON.stringify(summary, null, 2)}\n` };
}
