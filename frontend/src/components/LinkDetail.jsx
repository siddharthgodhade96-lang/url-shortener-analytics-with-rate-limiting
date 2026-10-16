import { useCallback, useEffect, useState } from 'react';
import { api, fullShortUrl } from '../api.js';

const DAYS = 14;

// The backend counts days in UTC, so we build the same days here.
function lastDays(count) {
  const days = [];
  for (let offset = count - 1; offset >= 0; offset -= 1) {
    days.push(new Date(Date.now() - offset * 86400000).toISOString().slice(0, 10));
  }
  return days;
}

function shortDay(isoDate) {
  const [, month, day] = isoDate.split('-');
  return `${Number(day)}/${Number(month)}`;
}

export default function LinkDetail({ apiKey, link, onDeactivate, onError, onViewed }) {
  const [stats, setStats] = useState(null);
  const [daily, setDaily] = useState(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    const days = lastDays(DAYS);
    try {
      const [total, perDay] = await Promise.all([
        api.urlStats(apiKey, link.id),
        api.dailyClicks(apiKey, link.id, days[0], days[days.length - 1]),
      ]);
      const byDate = new Map(perDay.map((row) => [row.date, row.clicks]));
      setStats(total);
      setDaily(days.map((date) => ({ date, clicks: byDate.get(date) || 0 })));
      onViewed();
    } catch (error) {
      onError(error);
    } finally {
      setLoading(false);
    }
  }, [apiKey, link.id, onError, onViewed]);

  useEffect(() => {
    load();
    // Load once when a link is opened. The Refresh button loads again.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [apiKey, link.id]);

  const peak = daily ? Math.max(1, ...daily.map((day) => day.clicks)) : 1;
  const recent = daily ? daily.reduce((sum, day) => sum + day.clicks, 0) : 0;

  return (
    <section className="panel" aria-live="polite">
      <div className="panel-head">
        <h2>
          Clicks for <span className="mono">{link.shortUrl}</span>
        </h2>
        <div className="row">
          <button type="button" className="button button-quiet" onClick={load} disabled={loading}>
            {loading ? 'Loading…' : 'Refresh'}
          </button>
          {link.active && (
            <button type="button" className="button button-danger" onClick={() => onDeactivate(link)}>
              Deactivate
            </button>
          )}
        </div>
      </div>

      <p className="muted small break">
        Sends people to <a href={link.originalUrl} target="_blank" rel="noopener noreferrer">{link.originalUrl}</a>
        {' '}through <span className="mono">{fullShortUrl(link)}</span>
      </p>

      <div className="figures">
        <div>
          <strong>{stats ? stats.totalClicks : '–'}</strong>
          <span>clicks in total</span>
        </div>
        <div>
          <strong>{daily ? recent : '–'}</strong>
          <span>in the last {DAYS} days</span>
        </div>
      </div>

      {daily && (
        <>
          <div
            className="chart"
            role="img"
            aria-label={`Clicks per day for the last ${DAYS} days. ${recent} in total.`}
          >
            {daily.map((day) => (
              <div className="chart-col" key={day.date} title={`${day.date}: ${day.clicks} click${day.clicks === 1 ? '' : 's'}`}>
                <span className="chart-count">{day.clicks > 0 ? day.clicks : ''}</span>
                <span className="chart-bar" style={{ height: `${(day.clicks / peak) * 100}%` }} />
              </div>
            ))}
          </div>
          <div className="chart-axis">
            <span>{shortDay(daily[0].date)}</span>
            <span>Days are counted in UTC</span>
            <span>{shortDay(daily[daily.length - 1].date)}</span>
          </div>
        </>
      )}

      {stats && stats.totalClicks === 0 && (
        <p className="empty">
          No clicks yet. Open the short link above in a new tab, wait a second, then press Refresh.
        </p>
      )}
    </section>
  );
}
