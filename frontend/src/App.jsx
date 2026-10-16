import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from './api.js';
import Health from './components/Health.jsx';
import KeyForm from './components/KeyForm.jsx';
import CreateForm from './components/CreateForm.jsx';
import TokenBucket from './components/TokenBucket.jsx';
import LinkList from './components/LinkList.jsx';
import LinkDetail from './components/LinkDetail.jsx';
import Summary from './components/Summary.jsx';

const KEY_STORAGE = 'urlShortener.apiKey';

function loadSavedKey() {
  try {
    return localStorage.getItem(KEY_STORAGE) || '';
  } catch {
    return '';
  }
}

export default function App() {
  const [apiKey, setApiKey] = useState(loadSavedKey);
  const [links, setLinks] = useState([]);
  const [linksLoading, setLinksLoading] = useState(false);
  const [selectedId, setSelectedId] = useState(null);
  const [usage, setUsage] = useState(null);
  const [notice, setNotice] = useState(null);
  const [analyticsVersion, setAnalyticsVersion] = useState(0);

  const signOut = useCallback((message) => {
    try {
      localStorage.removeItem(KEY_STORAGE);
    } catch {
      /* ignore */
    }
    setApiKey('');
    setLinks([]);
    setSelectedId(null);
    setUsage(null);
    setNotice(message ? { type: 'error', text: message } : null);
  }, []);

  const handleError = useCallback(
    (error) => {
      if (error instanceof ApiError && error.status === 401) {
        signOut(error.message);
      } else {
        setNotice({ type: 'error', text: error.message || 'Something went wrong.' });
      }
    },
    [signOut]
  );

  const refreshUsage = useCallback(async () => {
    if (!apiKey) return;
    try {
      setUsage({ ...(await api.usage(apiKey)), fetchedAt: Date.now() });
    } catch (error) {
      handleError(error);
    }
  }, [apiKey, handleError]);

  const refreshLinks = useCallback(async () => {
    if (!apiKey) return;
    setLinksLoading(true);
    try {
      setLinks(await api.listUrls(apiKey));
    } catch (error) {
      handleError(error);
    } finally {
      setLinksLoading(false);
    }
  }, [apiKey, handleError]);

  useEffect(() => {
    if (apiKey) {
      refreshLinks();
      refreshUsage();
    }
  }, [apiKey, refreshLinks, refreshUsage]);

  function saveKey(key, firstUsage) {
    try {
      localStorage.setItem(KEY_STORAGE, key);
    } catch {
      /* the key just will not be remembered */
    }
    setNotice(null);
    setUsage({ ...firstUsage, fetchedAt: Date.now() });
    setApiKey(key);
  }

  async function handleCreate(originalUrl, customAlias) {
    try {
      const created = await api.createUrl(apiKey, originalUrl, customAlias);
      setLinks((current) => [created, ...current]);
      setSelectedId(created.id);
      setNotice({ type: 'ok', text: `Short link created: ${created.shortUrl}` });
      refreshUsage();
      return true;
    } catch (error) {
      if (error instanceof ApiError && error.status === 429) refreshUsage();
      handleError(error);
      return false;
    }
  }

  async function handleDeactivate(link) {
    if (!window.confirm(`Deactivate ${link.shortUrl}? People who open it will see "not found".`)) {
      return;
    }
    try {
      await api.deactivateUrl(apiKey, link.id);
      setLinks((current) => current.map((item) => (item.id === link.id ? { ...item, active: false } : item)));
      setNotice({ type: 'ok', text: `${link.shortUrl} is now deactivated.` });
    } catch (error) {
      handleError(error);
    }
  }

  const selected = links.find((link) => link.id === selectedId) || null;

  return (
    <div className="page">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true" />
          <h1>Short links</h1>
        </div>
        <div className="topbar-right">
          <Health />
          {apiKey && (
            <button type="button" className="button button-quiet" onClick={() => signOut()}>
              Use a different key
            </button>
          )}
        </div>
      </header>

      {notice && (
        <div className={`notice notice-${notice.type}`} role={notice.type === 'error' ? 'alert' : 'status'}>
          <span>{notice.text}</span>
          <button type="button" className="notice-close" aria-label="Dismiss message" onClick={() => setNotice(null)}>
            ×
          </button>
        </div>
      )}

      {!apiKey ? (
        <KeyForm onSave={saveKey} />
      ) : (
        <main className="layout">
          <aside className="side">
            <CreateForm onCreate={handleCreate} />
            <TokenBucket usage={usage} />
          </aside>

          <div className="main">
            <LinkList
              links={links}
              loading={linksLoading}
              selectedId={selectedId}
              onSelect={setSelectedId}
              onRefresh={refreshLinks}
            />
            {selected && (
              <LinkDetail
                key={selected.id}
                apiKey={apiKey}
                link={selected}
                onDeactivate={handleDeactivate}
                onError={handleError}
                onViewed={() => setAnalyticsVersion((n) => n + 1)}
              />
            )}
            <Summary
              apiKey={apiKey}
              version={analyticsVersion}
              onError={handleError}
            />
          </div>
        </main>
      )}
    </div>
  );
}
