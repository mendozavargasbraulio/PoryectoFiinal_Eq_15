import { useState } from 'react';
import { ErrorState, PageHeader, Spinner } from '../../components/ui.jsx';
import ConfirmModal from '../../components/ConfirmModal.jsx';
import Input from '../../components/Input.jsx';
import { useToast } from '../../components/Toast.jsx';
import { useRemote, REMOTE_STATUS } from '../../api/useRemote.js';
import { listUsers, createUser, updateUser, removeUser } from '../../api/users.js';
import { extractApiError } from '../../api/client.js';
import { useAuth } from '../../auth/AuthContext.jsx';
import { roleLabel } from '../../lib/format.js';
import { required, email as validateEmail, password as validatePassword } from '../../lib/validators.js';

const PAGE_SIZE = 5;
const ROLES = ['ADMIN', 'TEACHER', 'STUDENT'];

/** CRUD de usuarios (ADMIN): GET/POST/PUT/DELETE /admin/users con paginación
 *  y filtros en servidor. Alta/edición en modal con errores bajo cada input;
 *  baja con <ConfirmModal/> (bloquea la auto-eliminación del admin actual). */
export default function AdminUsers() {
  const { user: currentUser } = useAuth();
  const { showToast } = useToast();

  const [textFilter, setTextFilter] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [applied, setApplied] = useState({ text: '', role: '' });
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState(null); // null | {} (crear) | usuario (editar)
  const [toDelete, setToDelete] = useState(null);
  const [busy, setBusy] = useState(false);

  const { status, data, error, reload } = useRemote({
    fetcher: () =>
      listUsers({
        page,
        size: PAGE_SIZE,
        sort: 'name,asc',
        role: applied.role || undefined,
        text: applied.text || undefined,
      }),
    deps: [page, applied],
  });

  // Lista de profesores para asignar o preservar `teacherId` al editar un STUDENT.
  const teachersRemote = useRemote({
    fetcher: () => listUsers({ page: 0, size: 100, sort: 'name,asc', role: 'TEACHER' }),
  });
  const teachers = teachersRemote.status === REMOTE_STATUS.real
    ? teachersRemote.data?.content || []
    : [];

  const content = data?.content || [];
  const totalPages = Math.max(1, data?.totalPages || 1);
  const currentPage = data?.page ?? page;

  function applyFilter(e) {
    e.preventDefault();
    setApplied({ text: textFilter.trim(), role: roleFilter });
    setPage(0);
  }

  async function saveUser(payload) {
    const isNew = !editing?.id;
    setBusy(true);
    try {
      if (isNew) {
        await createUser(payload);
      } else {
        await updateUser(editing.id, payload);
      }
      showToast(isNew ? 'Usuario creado.' : 'Usuario actualizado.');
      setEditing(null);
      reload();
      if (payload.role === 'TEACHER' || editing?.role === 'TEACHER') {
        teachersRemote.reload();
      }
    } catch (err) {
      return extractApiError(err);
    } finally {
      setBusy(false);
    }
    return null;
  }

  async function confirmDelete() {
    setBusy(true);
    try {
      await removeUser(toDelete.id);
      showToast('Usuario eliminado.');
      const deletedTeacher = toDelete.role === 'TEACHER';
      setToDelete(null);
      if (content.length === 1 && currentPage > 0) {
        setPage((p) => Math.max(0, p - 1));
      } else {
        reload();
      }
      if (deletedTeacher) teachersRemote.reload();
    } catch (err) {
      showToast(extractApiError(err).detail || 'No se pudo eliminar el usuario.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="PORTAL / ADMINISTRADOR"
        title="Administración de usuarios"
        subtitle="Crea y administra cuentas."
      />

      {status === REMOTE_STATUS.error ? (
        <ErrorState message={error?.detail} onRetry={reload} />
      ) : status === REMOTE_STATUS.loading ? (
        <Spinner />
      ) : (
        <>
          <form className="filters" onSubmit={applyFilter}>
            <input
              className="input search"
              placeholder="Buscar nombre o correo"
              aria-label="Buscar nombre o correo"
              value={textFilter}
              onChange={(e) => setTextFilter(e.target.value)}
            />
            <select
              className="input"
              aria-label="Rol"
              value={roleFilter}
              onChange={(e) => setRoleFilter(e.target.value)}
            >
              <option value="">Todos los roles</option>
              <option value="ADMIN">Administrador</option>
              <option value="TEACHER">Profesor</option>
              <option value="STUDENT">Estudiante</option>
            </select>
            <button className="btn alt small" type="submit">
              Filtrar
            </button>
            <button className="btn coffee-outline small" type="button" onClick={() => setEditing({})}>
              Crear usuario
            </button>
          </form>

          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Correo</th>
                  <th>Rol</th>
                  <th>Profesor asignado</th>
                  <th>Teléfono</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {content.length ? (
                  content.map((u) => {
                    const isSelf = currentUser?.id != null && Number(currentUser.id) === Number(u.id);
                    return (
                      <tr key={u.id}>
                        <td>{u.name}</td>
                        <td>{u.email}</td>
                        <td>{roleLabel(u.role)}</td>
                        <td>{u.teacherName || '—'}</td>
                        <td>{u.phone || '—'}</td>
                        <td>
                          <div className="btn-row">
                            <button className="btn alt small" type="button" onClick={() => setEditing(u)}>
                              Editar
                            </button>
                            <button
                              className="btn rust small"
                              type="button"
                              disabled={isSelf}
                              title={isSelf ? 'No puedes eliminar tu propia cuenta' : undefined}
                              onClick={() => setToDelete(u)}
                            >
                              Eliminar
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                ) : (
                  <tr>
                    <td colSpan={6}>
                      <span className="muted">Sin usuarios para este filtro.</span>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="pagination">
            <span>
              Página {currentPage + 1} de {totalPages}
              {data?.totalElements != null ? ` · ${data.totalElements} usuarios` : ''}
            </span>
            <div className="btn-row">
              <button
                className="btn alt small"
                type="button"
                disabled={currentPage <= 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Anterior
              </button>
              <button
                className="btn alt small"
                type="button"
                disabled={currentPage >= totalPages - 1}
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              >
                Siguiente
              </button>
            </div>
          </div>
        </>
      )}

      {editing ? (
        <UserModal
          key={editing.id ?? 'new'}
          user={editing}
          teachers={teachers}
          busy={busy}
          onClose={() => setEditing(null)}
          onSave={saveUser}
        />
      ) : null}

      <ConfirmModal
        open={Boolean(toDelete)}
        title="Eliminar usuario"
        description={toDelete ? `¿Quieres eliminar la cuenta de ${toDelete.name}?` : ''}
        confirmLabel="Eliminar"
        busy={busy}
        onConfirm={confirmDelete}
        onCancel={() => setToDelete(null)}
      />
    </>
  );
}

/** Alta/edición en modal con errores bajo cada input. La contraseña solo se pide
 *  en alta; en edición es opcional. En STUDENT conserva/permite elegir teacherId. */
function UserModal({ user, teachers, busy, onClose, onSave }) {
  const isNew = !user?.id;
  const [values, setValues] = useState({
    name: user?.name || '',
    email: user?.email || '',
    password: '',
    role: user?.role || '',
    teacherId: user?.teacherId != null ? String(user.teacherId) : '',
    phone: user?.phone || '',
  });
  const [errors, setErrors] = useState({ name: '', email: '', password: '', role: '', teacherId: '', phone: '' });
  const [touched, setTouched] = useState({});
  const [submitError, setSubmitError] = useState('');

  const rules = {
    name: (v) => required(v, 'El nombre') || (String(v || '').trim().length > 120 ? 'Máximo 120 caracteres.' : ''),
    email: (v) =>
      required(v, 'El correo')
      || validateEmail(v)
      || (String(v || '').trim().length > 255 ? 'Máximo 255 caracteres.' : ''),
    password: (v) => {
      if (!isNew && !String(v || '').trim()) return '';
      return validatePassword(v);
    },
    role: (v) => required(v, 'El rol'),
    teacherId: () => '',
    phone: (v) => (String(v || '').trim().length > 20 ? 'Máximo 20 caracteres.' : ''),
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setValues((v) => ({
      ...v,
      [name]: value,
      ...(name === 'role' && value !== 'STUDENT' ? { teacherId: '' } : {}),
    }));
    setSubmitError('');
    if (touched[name] && rules[name]) {
      setErrors((errs) => ({ ...errs, [name]: rules[name](value) }));
    }
  };

  const handleBlur = (e) => {
    const { name, value } = e.target;
    setTouched((t) => ({ ...t, [name]: true }));
    if (rules[name]) {
      setErrors((errs) => ({ ...errs, [name]: rules[name](value) }));
    }
  };

  async function handleSubmit(e) {
    e.preventDefault();
    const nextErrors = {};
    for (const key of Object.keys(rules)) nextErrors[key] = rules[key](values[key]);
    setErrors(nextErrors);
    setTouched({ name: true, email: true, password: true, role: true, teacherId: true, phone: true });
    if (Object.values(nextErrors).some(Boolean)) return;

    const payload = {
      name: values.name.trim(),
      email: values.email.trim(),
      role: values.role,
      teacherId: values.role === 'STUDENT' && values.teacherId ? Number(values.teacherId) : null,
      phone: values.phone.trim() || null,
    };
    if (isNew || values.password.trim()) payload.password = values.password;

    const apiError = await onSave(payload);
    if (apiError) {
      const fieldErrors = {};
      (apiError.fieldErrors || []).forEach((f) => {
        if (f.field in values) fieldErrors[f.field] = f.message;
      });
      setErrors((prev) => ({ ...prev, ...fieldErrors }));
      setSubmitError(Object.keys(fieldErrors).length ? '' : apiError.detail || 'No se pudo guardar el usuario.');
    }
  }

  return (
    <div
      className="overlay"
      onClick={(e) => {
        if (e.target === e.currentTarget && !busy) onClose();
      }}
    >
      <section className="modal" role="dialog" aria-modal="true" aria-labelledby="user-modal-title">
        <p className="eyebrow">ENGLISH LAB / {isNew ? 'NUEVO' : 'EDITAR'} USUARIO</p>
        <h2 id="user-modal-title">{isNew ? 'Crear usuario' : 'Editar usuario'}</h2>
        <form onSubmit={handleSubmit} noValidate>
          <Input
            label="Nombre"
            name="name"
            placeholder="Nombre y apellidos"
            value={values.name}
            onChange={handleChange}
            onBlur={handleBlur}
            error={touched.name ? errors.name : ''}
          />
          <Input
            label="Correo electrónico"
            name="email"
            type="email"
            placeholder="nombre@correo.com"
            value={values.email}
            onChange={handleChange}
            onBlur={handleBlur}
            error={touched.email ? errors.email : ''}
          />
          <Input
            label={isNew ? 'Contraseña' : 'Contraseña (opcional)'}
            name="password"
            type="password"
            placeholder={isNew ? 'Mín. 8 caracteres' : 'Dejar vacía para no cambiarla'}
            help="Debe incluir mayúscula, número y carácter especial."
            value={values.password}
            onChange={handleChange}
            onBlur={handleBlur}
            error={touched.password ? errors.password : ''}
          />
          <div>
            <label className="form-label" htmlFor="user-role">Rol</label>
            <select
              id="user-role"
              className={`input${touched.role && errors.role ? ' invalid' : ''}`}
              name="role"
              value={values.role}
              onChange={handleChange}
              onBlur={handleBlur}
            >
              <option value="">Selecciona…</option>
              {ROLES.map((r) => (
                <option key={r} value={r}>
                  {roleLabel(r)}
                </option>
              ))}
            </select>
            {touched.role && errors.role ? <p className="error" role="alert">{errors.role}</p> : null}
          </div>
          {values.role === 'STUDENT' ? (
            <div>
              <label className="form-label" htmlFor="user-teacher">Profesor asignado (opcional)</label>
              <select
                id="user-teacher"
                className="input"
                name="teacherId"
                value={values.teacherId}
                onChange={handleChange}
                onBlur={handleBlur}
              >
                <option value="">Sin profesor asignado</option>
                {teachers.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name} ({t.email})
                  </option>
                ))}
              </select>
            </div>
          ) : null}
          <Input
            label="Teléfono (opcional)"
            name="phone"
            placeholder="+34 600 000 000"
            value={values.phone}
            onChange={handleChange}
            onBlur={handleBlur}
            error={touched.phone ? errors.phone : ''}
          />
          {submitError ? <p className="form-status" role="alert">{submitError}</p> : null}
          <div className="btn-row" style={{ justifyContent: 'flex-end', marginTop: 24 }}>
            <button className="btn alt" type="button" onClick={onClose} disabled={busy}>
              Cancelar
            </button>
            <button className="btn" type="submit" disabled={busy}>
              {busy ? 'Guardando…' : 'Guardar'}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
