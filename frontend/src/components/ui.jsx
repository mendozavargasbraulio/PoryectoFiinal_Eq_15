/**
 * Piezas de interfaz pequeñas y reutilizables (antes un archivo por componente).
 * Los componentes con estado o con mucho marcado siguen en su propio archivo.
 */

/** Etiqueta de color (.badge + gray/green/rust/olive…). */
export function Badge({ color = 'gray', children }) {
  return <span className={`badge ${color}`}>{children}</span>;
}

/** Indicador de carga accesible. */
export function Spinner({ small = false, label = 'Cargando…' }) {
  return (
    <div className="spinner-wrap" role="status" aria-live="polite">
      <span className={`spinner${small ? ' small' : ''}`} aria-hidden="true" />
      {label ? <span>{label}</span> : null}
    </div>
  );
}

/** Encabezado estándar de pantalla. */
export function PageHeader({ eyebrow, title, subtitle }) {
  return (
    <>
      <p className="eyebrow">{eyebrow}</p>
      <h1 className="page-title">{title}</h1>
      {subtitle ? <p className="subtitle">{subtitle}</p> : null}
    </>
  );
}

/** Aviso de que los datos son de muestra porque el endpoint aún no existe. */
export function MockNotice({ what }) {
  return (
    <div className="mock-note">
      <span>
        Datos de muestra — {what ? <b>{what}</b> : 'esta vista'} usa contenido de demostración;
        el endpoint del backend todavía no existe.
      </span>
    </div>
  );
}

/** Error de red/servidor con botón opcional de reintento. */
export function ErrorState({ message, onRetry }) {
  return (
    <div className="network-error" role="alert">
      <div>
        <b>No se pudo cargar la información.</b>
        <div>{message || 'Ocurrió un problema de red o del servidor. Intenta de nuevo.'}</div>
      </div>
      {onRetry ? (
        <button className="btn alt small" type="button" onClick={onRetry}>
          Reintentar
        </button>
      ) : null}
    </div>
  );
}
