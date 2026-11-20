export function validarCorreo(correo) {
  const valor = correo.trim();
  if (!valor) return 'Escribe tu correo electrónico.';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(valor)) {
    return 'Escribe un correo electrónico válido.';
  }
  return '';
}

export function validarContrasena(contrasena) {
  return contrasena ? '' : 'Escribe tu contraseña.';
}
export function validarContrasenaRegistro(contrasena) {
  if (!contrasena) {
    return 'La contraseña es obligatoria.';
  }

  if (contrasena.length > 72) {
    return 'La contraseña no puede superar los 72 caracteres.';
  }

  const patron = /^(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/;

  if (!patron.test(contrasena)) {
    return 'Usa al menos 8 caracteres, una mayúscula, un número y un carácter especial.';
  }

  return '';
}

export function validarConfirmacion(contrasena, confirmacion) {
  if (!confirmacion) {
    return 'Confirma tu contraseña.';
  }

  if (contrasena !== confirmacion) {
    return 'Las contraseñas no coinciden.';
  }

  return '';
}