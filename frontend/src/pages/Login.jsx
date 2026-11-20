import { useState } from 'react';
import AuthLayout from '../layouts/AuthLayout.jsx';
import Input from '../components/Input.jsx';
import { validarCorreo, validarContrasena } from '../lib/validaciones.js';

export default function Login() {
  const [form, setForm] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState({});
  const [enviando, setEnviando] = useState(false);
  const [mensaje, setMensaje] = useState('');

  function actualizar(event) {
    const { name, value } = event.target;
    setForm((actual) => ({ ...actual, [name]: value }));
    setErrors((actual) => ({ ...actual, [name]: '' }));
    setMensaje('');
  }

  function revisar(event) {
    const { name, value } = event.target;
    const error = name === 'email' ? validarCorreo(value) : validarContrasena(value);
    setErrors((actual) => ({ ...actual, [name]: error }));
  }

  async function iniciarSesion(event) {
    event.preventDefault();
    const nuevosErrores = {
      email: validarCorreo(form.email),
      password: validarContrasena(form.password),
    };
    setErrors(nuevosErrores);
    if (Object.values(nuevosErrores).some(Boolean)) return;

    setEnviando(true);
    setMensaje('');
    try {
      const respuesta = await fetch('/api/v1/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: form.email.trim(), password: form.password }),
      });
      const datos = await respuesta.json().catch(() => ({}));
      if (!respuesta.ok) {
        setMensaje(
          respuesta.status === 401
            ? 'Correo o contraseña incorrectos.'
            : datos.detail || 'No fue posible iniciar sesión. Inténtalo de nuevo.'
        );
        return;
      }
      setMensaje(datos.token ? 'Credenciales verificadas correctamente.' : 'Respuesta inesperada del servidor.');
    } catch {
      setMensaje('No fue posible conectar con el servidor. Inténtalo más tarde.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <AuthLayout>
      <h1>Bienvenido de nuevo.</h1>
      <p className="muted">Ingresa para continuar tu aprendizaje.</p>

      <form onSubmit={iniciarSesion} noValidate>
        <Input
          label="Correo electrónico"
          name="email"
          type="email"
          placeholder="nombre@correo.com"
          autoComplete="email"
          value={form.email}
          onChange={actualizar}
          onBlur={revisar}
          error={errors.email}
        />
        <Input
          label="Contraseña"
          name="password"
          type="password"
          placeholder="Tu contraseña"
          autoComplete="current-password"
          value={form.password}
          onChange={actualizar}
          onBlur={revisar}
          error={errors.password}
        />
        <p className="help">
          ¿Necesitas ayuda? <a href="/recuperar">Recuperar contraseña</a>
        </p>
        {mensaje && <p className="form-status" role="status">{mensaje}</p>}
        <button className="btn wide" type="submit" disabled={enviando}>
          {enviando ? 'Ingresando…' : 'Iniciar sesión'}
        </button>
      </form>

      <div className="auth-links">
        <span>¿No tienes cuenta? <a href="/registro">Regístrate</a></span>
      </div>
    </AuthLayout>
  );
}
