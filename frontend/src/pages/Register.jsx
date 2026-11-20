import { useState } from 'react';
import { Link } from 'react-router-dom';
import AuthLayout from '../layouts/AuthLayout.jsx';
import Input from '../components/Input.jsx';
import {
  validarCorreo,
  validarContrasenaRegistro,
  validarConfirmacion
} from '../lib/validaciones.js';

export default function Register() {
  const [datos, setDatos] = useState({
    name: '',
    email: '',
    password: '',
    confirmPassword: ''
  });

  const [errores, setErrores] = useState({});
  const [mensaje, setMensaje] = useState('');

  function actualizar(evento) {
    const { name, value } = evento.target;

    setDatos(actual => ({ ...actual, [name]: value }));
    setErrores(actual => ({ ...actual, [name]: '' }));
    setMensaje('');
  }

  function enviar(evento) {
    evento.preventDefault();

    const nuevosErrores = {
      name: datos.name.trim() ? '' : 'El nombre es obligatorio.',
      email: validarCorreo(datos.email),
      password: validarContrasenaRegistro(datos.password),
      confirmPassword: validarConfirmacion(
        datos.password,
        datos.confirmPassword
      )
    };

    setErrores(nuevosErrores);

    if (Object.values(nuevosErrores).some(Boolean)) {
      return;
    }

    setMensaje(
      'Los datos son válidos. El registro estará disponible al conectar el backend.'
    );
  }

  return (
    <AuthLayout>
      <h1>Crea tu cuenta.</h1>
      <p className="muted">
        Comienza tu aprendizaje con English Lab.
      </p>

      <form onSubmit={enviar} noValidate>
        <Input
          label="Nombre completo"
          name="name"
          placeholder="Tu nombre"
          value={datos.name}
          onChange={actualizar}
          error={errores.name}
        />

        <Input
          label="Correo electrónico"
          name="email"
          type="email"
          placeholder="nombre@correo.com"
          value={datos.email}
          onChange={actualizar}
          error={errores.email}
        />

        <Input
          label="Contraseña"
          name="password"
          type="password"
          placeholder="Crea una contraseña"
          value={datos.password}
          onChange={actualizar}
          error={errores.password}
        />

        <Input
          label="Confirmar contraseña"
          name="confirmPassword"
          type="password"
          placeholder="Repite tu contraseña"
          value={datos.confirmPassword}
          onChange={actualizar}
          error={errores.confirmPassword}
        />

        {mensaje && <p className="form-status" role="status">{mensaje}</p>}

        <button className="btn wide" type="submit">
          Validar formulario
        </button>
      </form>

      <div className="auth-links">
        ¿Ya tienes cuenta? <Link to="/login">Inicia sesión</Link>
      </div>
    </AuthLayout>
  );
}