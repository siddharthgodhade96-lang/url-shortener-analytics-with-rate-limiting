import { useCallback, useEffect, useState } from 'react';
import { api } from '../api.js';

function topEntries(map, limit) {
  return Object.entries(map || {})
    .sort((a, b) => b[1] - a[1])
    .slice(0, limit);
}

export default function Summary({ apiKey, version, onError }) {
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setSummary(await api.summary(apiKey));
    } catch (error) {
      onError(error);
    } finally {
      setLoading(false);
    }
  }, [apiKey, onError]);

  useEffect(() => {
    load();
    // Reload when the person refreshes a link's stats (version changes).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [apiKey, version]);

  const referrers = topEntries(summary && summary.clicksByReferrer, 5);
  const devices = topEntries(summary && summary.clicksByDeviceCategory, 3);
  const referrerMax = referrers.length ? referrers[0][1] : 1;
  const deviceTotal = devices.reduce((sum, [, count]) => sum + count, 0);

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>All your links</h2>
        <button type="button" className="button button-quiet" onClick={load} disabled={loading}>
          {loading ? 'Loading…' : 'Refresh'}
        </button>
      </div>

      {!summary ? (
        <p className="muted">Loading…</p>
      ) : summary.totalClicks === 0 ? (
        <p className="empty">No clicks recorded yet. Open one of your short links to see numbers here.</p>
      ) : (
        <div className="summary">
          <div className="figures">
            <div>
              <strong>{summary.totalClicks}</strong>
              <span>clicks across all links</span>
            </div>
          </div>

          <div>
            <h3>Where clicks came from</h3>
            <ul className="meter-list">
              {referrers.map(([label, count]) => (
                <li key={label}>
                  <span className="meter-label" title={label}>{label}</span>
                  <span className="meter-track">
                    <span style={{ width: `${(count / referrerMax) * 100}%` }} />
                  </span>
                  <span className="meter-value">{count}</span>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h3>Devices</h3>
            <div className="split" role="img" aria-label={devices.map(([name, count]) => `${name} ${count}`).join(', ')}>
              {devices.map(([name, count], index) => (
                <span key={name} className={`split-${index}`} style={{ flexGrow: count }} />
              ))}
            </div>
            <ul className="legend">
              {devices.map(([name, count], index) => (
                <li key={name}>
                  <span className={`legend-dot split-${index}`} aria-hidden="true" />
                  {name}: {count} ({Math.round((count / deviceTotal) * 100)}%)
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}
    </section>
  );
}
