import { useEffect, useState } from 'react';
import { api } from '../api.js';

const LABELS = {
  up: 'Backend is ready',
  partial: 'Backend is running, but a service is down',
  down: 'Backend is not reachable',
  checking: 'Checking backend',
};

export default function Health() {
  const [state, setState] = useState({ kind: 'checking', detail: '' });

  useEffect(() => {
    let active = true;

    async function check() {
      try {
        const health = await api.health();
        if (!active) return;
        if (health && health.status === 'UP') {
          setState({ kind: 'up', detail: '' });
        } else {
          const checks = (health && health.checks) || {};
          const broken = Object.entries(checks)
            .filter(([, value]) => value !== 'UP')
            .map(([name]) => (name === 'database' ? 'MySQL' : 'Redis'));
          setState({ kind: 'partial', detail: broken.join(' and ') + ' not responding' });
        }
      } catch {
        if (active) setState({ kind: 'down', detail: 'Start the backend on port 8080' });
      }
    }

    check();
    const timer = setInterval(check, 15000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, []);

  return (
    <span className={`health health-${state.kind}`} role="status" title={state.detail}>
      <span className="health-dot" aria-hidden="true" />
      {LABELS[state.kind]}
      {state.detail && <span className="health-detail">: {state.detail}</span>}
    </span>
  );
}
