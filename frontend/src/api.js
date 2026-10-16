// All calls to the Spring Boot backend live in this one file.

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');

// The backend returns short links like "/abc12". They open on the backend, not here.
export const SHORT_LINK_BASE = (
  import.meta.env.VITE_SHORT_LINK_BASE_URL || 'http://localhost:8080'
).replace(/\/$/, '');

export const fullShortUrl = (link) => `${SHORT_LINK_BASE}${link.shortUrl}`;

export class ApiError extends Error {
  constructor(message, status = 0, retryAfter = null) {
    super(message);
    this.status = status;
    this.retryAfter = retryAfter;
  }
}

function describeError(status, data, retryAfter) {
  if (status === 401) {
    return 'The API key was not accepted. Check that you copied the whole key.';
  }
  if (status === 429) {
    const wait = retryAfter ? `${retryAfter} second${retryAfter === '1' ? '' : 's'}` : 'a little while';
    return `Too many requests. Try again in ${wait}.`;
  }
  if (data && data.message) {
    return data.message;
  }
  return `Something went wrong (HTTP ${status}).`;
}

async function request(path, { method = 'GET', apiKey, body, returnBodyOnError = false } = {}) {
  const headers = {};
  if (apiKey) headers['X-API-Key'] = apiKey;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(
      'Cannot reach the backend. Make sure it is running on http://localhost:8080.'
    );
  }

  const text = await response.text();
  let data = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = null;
    }
  }

  if (!response.ok && !returnBodyOnError) {
    const retryAfter = response.headers.get('Retry-After');
    throw new ApiError(describeError(response.status, data, retryAfter), response.status, retryAfter);
  }
  return data;
}

export const api = {
  health: () => request('/api/health', { returnBodyOnError: true }),
  usage: (apiKey) => request('/api/keys/me/usage', { apiKey }),
  listUrls: (apiKey) => request('/api/urls', { apiKey }),
  createUrl: (apiKey, originalUrl, customAlias) =>
    request('/api/urls', {
      method: 'POST',
      apiKey,
      body: customAlias ? { originalUrl, customAlias } : { originalUrl },
    }),
  deactivateUrl: (apiKey, id) => request(`/api/urls/${id}`, { method: 'DELETE', apiKey }),
  urlStats: (apiKey, id) => request(`/api/urls/${id}/stats`, { apiKey }),
  dailyClicks: (apiKey, id, from, to) =>
    request(`/api/analytics/daily?urlId=${id}&from=${from}&to=${to}`, { apiKey }),
  summary: (apiKey) => request('/api/analytics/summary', { apiKey }),
};
