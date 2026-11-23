import { Link } from 'react-router-dom';
import { useAuth, homePathForRole } from '../auth/AuthContext.jsx';

export default function NotFound() {
  const { isAuthenticated, role } = useAuth();
  const target = isAuthenticated ? homePathForRole(role) : '/login';
  const label = isAuthenticated ? 'Volver a mi panel' : 'Ir a iniciar sesión';

  return (
    <main className="auth-page">
      <section className="auth-zone" style={{ minHeight: 0 }}>
        <div className="auth-form">
          <div className="auth-inner">
            <p className="eyebrow">ENGLISH LAB / 404</p>
            <h1>Página no encontrada.</h1>
            <p className="muted">El enlace que seguiste no existe o cambió de dirección.</p>
            <div className="auth-links">
              <Link to={target}>{label}</Link>
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}
