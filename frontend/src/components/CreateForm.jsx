import { useState } from 'react';

const ALIAS_PATTERN = /^[a-zA-Z0-9_-]{3,30}$/;

export default function CreateForm({ onCreate }) {
  const [url, setUrl] = useState('');
  const [alias, setAlias] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    const address = url.trim();
    const customAlias = alias.trim();

    if (!/^https?:\/\//i.test(address)) {
      setError('The address must start with http:// or https://');
      return;
    }
    if (customAlias && !ALIAS_PATTERN.test(customAlias)) {
      setError('A custom name needs 3 to 30 letters, numbers, hyphens or underscores.');
      return;
    }

    setError('');
    setBusy(true);
    const created = await onCreate(address, customAlias);
    setBusy(false);
    if (created) {
      setUrl('');
      setAlias('');
    }
  }

  return (
    <form className="panel" onSubmit={submit}>
      <h2>Shorten a link</h2>
      <label className="field">
        <span>Long address</span>
        <input
          type="text"
          inputMode="url"
          autoComplete="off"
          spellCheck="false"
          value={url}
          onChange={(event) => setUrl(event.target.value)}
          placeholder="https://example.com/a/very/long/page"
        />
      </label>
      <label className="field">
        <span>
          Custom name <em>(optional)</em>
        </span>
        <input
          type="text"
          autoComplete="off"
          spellCheck="false"
          value={alias}
          onChange={(event) => setAlias(event.target.value)}
          placeholder="my-article"
        />
      </label>
      {error && <p className="field-error" role="alert">{error}</p>}
      <button type="submit" className="button button-primary" disabled={busy}>
        {busy ? 'Creating…' : 'Create short link'}
      </button>
    </form>
  );
}
