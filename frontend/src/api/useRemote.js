import { useCallback, useEffect, useState } from 'react';
import { extractApiError } from './client.js';

/**
 * Patrón mock/real para endpoints que aún no existen en el backend.
 * 2xx → 'real'; 404 → 'fallback' (mock + <MockNotice/>); cualquier otro fallo
 * → 'error' con <ErrorState/> y reintento, sin caer nunca al mock.
 * Estados: 'loading' | 'real' | 'fallback' | 'error'.
 */
export const REMOTE_STATUS = {
  loading: 'loading',
  real: 'real',
  fallback: 'fallback',
  error: 'error',
};

export function useRemote({ fetcher, fallback, deps = [] }) {
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
        if (err?.response?.status === 404 && fallback) {
          setData(typeof fallback === 'function' ? fallback() : fallback);
          setError(null);
          setStatus(REMOTE_STATUS.fallback);
        } else {
          setError(extractApiError(err));
          setStatus(REMOTE_STATUS.error);
        }
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

/** Igual que useRemote pero para mutaciones: 404 → fallback mock; cualquier
 *  otro error se propaga como fallo real. Devuelve {mode:'real'|'fallback', data}. */
export async function mutateWithFallback(request, fallbackFn) {
  try {
    const res = await request();
    return { mode: 'real', data: res?.data ?? res };
  } catch (err) {
    if (err?.response?.status === 404) {
      return { mode: 'fallback', data: fallbackFn ? fallbackFn() : null };
    }
    throw err;
  }
}
