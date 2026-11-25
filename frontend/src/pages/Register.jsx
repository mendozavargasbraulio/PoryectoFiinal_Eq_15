import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AuthLayout from '../layouts/AuthLayout.jsx';
import Input from '../components/Input.jsx';
import client, { extractApiError } from '../api/client.js';
import ResendVerification from '../components/ResendVerification.jsx';
import GoogleSignInButton from '../components/GoogleSignInButton.jsx';
import { useAuth, homePathForRole } from '../auth/AuthContext.jsx';
import {
  email as validateEmail,
  matchesPassword,
  password as validatePassword,
  required,
} from '../lib/validators.js';

/**
 * El registro público SIEMPRE crea STUDENT. No inicia sesión: el backend deja la cuenta
 * pendiente y envía un enlace de verificación al correo.
 */
export default function Register() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [googlePending, setGooglePending] = useState('');
  const [values, setValues] = useState({ name: '', email: '', password: '', confirmPassword: '' });
  const [errors, setErrors] = useState({ name: '', email: '', password: '', confirmPassword: '' });
  const [touched, setTouched] = useState({});
  const [submitError, setSubmitError] = useState('');
  const [busy, setBusy] = useState(false);
  const [sentTo, setSentTo] = useState('');

  const rules = {
    name: (v) => required(v, 'El nombre') || (String(v || '').trim().length > 120 ? 'Máximo 120 caracteres.' : ''),
    email: (v) =>
      required(v, 'El correo')
      || validateEmail(v)
      || (String(v || '').trim().length > 255 ? 'Máximo 255 caracteres.' : ''),
    password: (v) => validatePassword(v),
    confirmPassword: (v) => required(v, 'Confirmar contraseña') || matchesPassword(v, values.password),
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    const next = { ...values, [name]: value };
    setValues(next);
    setSubmitError('');
    // Revalidación en vivo: si ya tocó confirmar y cambia la clave, revisa coincidencia.
    setErrors((errs) => ({
      ...errs,
      ...(touched[name] ? { [name]: rules[name](value) } : {}),
      ...(touched.confirmPassword && name === 'password'
        ? { confirmPassword: required(next.confirmPassword, 'Confirmar contraseña') || matchesPassword(next.confirmPassword, value) }
        : {}),
    }));
  };

  const handleBlur = (e) => {
    const { name, value } = e.target;
    setTouched((t) => ({ ...t, [name]: true }));
    setErrors((errs) => ({ ...errs, [name]: rules[name](value) }));
  };

  async function handleSubmit(e) {
    e.preventDefault();
    const nextErrors = {};
    for (const key of Object.keys(rules)) nextErrors[key] = rules[key](values[key]);
    setErrors(nextErrors);
    setTouched(Object.keys(rules).reduce((acc, k) => ({ ...acc, [k]: true }), {}));
    if (Object.values(nextErrors).some(Boolean)) return;

    setBusy(true);
    setSubmitError('');
    try {
      await client.post('/auth/register', {
        name: values.name.trim(),
        email: values.email.trim(),
        password: values.password,
      });
      // El backend responde igual exista o no el correo (no revela cuentas).
      setSentTo(values.email.trim());
    } catch (err) {
      const apiError = extractApiError(err);
      const fieldErrors = {};
      apiError.fieldErrors.forEach((f) => {
        if (f.field in values) fieldErrors[f.field] = f.message;
      });
      setErrors((prev) => ({ ...prev, ...fieldErrors }));
      if (!Object.keys(fieldErrors).length) {
        setSubmitError(apiError.detail);
      }
    } finally {
      setBusy(false);
    }
  }

  if (googlePending) {
    return (
      <AuthLayout>
        <h1>Revisa tu correo.</h1>
        <div className="notice" role="status"><span>{googlePending}</span></div>
        <div className="auth-links">
          <Link to="/login">Ir a iniciar sesión</Link>
        </div>
      </AuthLayout>
    );
  }

  if (sentTo) {
    return (
      <AuthLayout>
        <h1>Revisa tu correo.</h1>
        <div className="notice" role="status">
          <span>
            Si <b>{sentTo}</b> puede registrarse, te enviamos un enlace para verificar tu cuenta.
            Ábrelo para activarla; después podrás iniciar sesión. Si no lo ves, revisa la carpeta de spam.
          </span>
        </div>
        <ResendVerification email={sentTo} startCooldown />
        <div className="auth-links">
          <Link to="/login">Ir a iniciar sesión</Link>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout>
      <h1>Crea tu cuenta.</h1>
      <p className="muted">Regístrate para comenzar a aprender.</p>
      {/* Registro con Google: el backend crea una cuenta STUDENT (o inicia sesión si ya existe). */}
      <GoogleSignInButton
        text="signup_with"
        onSession={(data) => {
          login(data.token, data.user);
          navigate(homePathForRole(data.user?.role), { replace: true });
        }}
        onPending={setGooglePending}
        onError={(apiError) => setSubmitError(apiError.detail)}
      />
      <form onSubmit={handleSubmit} noValidate>
        <Input
          label="Nombre completo"
          name="name"
          placeholder="Nombre y apellidos"
          autoComplete="name"
          value={values.name}
          onChange={handleChange}
          onBlur={handleBlur}
          error={touched.name ? errors.name : ''}
        />
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
          placeholder="Mín. 8 caracteres"
          autoComplete="new-password"
          help="Debe incluir mayúscula, número y carácter especial."
          value={values.password}
          onChange={handleChange}
          onBlur={handleBlur}
          error={touched.password ? errors.password : ''}
        />
        <Input
          label="Confirmar contraseña"
          name="confirmPassword"
          type="password"
          placeholder="Repite tu contraseña"
          autoComplete="new-password"
          value={values.confirmPassword}
          onChange={handleChange}
          onBlur={handleBlur}
          error={touched.confirmPassword ? errors.confirmPassword : ''}
        />
        {submitError ? <p className="form-status" role="alert">{submitError}</p> : null}
        <button className="btn wide" type="submit" disabled={busy} style={{ marginTop: 14 }}>
          {busy ? 'Creando cuenta…' : 'Crear cuenta'}
        </button>
      </form>
      <p className="smallprint">
        ¿Ya tienes cuenta? <Link to="/login">Iniciar sesión</Link>
      </p>
    </AuthLayout>
  );
}
