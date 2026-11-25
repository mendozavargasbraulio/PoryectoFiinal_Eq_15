import { useEffect, useRef, useState } from 'react';
import client, { extractApiError } from '../api/client.js';

const GSI_SRC = 'https://accounts.google.com/gsi/client';
export const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID || '';

/** Carga el script de Google Identity Services una sola vez. */
let gsiPromise = null;
function loadGsi() {
  if (window.google?.accounts?.id) return Promise.resolve();
  if (!gsiPromise) {
    gsiPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = GSI_SRC;
      script.async = true;
      script.defer = true;
      script.onload = resolve;
      script.onerror = () => {
        gsiPromise = null;
        reject(new Error('No se pudo cargar Google'));
      };
      document.head.appendChild(script);
    });
  }
  return gsiPromise;
}

/**
 * Botón "Continuar con Google" (registro e inicio de sesión: el backend decide).
 * Envía el ID token a POST /auth/google y devuelve el resultado al padre:
 *  - 200 -> onSession({token, user})  (mismo formato que el login tradicional)
 *  - 202 -> onPending(message)        (Google no certificó el correo: llega un enlace por correo)
 * Si no hay VITE_GOOGLE_CLIENT_ID el botón no se muestra.
 */
export default function GoogleSignInButton({ onSession, onPending, onError, text = 'continue_with' }) {
  const holderRef = useRef(null);
  const [failed, setFailed] = useState(false);
  // Los callbacks cambian en cada render del padre: se guardan en refs para no reinicializar Google.
  const handlers = useRef({});
  handlers.current = { onSession, onPending, onError };

  useEffect(() => {
    if (!GOOGLE_CLIENT_ID) return undefined;
    let cancelled = false;

    async function handleCredential(response) {
      try {
        const res = await client.post('/auth/google', { credential: response.credential });
        if (res.status === 202) handlers.current.onPending?.(res.data?.message || '');
        else handlers.current.onSession?.(res.data);
      } catch (err) {
        handlers.current.onError?.(extractApiError(err));
      }
    }

    loadGsi()
      .then(() => {
        if (cancelled || !holderRef.current) return;
        window.google.accounts.id.initialize({
          client_id: GOOGLE_CLIENT_ID,
          callback: handleCredential,
          ux_mode: 'popup',
        });
        window.google.accounts.id.renderButton(holderRef.current, {
          type: 'standard',
          theme: 'outline',
          size: 'large',
          text,
          shape: 'rectangular',
          width: Math.min(380, holderRef.current.parentElement?.clientWidth || 380),
        });
      })
      .catch(() => setFailed(true));

    return () => {
      cancelled = true;
    };
  }, [text]);

  if (!GOOGLE_CLIENT_ID) return null;
  return (
    <div style={{ marginTop: 18 }}>
      <div ref={holderRef} style={{ display: 'flex', justifyContent: 'center', minHeight: 44 }} />
      {failed ? (
        <p className="form-status" role="alert">No se pudo cargar el acceso con Google. Intenta de nuevo más tarde.</p>
      ) : null}
      <div className="divider" style={{ margin: '18px 0 4px' }} />
    </div>
  );
}
