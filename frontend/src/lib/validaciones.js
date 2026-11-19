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
