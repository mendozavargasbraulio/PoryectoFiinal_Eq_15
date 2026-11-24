import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import AuthLayout from '../layouts/AuthLayout.jsx';
import Input from '../components/Input.jsx';
import { Spinner } from '../components/ui.jsx';
import ResendVerification from '../components/ResendVerification.jsx';
import GoogleSignInButton from '../components/GoogleSignInButton.jsx';
import client, { extractApiError, SESSION_EXPIRED_KEY } from '../api/client.js';
import { useAuth, homePathForRole } from '../auth/AuthContext.jsx';
import { email as validateEmail, required } from '../lib/validators.js';

function isAllowedForRole(path, role) {
  if (!path || path === '/' || path === '/login' || path === '/registro' || path === '/recuperar' || path === '/restablecer' || path === '/verificar-correo') {
    return false;
  }
  if (role === 'STUDENT') return path.startsWith('/estudiante/');
  if (role === 'TEACHER') {
    return (
      path.startsWith('/profesor/')
      || path.startsWith('/actividades')
      || path.startsWith('/asignaciones')
      || path.startsWith('/resultados')
    );
  }
  if (role === 'ADMIN') return path.startsWith('/admin/') || path.startsWith('/actividades');
  return false;
}

/** Sin selector de rol: el rol lo devuelve el backend y decide el panel al que
 *  se redirige. */
export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();

  const [values, setValues] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState({ email: '', password: '' });
  const [touched, setTouched] = useState({});
  const [submitError, setSubmitError] = useState('');
  const [busy, setBusy] = useState(false);
  const [sessionExpired, setSessionExpired] = useState(false);
  const [needsVerification, setNeedsVerification] = useState(false);
  const [googleNotice, setGoogleNotice] = useState('');
  const justVerified = Boolean(location.state?.verified);

  // El interceptor 401 deja una marca antes de redirigir; se consume una sola vez.
  useEffect(() => {
    try {
      if (window.sessionStorage.getItem(SESSION_EXPIRED_KEY) === '1') {
        window.sessionStorage.removeItem(SESSION_EXPIRED_KEY);
        setSessionExpired(true);
      }
    } catch {
      /* sessionStorage no disponible: sin aviso */
    }
  }, []);

  const rules = {
    email: (v) => required(v, 'El correo') || validateEmail(v),
    password: (v) => required(v, 'La contraseña'),
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setValues((v) => ({ ...v, [name]: value }));
    setSubmitError('');
    if (touched[name]) setErrors((errs) => ({ ...errs, [name]: rules[name](value) }));
  };

  const handleBlur = (e) => {
    const { name, value } = e.target;
    setTouched((t) => ({ ...t, [name]: true }));
    setErrors((errs) => ({ ...errs, [name]: rules[name](value) }));
  };

  function enterWithSession(data) {
    login(data.token, data.user);
    const from = location.state?.from;
    const role = data.user?.role;
    navigate(isAllowedForRole(from, role) ? from : homePathForRole(role), { replace: true });
  }

  async function handleSubmit(e) {
    e.preventDefault();
    const nextErrors = {};
    for (const key of Object.keys(rules)) nextErrors[key] = rules[key](values[key]);
    setErrors(nextErrors);
    setTouched({ email: true, password: true });
    if (Object.values(nextErrors).some(Boolean)) return;

    setBusy(true);
    setSubmitError('');
    setNeedsVerification(false);
    try {
      const { data } = await client.post('/auth/login', {
        email: values.email.trim(),
        password: values.password,
      });
      enterWithSession(data);
    } catch (err) {
      const apiError = extractApiError(err);
      const fieldErrors = {};
      apiError.fieldErrors.forEach((f) => {
        if (f.field in values) fieldErrors[f.field] = f.message;
      });
      setErrors((prev) => ({ ...prev, ...fieldErrors }));
      if (apiError.status === 403 && apiError.code === 'EMAIL_NOT_VERIFIED') {
        // Contraseña correcta pero cuenta sin verificar: no hay sesión; ofrece reenviar el enlace.
        setNeedsVerification(true);
        setSubmitError('Aún no has verificado tu correo. Abre el enlace que te enviamos o solicita uno nuevo.');
      } else if (!Object.keys(fieldErrors).length) {
        setSubmitError(
          apiError.status === 401
            ? 'Credenciales inválidas. Revisa tu correo y contraseña.'
            : apiError.detail
        );
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthLayout>
      <h1>Bienvenido de nuevo.</h1>
      <p className="muted">Ingresa para continuar tu aprendizaje.</p>
      {justVerified ? (
        <div className="notice" role="status" style={{ margin: '4px 0 16px' }}>
          <span><b>Correo verificado.</b> Ya puedes iniciar sesión.</span>
        </div>
      ) : null}
      {sessionExpired ? (
        <div className="notice" role="status" style={{ margin: '4px 0 16px' }}>
          <span>Tu sesión expiró. Inicia sesión de nuevo.</span>
        </div>
      ) : null}
      {googleNotice ? (
        <div className="notice" role="status" style={{ margin: '4px 0 16px' }}>
          <span>{googleNotice}</span>
        </div>
      ) : null}
      <GoogleSignInButton
        onSession={enterWithSession}
        onPending={(message) => { setSubmitError(''); setGoogleNotice(message); }}
        onError={(apiError) => { setGoogleNotice(''); setSubmitError(apiError.detail); }}
      />
      <form onSubmit={handleSubmit} noValidate>
        <Input
          label="Correo electrónico"
          name="email"
          type="email"
          placeholder="nombre@correo.com"
          autoComplete="email"
          value={values.email}
          onChange={handleChange}
          onBlur={handleBlur}
          error={touched.email ? errors.email : ''}
        />
        <Input
          label="Contraseña"
          name="password"
          type="password"
          placeholder="Tu contraseña"
          autoComplete="current-password"
          value={values.password}
          onChange={handleChange}
          onBlur={handleBlur}
          error={touched.password ? errors.password : ''}
        />
        <div className="help">
          <Link to="/recuperar">¿Olvidaste tu contraseña?</Link>
        </div>
        {submitError ? <p className="form-status" role="alert">{submitError}</p> : null}
        {needsVerification ? <ResendVerification email={values.email.trim()} /> : null}
        <button className="btn wide" type="submit" disabled={busy}>
          {busy ? 'Ingresando…' : 'Iniciar sesión'}
        </button>
      </form>
      {busy ? (
        <div className="mt" style={{ display: 'flex', justifyContent: 'center' }}>
          <Spinner small label="" />
        </div>
      ) : null}
      <div className="auth-links">
        <span>
          ¿No tienes cuenta? <Link to="/registro">Regístrate</Link>
        </span>
      </div>
    </AuthLayout>
  );
}
