import { useState } from 'react';

/**
 * Input con etiqueta, ayuda, error y botón de ojo automático para campos de contraseña.
 */
export default function Input({
  label,
  name,
  type = 'text',
  placeholder,
  help,
  error,
  autoComplete,
  value,
  onChange,
  onBlur,
  disabled = false,
  className = '',
  ...rest
}) {
  const [showPassword, setShowPassword] = useState(false);
  const isPassword = type === 'password';
  const effectiveType = isPassword && showPassword ? 'text' : type;
  const invalid = Boolean(error);

  const inputEl = (
    <input
      id={name}
      name={name}
      type={effectiveType}
      className={`input${invalid ? ' invalid' : ''}${isPassword ? ' has-eye' : ''}`}
      placeholder={placeholder}
      autoComplete={autoComplete}
      value={value}
      onChange={onChange}
      onBlur={onBlur}
      disabled={disabled}
      aria-invalid={invalid}
      aria-describedby={invalid ? `${name}-error` : undefined}
      {...rest}
    />
  );

  return (
    <div className={className}>
      <label className="form-label" htmlFor={name}>
        {label}
      </label>
      {isPassword ? (
        <div className="input-wrap">
          {inputEl}
          <button
            type="button"
            className="input-eye"
            disabled={disabled}
            onClick={() => setShowPassword((prev) => !prev)}
            aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
            title={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
          >
            {showPassword ? <EyeOffIcon /> : <EyeIcon />}
          </button>
        </div>
      ) : (
        inputEl
      )}
      {error ? (
        <p className="error" id={`${name}-error`} role="alert">
          {error}
        </p>
      ) : help ? (
        <p className="help">{help}</p>
      ) : null}
    </div>
  );
}

function EyeIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

function EyeOffIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M17.94 17.94A10.94 10.94 0 0 1 12 19c-6.5 0-10-7-10-7a21.8 21.8 0 0 1 5.06-5.94" />
      <path d="M9.9 4.24A10.9 10.9 0 0 1 12 4c6.5 0 10 7 10 7a21.8 21.8 0 0 1-2.16 3.19" />
      <path d="M14.12 14.12a3 3 0 1 1-4.24-4.24" />
      <line x1="2" y1="2" x2="22" y2="22" />
    </svg>
  );
}
