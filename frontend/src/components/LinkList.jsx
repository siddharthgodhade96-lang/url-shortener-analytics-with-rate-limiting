import { useState } from 'react';
import { fullShortUrl } from '../api.js';

function formatDate(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

export default function LinkList({ links, loading, selectedId, onSelect, onRefresh }) {
  const [copiedId, setCopiedId] = useState(null);

  async function copy(link) {
    try {
      await navigator.clipboard.writeText(fullShortUrl(link));
      setCopiedId(link.id);
      setTimeout(() => setCopiedId((id) => (id === link.id ? null : id)), 1500);
    } catch {
      window.prompt('Copy this link:', fullShortUrl(link));
    }
  }

  return (
    <section className="panel">
      <div className="panel-head">
        <h2>Your links</h2>
        <button type="button" className="button button-quiet" onClick={onRefresh} disabled={loading}>
          {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>

      {links.length === 0 ? (
        <p className="empty">
          {loading ? 'Loading your links…' : 'No links yet. Paste a long address on the left to make your first one.'}
        </p>
      ) : (
        <ul className="links">
          {links.map((link) => (
            <li key={link.id} className={link.id === selectedId ? 'link is-selected' : 'link'}>
              <div className="link-main">
                <a
                  className="short"
                  href={fullShortUrl(link)}
                  target="_blank"
                  rel="noopener"
                  title="Opens the link and counts one click"
                >
                  {fullShortUrl(link).replace(/^https?:\/\//, '')}
                </a>
                {!link.active && <span className="badge">Deactivated</span>}
                <span className="original" title={link.originalUrl}>
                  {link.originalUrl}
                </span>
                <span className="when">Created {formatDate(link.createdAt)}</span>
              </div>
              <div className="link-actions">
                <button type="button" className="button button-small" onClick={() => copy(link)}>
                  {copiedId === link.id ? 'Copied' : 'Copy'}
                </button>
                <button
                  type="button"
                  className="button button-small"
                  aria-pressed={link.id === selectedId}
                  onClick={() => onSelect(link.id)}
                >
                  Stats
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
