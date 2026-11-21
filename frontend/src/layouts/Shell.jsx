import { useEffect } from 'react';
import { NavLink as RouterNavLink } from 'react-router-dom';
import { initials } from '../lib/format.js';

/**
 * Menú por rol. Estudiante: inicio/tareas/progreso (el reproductor y el
 * resultado no están en el menú, se llega desde tareas y sesiones). Profesor y
 * admin: banco/editor/asignar/resultados (+ usuarios/categorías en admin). El
 * menú lateral va sin numeración; el breadcrumb del topbar conserva la suya.
 */
export const NAV_MENUS = {
  STUDENT: [
    { to: '/estudiante/inicio', label: 'Inicio' },
    { to: '/estudiante/tareas', label: 'Tareas y práctica' },
    { to: '/estudiante/progreso', label: 'Mi progreso' },
  ],
  TEACHER: [
    { to: '/profesor/inicio', label: 'Inicio' },
    { to: '/actividades', label: 'Banco de actividades', end: true },
    { to: '/actividades/nueva', label: 'Crear actividad', match: '/actividades/nueva' },
    { to: '/asignaciones/nueva', label: 'Asignar tarea' },
    { to: '/resultados', label: 'Resultados' },
  ],
  ADMIN: [
    { to: '/actividades', label: 'Banco de actividades', end: true },
    { to: '/actividades/nueva', label: 'Crear actividad', match: '/actividades/nueva' },
    { to: '/asignaciones/nueva', label: 'Asignar tarea' },
    { to: '/resultados', label: 'Resultados' },
    { to: '/admin/usuarios', label: 'Administrar usuarios' },
    { to: '/admin/categorias', label: 'Categorías' },
  ],
};

/** Shell interno: sidebar con brand + nav por rol y topbar con crumb y acciones
 *  Perfil/Salir. En móvil (<=700px) la sidebar colapsa a nav horizontal. */
export default function Shell({ role, crumb, user, onProfile, onLogout, children }) {
  const items = NAV_MENUS[role] || [];

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [crumb]);

  return (
    <div className="shell">
      <aside className="side">
        <div className="brand">
          <span className="brand-mark">EL</span>
          <span>
            ENGLISH LAB
            <small>LEARNING SYSTEM</small>
          </span>
        </div>
        <div className="heading">Menú</div>
        <nav className="nav" aria-label="Navegación principal">
          {items.map((item) => (
            <NavLink key={item.to} {...item} />
          ))}
        </nav>
      </aside>
      <section className="workspace">
        <header className="topbar">
          <span className="crumb">ENGLISH LAB / {crumb}</span>
          <div className="top-actions">
            <span className="user-pill">
              <span className="who">
                <b>{user?.name || 'Usuario'}</b>
                <span>{user?.email || ''}</span>
              </span>
              <span className="avatar" aria-hidden="true">
                {avatarInitials(user?.name)}
              </span>
            </span>
            <button className="btn alt small" type="button" onClick={onProfile}>
              Perfil
            </button>
            <button className="btn alt small" type="button" onClick={onLogout}>
              Salir
            </button>
          </div>
        </header>
        <main className="canvas">
          <div className="screen">{children}</div>
        </main>
      </section>
    </div>
  );
}

function NavLink({ to, label, match, end = false }) {
  const pattern = match || to;
  return (
    <RouterNavLink to={to} end={end} className={({ isActive }) => (isActive && isActiveFor(pattern, to) ? 'active' : '')}>
      {label}
    </RouterNavLink>
  );
}

/* Marca activo si la ruta actual empieza por el patrón (para rutas con :id). */
function isActiveFor(pattern, to) {
  if (pattern === to) return true;
  return window.location.pathname.startsWith(pattern);
}

function avatarInitials(name) {
  return initials(name || '');
}
