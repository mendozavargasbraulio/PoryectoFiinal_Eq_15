/** Validadores compartidos (los mismos criterios que aplica el backend). */

export const PASSWORD_RULE =
  'Mínimo 8 caracteres, una mayúscula, un número y un carácter especial.';

export const PASSWORD_RE = /^(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/;

export function required(value, label = 'Este campo') {
  return String(value || '').trim() ? '' : `${label} es obligatorio.`;
}

export function email(value) {
  const v = String(value || '').trim();
  if (!v) return 'El correo es obligatorio.';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v)) return 'Escribe un correo válido (ej. nombre@correo.com).';
  return '';
}

export function password(value) {
  const v = String(value || '');
  if (!v) return 'La contraseña es obligatoria.';
  if (!PASSWORD_RE.test(v)) return `Revisa los requisitos: ${PASSWORD_RULE}`;
  return '';
}

export function matchesPassword(value, other) {
  return value === other ? '' : 'Las contraseñas no coinciden.';
}
