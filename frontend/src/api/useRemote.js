import { useCallback, useEffect, useState } from 'react';
import { extractApiError } from './client.js';

/**
 * Hook de lectura remota contra la API real.
 * Estados: 'loading' | 'real' | 'error'.
 */
export const REMOTE_STATUS = {
  loading: 'loading',
  real: 'real',
  error: 'error',
};

export function useRemote({ fetcher, deps = [] }) {
  const [status, setStatus] = useState(REMOTE_STATUS.loading);
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let alive = true;
    setStatus(REMOTE_STATUS.loading);
    (async () => {
      try {
        const res = await fetcher();
        if (!alive) return;
        setData(res?.data ?? res);
        setError(null);
        setStatus(REMOTE_STATUS.real);
      } catch (err) {
        if (!alive) return;
        setError(extractApiError(err));
        setStatus(REMOTE_STATUS.error);
      }
    })();
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, reloadKey]);

  const reload = useCallback(() => setReloadKey((k) => k + 1), []);

  return { status, data, error, reload };
}
