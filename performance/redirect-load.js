import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const coldCodes = (__ENV.COLD_CODES || '').split(',').map((code) => code.trim()).filter(Boolean);
const warmCode = __ENV.WARM_CODE;
const warmRate = Number(__ENV.WARM_RATE || 20);
const warmDuration = __ENV.WARM_DURATION || '30s';

if (coldCodes.length === 0 || !warmCode) {
  throw new Error('Set COLD_CODES to comma-separated uncached codes and WARM_CODE to a pre-warmed code');
}

const coldLatency = new Trend('cache_cold_latency', true);
const warmLatency = new Trend('cache_warm_latency', true);
const successfulRedirects = new Counter('successful_redirects');
const unexpectedResponses = new Counter('unexpected_redirect_responses');

export const options = {
  scenarios: {
    cold_start: {
      executor: 'per-vu-iterations',
      vus: coldCodes.length,
      iterations: 1,
      maxDuration: '2m',
      exec: 'coldStart',
    },
    warm_cache: {
      executor: 'constant-arrival-rate',
      startTime: '2s',
      rate: warmRate,
      timeUnit: '1s',
      duration: warmDuration,
      preAllocatedVUs: 10,
      maxVUs: 50,
      exec: 'warmCache',
    },
  },
};

function requestRedirect(code, phase, latency) {
  const response = http.get(`${baseUrl}/${encodeURIComponent(code)}`, {
    redirects: 0,
    tags: { phase },
  });
  latency.add(response.timings.duration);
  const redirected = check(response, {
    'redirect returns HTTP 302': (result) => result.status === 302,
  });
  if (redirected) {
    successfulRedirects.add(1, { phase });
  } else {
    unexpectedResponses.add(1, { phase });
  }
}

export function coldStart() {
  requestRedirect(coldCodes[__VU - 1], 'cold', coldLatency);
}

export function warmCache() {
  requestRedirect(warmCode, 'warm', warmLatency);
}

function metricValues(data, name) {
  return data.metrics[name] ? data.metrics[name].values : {};
}

export function handleSummary(data) {
  const summary = {
    requestsPerSecond: metricValues(data, 'http_reqs').rate || 0,
    cold: {
      requests: metricValues(data, 'cache_cold_latency').count || 0,
        p95LatencyMs: metricValues(data, 'cache_cold_latency')['p(95)'] || 0,
    },
    warm: {
      requests: metricValues(data, 'cache_warm_latency').count || 0,
      p95LatencyMs: metricValues(data, 'cache_warm_latency')['p(95)'] || 0,
    },
    successfulRedirects: metricValues(data, 'successful_redirects').count || 0,
    unexpectedResponses: metricValues(data, 'unexpected_redirect_responses').count || 0,
  };
  return { stdout: `${JSON.stringify(summary, null, 2)}\n` };
}
