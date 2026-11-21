/**
 * Input con etiqueta, ayuda y error debajo del propio campo, en vivo y en blur.
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
}) {
  const invalid = Boolean(error);
  return (
    <div className={className}>
      <label className="form-label" htmlFor={name}>
        {label}
      </label>
      <input
        id={name}
        name={name}
        type={type}
        className={`input${invalid ? ' invalid' : ''}`}
        placeholder={placeholder}
        autoComplete={autoComplete}
        value={value}
        onChange={onChange}
        onBlur={onBlur}
        disabled={disabled}
        aria-invalid={invalid}
        aria-describedby={invalid ? `${name}-error` : undefined}
      />
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
