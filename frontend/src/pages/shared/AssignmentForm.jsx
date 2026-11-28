import { useState } from 'react';
import { Badge, ErrorState, PageHeader, Spinner } from '../../components/ui.jsx';
import Input from '../../components/Input.jsx';
import { useToast } from '../../components/Toast.jsx';
import { useRemote, REMOTE_STATUS } from '../../api/useRemote.js';
import {
  listStudents,
  linkStudent,
  createAssignment,
  listTeacherAssignments,
  updateAssignmentDueDate,
} from '../../api/teacher.js';
import { listActivities } from '../../api/activities.js';
import { extractApiError } from '../../api/client.js';
import { email as validateEmail, required } from '../../lib/validators.js';
import { LEVELS } from '../../lib/constants.js';
import { useAuth } from '../../auth/AuthContext.jsx';

const ASSIGNMENTS_PAGE_SIZE = 8;

function dateInputToIso(dateStr) {
  if (!dateStr) return null;
  const [y, m, d] = dateStr.split('-').map(Number);
  if (!y || !m || !d) return null;
  return new Date(y, m - 1, d, 23, 59, 59).toISOString();
}

function isoToDateInput(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** Asignación de tareas con fecha de entrega opcional, edición de fecha límite
 *  en tareas existentes y vinculación de alumnos por correo. */
export default function AssignmentForm() {
  const { role } = useAuth();
  const { showToast } = useToast();

  const [activityFilter, setActivityFilter] = useState({ text: '', level: '' });
  const [appliedFilter, setAppliedFilter] = useState({ text: '', level: '' });
  const [assignmentsPage, setAssignmentsPage] = useState(0);
  const [dueDrafts, setDueDrafts] = useState({});
  const [savingDueId, setSavingDueId] = useState(null);

  const students = useRemote({
    fetcher: () => listStudents({ page: 0, size: 100, sort: 'name,asc' }),
  });
  const activities = useRemote({
    fetcher: () =>
      listActivities({
        page: 0,
        size: 100,
        sort: 'title,asc',
        text: appliedFilter.text || undefined,
        level: appliedFilter.level || undefined,
      }),
    deps: [appliedFilter],
  });
  const teacherAssignments = useRemote({
    fetcher: () =>
      listTeacherAssignments({
        page: assignmentsPage,
        size: ASSIGNMENTS_PAGE_SIZE,
        sort: 'assignedAt,desc',
      }),
    deps: [assignmentsPage],
  });

  const studentRows = Array.isArray(students.data) ? students.data : students.data?.content || [];
  const activityRows = Array.isArray(activities.data) ? activities.data : activities.data?.content || [];
  const totalActivities = activities.data?.totalElements ?? activityRows.length;

  const assignedRows = Array.isArray(teacherAssignments.data)
    ? teacherAssignments.data
    : teacherAssignments.data?.content || [];
  const assignedTotalPages = Math.max(1, teacherAssignments.data?.totalPages || 1);
  const assignedCurrentPage = teacherAssignments.data?.page ?? assignmentsPage;

  const [values, setValues] = useState({ student: '', activity: '', dueDate: '' });
  const [touched, setTouched] = useState({});
  const [errors, setErrors] = useState({});
  const [submitError, setSubmitError] = useState('');
  const [busy, setBusy] = useState(false);

  const [linkEmail, setLinkEmail] = useState('');
  const [linkError, setLinkError] = useState('');
  const [linking, setLinking] = useState(false);

  const rules = {
    student: (v) => required(v, 'El estudiante'),
    activity: (v) => required(v, 'La actividad'),
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setValues((v) => ({ ...v, [name]: value }));
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

  function applyActivityFilter() {
    setValues((v) => ({ ...v, activity: '' }));
    setAppliedFilter({
      text: activityFilter.text.trim(),
      level: activityFilter.level,
    });
  }

  async function handleSubmit(e) {
    e.preventDefault();
    const nextErrors = { student: rules.student(values.student), activity: rules.activity(values.activity) };
    setErrors(nextErrors);
    setTouched({ student: true, activity: true });
    if (Object.values(nextErrors).some(Boolean)) return;

    setBusy(true);
    setSubmitError('');
    try {
      await createAssignment({
        studentId: Number(values.student),
        activityId: Number(values.activity),
        dueAt: dateInputToIso(values.dueDate),
      });
      showToast('Tarea asignada.');
      setValues({ student: '', activity: '', dueDate: '' });
      setTouched({});
      setAssignmentsPage(0);
      teacherAssignments.reload();
    } catch (err) {
      const apiError = extractApiError(err);
      setSubmitError(apiError.detail || 'No se pudo asignar la tarea.');
    } finally {
      setBusy(false);
    }
  }

  async function handleSaveDueDate(row) {
    const draft = dueDrafts[row.id] !== undefined ? dueDrafts[row.id] : isoToDateInput(row.dueAt);
    setSavingDueId(row.id);
    try {
      await updateAssignmentDueDate(row.id, { dueAt: dateInputToIso(draft) });
      showToast(draft ? 'Fecha de entrega actualizada.' : 'Fecha de entrega eliminada.');
      setDueDrafts((prev) => {
        const next = { ...prev };
        delete next[row.id];
        return next;
      });
      teacherAssignments.reload();
    } catch (err) {
      showToast(extractApiError(err).detail || 'No se pudo actualizar la fecha de entrega.');
    } finally {
      setSavingDueId(null);
    }
  }

  async function handleLink(e) {
    e.preventDefault();
    const err = validateEmail(linkEmail);
    setLinkError(err);
    if (err) return;

    setLinking(true);
    try {
      await linkStudent({ email: linkEmail.trim() });
      showToast('Estudiante vinculado.');
      setLinkEmail('');
      setLinkError('');
      students.reload();
    } catch (err) {
      const apiError = extractApiError(err);
      if (err?.response?.status === 404) {
        setLinkError('No existe un estudiante con ese correo.');
      } else if (apiError.fieldErrors?.[0]?.message) {
        setLinkError(apiError.fieldErrors[0].message);
      } else {
        setLinkError(apiError.detail || 'No se pudo vincular al estudiante.');
      }
    } finally {
      setLinking(false);
    }
  }

  const selectField = (id, label, options, name, value) => (
    <div>
      <label className="form-label" htmlFor={id}>{label}</label>
      <select
        id={id}
        className={`input${touched[name] && errors[name] ? ' invalid' : ''}`}
        name={name}
        value={value}
        onChange={handleChange}
        onBlur={handleBlur}
      >
        <option value="">Selecciona…</option>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
      {touched[name] && errors[name] ? <p className="error" role="alert">{errors[name]}</p> : null}
    </div>
  );

  const remoteReady = (remote) => remote.status === REMOTE_STATUS.real;
  const formReady = remoteReady(students) && remoteReady(activities);

  const selectOrState = (remote, { id, label, options, name }) => {
    if (remote.status === REMOTE_STATUS.error) {
      return (
        <ErrorState
          message={remote.error?.detail || `No se pudo cargar la lista de ${label.toLowerCase()}.`}
          onRetry={remote.reload}
        />
      );
    }
    if (!remoteReady(remote)) {
      return (
        <div>
          <label className="form-label" htmlFor={id}>{label}</label>
          <div className="spinner-wrap">
            <Spinner small label="" />
          </div>
        </div>
      );
    }
    return selectField(id, label, options, name, values[name]);
  };

  return (
    <>
      <PageHeader
        eyebrow={role === 'ADMIN' ? 'PORTAL / ADMINISTRADOR' : 'PORTAL / PROFESOR'}
        title="Asignar actividad"
        subtitle="Selecciona a un estudiante, la actividad y opcionalmente la fecha de entrega."
      />
      <form className="card light assign-card" onSubmit={handleSubmit} noValidate>
        {selectOrState(students, {
          id: 'assign-student',
          label: 'Estudiante',
          name: 'student',
          options: studentRows.map((s) => ({ value: s.id, label: `${s.name} (${s.email || 'sin correo'})` })),
        })}

        <div className="filters" style={{ marginTop: 18, marginBottom: 4 }}>
          <input
            className="input search"
            placeholder="Filtrar actividad por título"
            aria-label="Filtrar actividad por título"
            value={activityFilter.text}
            onChange={(e) => setActivityFilter((f) => ({ ...f, text: e.target.value }))}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault();
                applyActivityFilter();
              }
            }}
          />
          <select
            className="input"
            aria-label="Filtrar por nivel"
            value={activityFilter.level}
            onChange={(e) => setActivityFilter((f) => ({ ...f, level: e.target.value }))}
          >
            <option value="">Todos los niveles</option>
            {LEVELS.map((l) => (
              <option key={l} value={l}>{l}</option>
            ))}
          </select>
          <button className="btn alt small" type="button" onClick={applyActivityFilter}>
            Buscar
          </button>
        </div>

        {selectOrState(activities, {
          id: 'assign-activity',
          label: 'Actividad',
          name: 'activity',
          options: activityRows.map((a) => ({ value: a.id, label: `${a.title} · ${a.level || ''}` })),
        })}
        {remoteReady(activities) && totalActivities > activityRows.length ? (
          <p className="help">
            Mostrando {activityRows.length} de {totalActivities} actividades. Usa el filtro de arriba para buscar otras.
          </p>
        ) : null}

        <Input
          label="Fecha de entrega (opcional)"
          name="dueDate"
          type="date"
          value={values.dueDate}
          onChange={handleChange}
          help="Puedes dejarla vacía si la actividad no tiene fecha límite."
        />

        {submitError ? <p className="form-status" role="alert">{submitError}</p> : null}

        <button className="btn wide" type="submit" style={{ marginTop: 14 }} disabled={busy || !formReady}>
          {busy ? 'Asignando…' : 'Asignar actividad'}
        </button>
        {!formReady && students.status !== REMOTE_STATUS.loading && activities.status !== REMOTE_STATUS.loading ? (
          <p className="muted" style={{ marginTop: 10 }}>
            Revisa los avisos de arriba para cargar las listas.
          </p>
        ) : null}
      </form>

      <section className="card mt">
        <h3 className="card-title">Tareas asignadas y fechas de entrega</h3>
        <p className="card-sub" style={{ marginBottom: 14 }}>
          Modifica la fecha límite de entrega cuando lo necesites.
        </p>
        {teacherAssignments.status === REMOTE_STATUS.error ? (
          <ErrorState
            message={teacherAssignments.error?.detail || 'No se pudieron cargar las tareas asignadas.'}
            onRetry={teacherAssignments.reload}
          />
        ) : teacherAssignments.status === REMOTE_STATUS.loading ? (
          <Spinner small />
        ) : assignedRows.length ? (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Estudiante</th>
                    <th>Actividad</th>
                    <th>Estado</th>
                    <th>Fecha de entrega</th>
                  </tr>
                </thead>
                <tbody>
                  {assignedRows.map((row) => {
                    const currentVal = isoToDateInput(row.dueAt);
                    const draftVal = dueDrafts[row.id] !== undefined ? dueDrafts[row.id] : currentVal;
                    const dirty = draftVal !== currentVal;
                    const done = row.status === 'COMPLETED';
                    return (
                      <tr key={row.id}>
                        <td>{row.studentName || `Alumno #${row.studentId}`}</td>
                        <td>{row.activityTitle || `Actividad #${row.activityId}`}</td>
                        <td>
                          <Badge color={done ? 'green' : 'gold'}>
                            {done ? 'Completada' : 'Pendiente'}
                          </Badge>
                        </td>
                        <td>
                          <div className="due-inline">
                            <input
                              type="date"
                              className="input"
                              aria-label={`Fecha de entrega para ${row.activityTitle || row.id}`}
                              value={draftVal}
                              disabled={savingDueId === row.id}
                              onChange={(e) =>
                                setDueDrafts((prev) => ({ ...prev, [row.id]: e.target.value }))
                              }
                            />
                            <button
                              type="button"
                              className="btn small alt"
                              disabled={!dirty || savingDueId === row.id}
                              onClick={() => handleSaveDueDate(row)}
                            >
                              {savingDueId === row.id ? 'Guardando…' : 'Guardar'}
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div className="pagination">
              <span>
                Página {assignedCurrentPage + 1} de {assignedTotalPages}
                {teacherAssignments.data?.totalElements != null
                  ? ` · ${teacherAssignments.data.totalElements} tareas`
                  : ''}
              </span>
              <div className="btn-row">
                <button
                  className="btn alt small"
                  type="button"
                  disabled={assignedCurrentPage <= 0}
                  onClick={() => setAssignmentsPage((p) => Math.max(0, p - 1))}
                >
                  Anterior
                </button>
                <button
                  className="btn alt small"
                  type="button"
                  disabled={assignedCurrentPage >= assignedTotalPages - 1}
                  onClick={() => setAssignmentsPage((p) => Math.min(assignedTotalPages - 1, p + 1))}
                >
                  Siguiente
                </button>
              </div>
            </div>
          </>
        ) : (
          <p className="muted">Todavía no has asignado actividades.</p>
        )}
      </section>

      <form className="card light assign-card" onSubmit={handleLink} noValidate>
        <h3 className="card-title">Vincular estudiante por correo</h3>
        <p className="card-sub">
          Agrega un alumno existente (rol ESTUDIANTE) a tu lista de estudiantes.
        </p>
        <Input
          label="Correo del estudiante"
          name="linkEmail"
          type="email"
          placeholder="estudiante@correo.com"
          value={linkEmail}
          onChange={(e) => {
            setLinkEmail(e.target.value);
            if (linkError) setLinkError('');
          }}
          onBlur={() => setLinkError(validateEmail(linkEmail))}
          error={linkError}
        />
        <button className="btn" type="submit" style={{ marginTop: 14 }} disabled={linking}>
          {linking ? 'Vinculando…' : 'Vincular estudiante'}
        </button>
      </form>
    </>
  );
}
