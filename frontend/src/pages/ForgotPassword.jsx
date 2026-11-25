import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AuthLayout from '../layouts/AuthLayout.jsx';
import Input from '../components/Input.jsx';
import client, { extractApiError } from '../api/client.js';
import { matchesPassword, password as validatePassword, required } from '../lib/validators.js';
import { useToast } from '../components/Toast.jsx';

const E164_REGEX = /^\+[1-9]\d{7,14}$/;

function validatePhone(value) {
  const err = required(value, 'El teléfono');
  if (err) return err;
  const cleaned = String(value).trim().replace(/[\s\-().]+/g, '');
  if (cleaned.length > 20 || !E164_REGEX.test(cleaned)) {
    return 'Ingresa un número con formato internacional (ej. +529511234567).';
  }
  return '';
}

function validateCode(value) {
  const err = required(value, 'El código SMS');
  if (err) return err;
  if (!/^\d{6}$/.test(String(value).trim())) {
    return 'Ingresa el código de 6 dígitos que recibiste por SMS.';
  }
  return '';
}

/** Recuperación de contraseña directa por SMS en 2 pasos (Twilio Verify). */
export default function ForgotPassword() {
  const navigate = useNavigate();
  const { showToast } = useToast();

  const [step, setStep] = useState(1);
  const [phone, setPhone] = useState('');
  const [phoneError, setPhoneError] = useState('');
  const [phoneTouched, setPhoneTouched] = useState(false);

  const [resetValues, setResetValues] = useState({ code: '', newPassword: '', confirmPassword: '' });
  const [resetErrors, setResetErrors] = useState({ code: '', newPassword: '', confirmPassword: '' });
  const [resetTouched, setResetTouched] = useState({});

  const [notice, setNotice] = useState('');
  const [submitError, setSubmitError] = useState('');
  const [busy, setBusy] = useState(false);

  const resetRules = {
    code: (v) => validateCode(v),
    newPassword: (v) => validatePassword(v),
    confirmPassword: (v) => required(v, 'Confirmar contraseña') || matchesPassword(v, resetValues.newPassword),
  };

  function handlePhoneChange(e) {
    const val = e.target.value;
    setPhone(val);
    setSubmitError('');
    if (phoneTouched) setPhoneError(validatePhone(val));
  }

  function handlePhoneBlur(e) {
    setPhoneTouched(true);
    setPhoneError(validatePhone(e.target.value));
  }

  async function handleSendCode(e) {
    e.preventDefault();
    setPhoneTouched(true);
    const err = validatePhone(phone);
    setPhoneError(err);
    if (err) return;

    setBusy(true);
    setSubmitError('');
    try {
      const cleanedPhone = phone.trim().replace(/[\s\-().]+/g, '');
      const { data } = await client.post('/auth/forgot-password/sms', { phone: cleanedPhone });
      const msg = data?.message || 'Si el número está registrado, recibirás un código por SMS.';
      setPhone(cleanedPhone);
      setNotice(msg);
      setStep(2);
      showToast(msg);
    } catch (err2) {
      const apiError = extractApiError(err2);
      setSubmitError(apiError.detail);
    } finally {
      setBusy(false);
    }
  }

  function handleResetChange(e) {
    const { name, value } = e.target;
    const next = { ...resetValues, [name]: value };
    setResetValues(next);
    setSubmitError('');
    setResetErrors((errs) => ({
      ...errs,
      ...(resetTouched[name] ? { [name]: resetRules[name](value) } : {}),
      ...(resetTouched.confirmPassword && name === 'newPassword'
        ? { confirmPassword: required(next.confirmPassword, 'Confirmar contraseña') || matchesPassword(next.confirmPassword, value) }
        : {}),
    }));
  }

  function handleResetBlur(e) {
    const { name, value } = e.target;
    setResetTouched((t) => ({ ...t, [name]: true }));
    setResetErrors((errs) => ({ ...errs, [name]: resetRules[name](value) }));
  }

  async function handleResetPassword(e) {
    e.preventDefault();
    const nextErrors = {};
    for (const key of Object.keys(resetRules)) nextErrors[key] = resetRules[key](resetValues[key]);
    setResetErrors(nextErrors);
    setResetTouched({ code: true, newPassword: true, confirmPassword: true });
    if (Object.values(nextErrors).some(Boolean)) return;

    setBusy(true);
    setSubmitError('');
    try {
      await client.post('/auth/reset-password/sms', {
        phone,
        code: resetValues.code.trim(),
        newPassword: resetValues.newPassword,
      });
      showToast('Contraseña actualizada. Ya puedes iniciar sesión.');
      navigate('/login', { replace: true });
    } catch (err2) {
      const apiError = extractApiError(err2);
      const fieldErrors = {};
      apiError.fieldErrors.forEach((f) => {
        if (f.field in resetValues) fieldErrors[f.field] = f.message;
      });
      setResetErrors((prev) => ({ ...prev, ...fieldErrors }));
      if (!Object.keys(fieldErrors).length) {
        setSubmitError(apiError.detail);
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout>
      <h1>Recupera tu acceso.</h1>
      {step === 1 ? (
        <>
          <p className="muted">
            Ingresa el teléfono registrado en tu cuenta. Si no tienes un número asociado, contacta al
            administrador al{' '}
            <a href="https://wa.me/529514010900" target="_blank" rel="noopener noreferrer">
              951 401 0900
            </a>{' '}
            (
            <a href="https://wa.me/529514010900" target="_blank" rel="noopener noreferrer">
              WhatsApp
            </a>
            ).
          </p>
          <form onSubmit={handleSendCode} noValidate>
            <Input
              label="Teléfono"
              name="phone"
              type="tel"
              placeholder="+529511234567"
              autoComplete="tel"
              value={phone}
              onChange={handlePhoneChange}
              onBlur={handlePhoneBlur}
              error={phoneTouched ? phoneError : ''}
            />
            {submitError ? <p className="form-status" role="alert">{submitError}</p> : null}
            <button className="btn wide" type="submit" disabled={busy} style={{ marginTop: 14 }}>
              {busy ? 'Enviando…' : 'Enviar código SMS'}
            </button>
            <div className="auth-links">
              <Link to="/login">Volver al inicio</Link>
            </div>
          </form>
        </>
      ) : (
        <>
          <div className="notice" role="status" style={{ margin: '8px 0 16px' }}>
            <span>{notice}</span>
          </div>
          <form onSubmit={handleResetPassword} noValidate>
            <Input
              label="Código SMS (6 dígitos)"
              name="code"
              type="text"
              inputMode="numeric"
              maxLength={6}
              placeholder="123456"
              autoComplete="one-time-code"
              value={resetValues.code}
              onChange={handleResetChange}
              onBlur={handleResetBlur}
              error={resetTouched.code ? resetErrors.code : ''}
            />
            <Input
              label="Nueva contraseña"
              name="newPassword"
              type="password"
              placeholder="Mín. 8 caracteres"
              autoComplete="new-password"
              help="Debe incluir mayúscula, número y carácter especial."
              value={resetValues.newPassword}
              onChange={handleResetChange}
              onBlur={handleResetBlur}
              error={resetTouched.newPassword ? resetErrors.newPassword : ''}
            />
            <Input
              label="Confirmar contraseña"
              name="confirmPassword"
              type="password"
              placeholder="Repite tu contraseña"
              autoComplete="new-password"
              value={resetValues.confirmPassword}
              onChange={handleResetChange}
              onBlur={handleResetBlur}
              error={resetTouched.confirmPassword ? resetErrors.confirmPassword : ''}
            />
            {submitError ? <p className="form-status" role="alert">{submitError}</p> : null}
            <button className="btn wide" type="submit" disabled={busy} style={{ marginTop: 14 }}>
              {busy ? 'Cambiando…' : 'Cambiar contraseña'}
            </button>
            <div className="auth-links">
              <button
                type="button"
                className="link"
                style={{ background: 'none', border: 0, padding: 0, cursor: 'pointer', color: 'inherit', textDecoration: 'underline' }}
                onClick={() => {
                  setStep(1);
                  setSubmitError('');
                  setResetValues({ code: '', newPassword: '', confirmPassword: '' });
                  setResetErrors({ code: '', newPassword: '', confirmPassword: '' });
                  setResetTouched({});
                }}
              >
                Cambiar teléfono o reenviar código
              </button>
              <Link to="/login">Volver al inicio</Link>
            </div>
          </form>
        </>
      )}
    </AuthLayout>
  );
}
