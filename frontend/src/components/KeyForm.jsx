import { useState } from 'react';
import { api } from '../api.js';

export default function KeyForm({ onSave }) {
  const [value, setValue] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    const key = value.trim();
    if (!key) {
      setError('Paste your API key first.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const usage = await api.usage(key);
      onSave(key, usage);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="gate">
      <form className="panel gate-panel" onSubmit={submit}>
        <h2>Enter your API key</h2>
        <p className="muted">
          Every link you create belongs to one API key. You make a key once, from the
          backend, and paste it here. The key is stored only in this browser.
        </p>
        <label className="field">
          <span>API key</span>
          <input
            type="password"
            autoComplete="off"
            spellCheck="false"
            value={value}
            onChange={(event) => setValue(event.target.value)}
            placeholder="Paste the key printed after API_KEY="
          />
        </label>
        {error && <p className="field-error" role="alert">{error}</p>}
        <button type="submit" className="button button-primary" disabled={busy}>
          {busy ? 'Checking key…' : 'Use this key'}
        </button>
        <details className="help">
          <summary>I do not have a key yet</summary>
          <p>
            Open a terminal in the <code>backend</code> folder, make sure MySQL and Redis are
            running, and run:
          </p>
          <pre>mvn spring-boot:run "-Dspring-boot.run.arguments=--create-api-key"</pre>
          <p>
            Copy the text after <code>API_KEY=</code> in the output. Keep the key private. It
            cannot be shown again.
          </p>
        </details>
      </form>
    </main>
  );
}
