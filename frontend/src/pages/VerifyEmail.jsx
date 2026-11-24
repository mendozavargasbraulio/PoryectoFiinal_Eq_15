import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import AuthLayout from '../layouts/AuthLayout.jsx';
import ResendVerification from '../components/ResendVerification.jsx';
import { Spinner } from '../components/ui.jsx';
import client, { extractApiError } from '../api/client.js';

/**
 * Destino del enlace del correo (/verificar-correo?token=...). La verificación es un POST
 * (no un GET) para que los escáneres/prefetch de los clientes de correo no gasten el token.
 * El token se quita de la barra de direcciones y del historial nada más leerlo.
 */
export default function VerifyEmail() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  // Refs: sobreviven al doble montaje de StrictMode y evitan consumir el token dos veces.
  const tokenRef = useRef(params.get('token') || '');
  const startedRef = useRef(false);
  const [state, setState] = useState({ status: tokenRef.current ? 'loading' : 'error', code: 'INVALID', detail: '' });

  useEffect(() => {
    if (startedRef.current) return;
    startedRef.current = true;
    const token = tokenRef.current;
    if (!token) return;

    navigate('/verificar-correo', { replace: true });
    client
      .post('/auth/verify-email', { token })
      .then(() => setState({ status: 'ok' }))
      .catch((err) => {
        const apiError = extractApiError(err);
        setState({ status: 'error', code: apiError.code || 'UNKNOWN', detail: apiError.detail });
      });
  }, [navigate]);

  return (
    <AuthLayout>
      {state.status === 'loading' ? (
        <>
          <h1>Verificando…</h1>
          <Spinner label="Comprobando tu enlace" />
        </>
      ) : null}

      {state.status === 'ok' ? (
        <>
          <h1>¡Correo verificado!</h1>
          <div className="notice" role="status">
            <span>Tu cuenta está activa. Ya puedes iniciar sesión y empezar a practicar.</span>
          </div>
          <Link className="btn wide" to="/login" state={{ verified: true }} style={{ marginTop: 16 }}>
            Iniciar sesión
          </Link>
        </>
      ) : null}

      {state.status === 'error' ? <VerifyError code={state.code} detail={state.detail} /> : null}
    </AuthLayout>
  );
}

function VerifyError({ code, detail }) {
  if (code === 'EXPIRED') {
    return (
      <>
        <h1>El enlace ha caducado.</h1>
        <div className="notice warn" role="alert">
          <span>Por seguridad, los enlaces caducan. Escribe tu correo y te enviaremos uno nuevo.</span>
        </div>
        <ResendVerification />
        <div className="auth-links"><Link to="/login">Volver al inicio</Link></div>
      </>
    );
  }
  if (code === 'USED') {
    return (
      <>
        <h1>Este enlace ya se usó.</h1>
        <div className="notice" role="status">
          <span>Si ya verificaste tu correo, solo tienes que iniciar sesión.</span>
        </div>
        <Link className="btn wide" to="/login" style={{ marginTop: 16 }}>Iniciar sesión</Link>
      </>
    );
  }
  return (
    <>
      <h1>Enlace no válido.</h1>
      <div className="notice warn" role="alert">
        <span>{detail || 'El enlace de verificación no es válido o está incompleto.'} Puedes pedir uno nuevo.</span>
      </div>
      <ResendVerification />
      <div className="auth-links"><Link to="/login">Volver al inicio</Link></div>
    </>
  );
}
