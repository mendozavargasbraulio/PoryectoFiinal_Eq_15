import { useEffect, useState } from 'react';
import Input from './Input.jsx';
import client, { extractApiError } from '../api/client.js';
import { email as validateEmail, required } from '../lib/validators.js';

const COOLDOWN_SECONDS = 60;

/**
 * Reenvío del enlace de verificación. La respuesta del backend es la misma exista o no la
 * cuenta (no revela nada); además aplica cooldown y tope por hora. Aquí solo se añade una
 * cuenta atrás para no invitar a pulsar repetidamente.
 *
 * - `email`: si viene informado se usa tal cual; si no, se pide en un campo.
 * - `startCooldown`: arranca la cuenta atrás (p. ej. justo después de registrarse).
 */
export default function ResendVerification({ email = '', startCooldown = false }) {
  const [typedEmail, setTypedEmail] = useState('');
  const [seconds, setSeconds] = useState(startCooldown ? COOLDOWN_SECONDS : 0);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  const target = (email || typedEmail).trim();

  useEffect(() => {
    if (seconds <= 0) return undefined;
    const id = setTimeout(() => setSeconds((s) => s - 1), 1000);
    return () => clearTimeout(id);
  }, [seconds]);

  async function handleResend() {
    const invalid = required(target, 'El correo') || validateEmail(target);
    if (invalid) {
      setError(invalid);
      return;
    }
    setBusy(true);
    setError('');
    setMessage('');
    try {
      const { data } = await client.post('/auth/resend-verification', { email: target });
      setMessage(data?.message || 'Si la cuenta está pendiente de verificar, te enviaremos un nuevo enlace.');
      setSeconds(COOLDOWN_SECONDS);
    } catch (err) {
      setError(extractApiError(err).detail);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ marginTop: 14 }}>
      {!email ? (
        <Input
          label="Correo electrónico"
          name="resendEmail"
          type="email"
          placeholder="nombre@correo.com"
          autoComplete="email"
          value={typedEmail}
          onChange={(e) => setTypedEmail(e.target.value)}
          error={error}
        />
      ) : error ? (
        <p className="form-status" role="alert">{error}</p>
      ) : null}
      {message ? <p className="help" role="status">{message}</p> : null}
      <button
        type="button"
        className="btn alt wide"
        style={{ marginTop: 10 }}
        onClick={handleResend}
        disabled={busy || seconds > 0}
      >
        {busy ? 'Enviando…' : seconds > 0 ? `Reenviar correo (${seconds} s)` : 'Reenviar correo de verificación'}
      </button>
    </div>
  );
}
