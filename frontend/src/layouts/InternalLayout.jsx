import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Shell from './Shell.jsx';
import ConfirmModal from '../components/ConfirmModal.jsx';
import { Spinner } from '../components/ui.jsx';
import { useAuth } from '../auth/AuthContext.jsx';
import { initials } from '../lib/format.js';
import client from '../api/client.js';

function formatMemberDate(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return new Intl.DateTimeFormat('es-MX', { day: 'numeric', month: 'long', year: 'numeric' }).format(d);
}

/** Página base de las vistas internas: envuelve el contenido en <Shell/> y
 *  provee las acciones de topbar: Perfil (datos completos del usuario) y Salir. */
export default function InternalLayout({ crumb, children }) {
  const { user, role, logout } = useAuth();
  const navigate = useNavigate();
  const [showLogout, setShowLogout] = useState(false);
  const [showProfile, setShowProfile] = useState(false);
  const [profile, setProfile] = useState(null);
  const [profileBusy, setProfileBusy] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  useEffect(() => {
    if (!showProfile) return;
    let active = true;
    setProfileBusy(true);
    client
      .get('/auth/me')
      .then(({ data }) => {
        if (active) setProfile(data);
      })
      .catch(() => {
        if (active) setProfile(null);
      })
      .finally(() => {
        if (active) setProfileBusy(false);
      });
    return () => {
      active = false;
    };
  }, [showProfile]);

  async function handleLogout() {
    setLoggingOut(true);
    try {
      await client.post('/auth/logout');
    } catch {
      // aunque el logout de servidor falle, la sesión local se cierra
    } finally {
      setLoggingOut(false);
      logout();
      navigate('/login', { replace: true });
    }
  }

  const displayName = profile?.name || user?.name || 'Usuario';
  const displayEmail = profile?.email || user?.email || '—';

  return (
    <>
      <Shell
        role={role}
        crumb={crumb}
        user={user}
        onProfile={() => setShowProfile(true)}
        onLogout={() => setShowLogout(true)}
      >
        {children}
      </Shell>

      <ConfirmModal
        open={showLogout}
        title="Cerrar sesión"
        description="¿Quieres salir de English Lab? Se cerrará tu sesión en este dispositivo."
        confirmLabel="Salir"
        busy={loggingOut}
        onConfirm={handleLogout}
        onCancel={() => setShowLogout(false)}
      />

      {showProfile ? (
        <div
          className="overlay"
          onClick={(e) => {
            if (e.target === e.currentTarget) setShowProfile(false);
          }}
        >
          <section className="modal" role="dialog" aria-modal="true" aria-labelledby="profile-title">
            <p className="eyebrow">ENGLISH LAB / PERFIL</p>
            <h2 id="profile-title">Mi cuenta</h2>

            {profileBusy && !profile ? (
              <Spinner small />
            ) : (
              <>
                <div className="profile-head">
                  <span className="avatar profile-avatar" aria-hidden="true">
                    {initials(displayName)}
                  </span>
                  <div className="profile-head-text">
                    <b>{displayName}</b>
                    <span>{displayEmail}</span>
                  </div>
                </div>

                <dl className="profile-list">
                  <div className="profile-row">
                    <dt>Nombre completo</dt>
                    <dd>{displayName}</dd>
                  </div>
                  <div className="profile-row">
                    <dt>Correo electrónico</dt>
                    <dd>{displayEmail}</dd>
                  </div>
                  <div className="profile-row">
                    <dt>Teléfono</dt>
                    <dd>{profile?.phone || 'No registrado'}</dd>
                  </div>
                  <div className="profile-row">
                    <dt>Estado del correo</dt>
                    <dd>{profile?.emailVerified === false ? 'Pendiente de verificación' : 'Verificado'}</dd>
                  </div>
                  <div className="profile-row">
                    <dt>Miembro desde</dt>
                    <dd>{formatMemberDate(profile?.createdAt)}</dd>
                  </div>
                  {profile?.teacherName ? (
                    <div className="profile-row">
                      <dt>Profesor asignado</dt>
                      <dd>
                        {profile.teacherName}
                        {profile.teacherEmail ? ` (${profile.teacherEmail})` : ''}
                      </dd>
                    </div>
                  ) : null}
                </dl>
              </>
            )}

            <div className="btn-row" style={{ justifyContent: 'flex-end', marginTop: 22 }}>
              <button className="btn alt" type="button" onClick={() => setShowProfile(false)}>
                Cerrar
              </button>
            </div>
          </section>
        </div>
      ) : null}
    </>
  );
}
