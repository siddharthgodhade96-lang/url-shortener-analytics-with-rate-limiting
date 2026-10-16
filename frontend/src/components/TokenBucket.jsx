import { useEffect, useState } from 'react';

// Shows the token bucket that limits how fast this API key can create links.
// The server tells us how many tokens were left at one moment; between checks we
// add the refilled tokens ourselves so the meter keeps moving.
function currentTokens(usage, now) {
  const seconds = Math.max(0, (now - usage.fetchedAt) / 1000);
  return Math.min(usage.capacity, usage.remainingTokens + seconds * usage.refillRate);
}

export default function TokenBucket({ usage }) {
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 250);
    return () => clearInterval(timer);
  }, []);

  if (!usage) {
    return (
      <section className="panel" aria-label="Link creation allowance">
        <h2>Creation allowance</h2>
        <p className="muted">Loading…</p>
      </section>
    );
  }

  const tokens = currentTokens(usage, now);
  const whole = Math.floor(tokens + 1e-9);
  const full = tokens >= usage.capacity - 1e-9;
  const secondsToNext = full ? 0 : Math.ceil((whole + 1 - tokens) / usage.refillRate);
  const secondsPerToken = Math.round(1 / usage.refillRate);
  const showSegments = usage.capacity <= 30;

  let status;
  if (whole === 0) {
    status = `No links left right now. The next one is ready in ${secondsToNext}s.`;
  } else if (full) {
    status = `${whole} of ${usage.capacity} links available.`;
  } else {
    status = `${whole} of ${usage.capacity} links available. Next one in ${secondsToNext}s.`;
  }

  return (
    <section className="panel" aria-label="Link creation allowance">
      <h2>Creation allowance</h2>
      {showSegments ? (
        <div className="bucket" role="img" aria-label={status}>
          {Array.from({ length: usage.capacity }, (_, index) => {
            const fill = Math.min(1, Math.max(0, tokens - index));
            return (
              <span className="bucket-slot" key={index}>
                <span className="bucket-fill" style={{ transform: `scaleY(${fill})` }} />
              </span>
            );
          })}
        </div>
      ) : (
        <div className="bar" role="img" aria-label={status}>
          <span style={{ width: `${(tokens / usage.capacity) * 100}%` }} />
        </div>
      )}
      <p className="bucket-status">{status}</p>
      <p className="muted small">
        Each new link uses one token. This key gets one token back every {secondsPerToken} seconds,
        up to {usage.capacity}.
      </p>
    </section>
  );
}
