import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Badge, ErrorState, PageHeader, Spinner } from '../../components/ui.jsx';
import { useRemote, REMOTE_STATUS } from '../../api/useRemote.js';
import { listStudentsOverview, getStudentProgress } from '../../api/teacher.js';
import { extractApiError } from '../../api/client.js';

const PAGE_SIZE = 5;
const MASTERED_THRESHOLD = 0.80;

/** Inicio del profesor: tabla paginada con barras visuales de progreso y
 *  actividades asignadas de sus estudiantes (navegable con botones de flecha
 *  para no saturar la pantalla) + accesos rápidos abajo. */
export default function TeacherHome() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);

  const [selected, setSelected] = useState(null);
  const [progress, setProgress] = useState(null);
  const [progressBusy, setProgressBusy] = useState(false);
  const [progressError, setProgressError] = useState('');

  const overview = useRemote({
    fetcher: () => listStudentsOverview({ page, size: PAGE_SIZE, sort: 'name,asc' }),
    deps: [page],
  });

  const rows = overview.data?.content || [];
  const totalElements = overview.data?.totalElements ?? 0;
  const totalPages = Math.max(1, overview.data?.totalPages || 1);
  const currentPage = overview.data?.page ?? page;
  const rangeStart = totalElements === 0 ? 0 : currentPage * PAGE_SIZE + 1;
  const rangeEnd = Math.min(totalElements, currentPage * PAGE_SIZE + rows.length);

  async function openDetail(student) {
    setSelected(student);
    setProgress(null);
    setProgressError('');
    setProgressBusy(true);
    try {
      const { data } = await getStudentProgress(student.id);
      setProgress(Array.isArray(data) ? data : []);
    } catch (err) {
      setProgressError(extractApiError(err).detail || 'No se pudo cargar el progreso.');
    } finally {
      setProgressBusy(false);
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="PORTAL / PROFESOR"
        title="Organiza el aprendizaje"
        subtitle="Supervisa el progreso y las actividades asignadas de tus estudiantes."
      />

      <div className="card">
        <div className="overview-head">
          <div>
            <h3 className="card-title" style={{ marginBottom: 4 }}>
              Progreso y actividades de tus estudiantes
            </h3>
            <span className="smallprint">
              {overview.status === REMOTE_STATUS.real
                ? totalElements > 0
                  ? `Mostrando ${rangeStart}–${rangeEnd} de ${totalElements} estudiantes`
                  : 'Aún no tienes estudiantes vinculados.'
                : 'Cargando resumen…'}
            </span>
          </div>
          {totalElements > 0 ? (
            <div className="arrow-pager" aria-label="Paginación de estudiantes">
              <button
                className="btn alt small arrow-btn"
                type="button"
                aria-label="Página anterior"
                title="Página anterior"
                disabled={currentPage <= 0 || overview.status === REMOTE_STATUS.loading}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                ←
              </button>
              <span className="smallprint" style={{ minWidth: 52, textAlign: 'center' }}>
                {currentPage + 1} / {totalPages}
              </span>
              <button
                className="btn alt small arrow-btn"
                type="button"
                aria-label="Página siguiente"
                title="Página siguiente"
                disabled={currentPage >= totalPages - 1 || overview.status === REMOTE_STATUS.loading}
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              >
                →
              </button>
            </div>
          ) : null}
        </div>

        {overview.status === REMOTE_STATUS.error ? (
          <ErrorState
            message={overview.error?.detail || 'No se pudo cargar el progreso de tus estudiantes.'}
            onRetry={overview.reload}
          />
        ) : overview.status === REMOTE_STATUS.loading ? (
          <Spinner small />
        ) : rows.length ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Estudiante</th>
                  <th>Progreso general</th>
                  <th>Actividades asignadas</th>
                  <th>Intentos</th>
                  <th>Acción</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((s) => {
                  const pct = Math.round(Math.min(1, Math.max(0, Number(s.averageMastery) || 0)) * 100);
                  const totalAssigned = Number(s.completedAssignments || 0) + Number(s.pendingAssignments || 0);
                  return (
                    <tr key={s.id}>
                      <td>
                        <b>{s.name}</b>
                        <div className="smallprint">{s.email}</div>
                      </td>
                      <td className="progress-cell">
                        <div className="progress-cell-top">
                          <b>{pct}%</b>
                          <span className="smallprint">
                            {s.masteredObjectives}/{s.totalObjectives} dominados
                          </span>
                        </div>
                        <div
                          className="progress-track"
                          role="img"
                          aria-label={`Progreso de ${s.name}: ${pct}%`}
                        >
                          <div className="progress-fill" style={{ width: `${pct}%` }} />
                        </div>
                      </td>
                      <td>
                        {totalAssigned > 0 ? (
                          <div className="btn-row">
                            <Badge color="green">{s.completedAssignments} completadas</Badge>
                            <Badge color={s.pendingAssignments > 0 ? 'gold' : 'gray'}>
                              {s.pendingAssignments} pendientes
                            </Badge>
                          </div>
                        ) : (
                          <span className="muted">Sin asignaciones</span>
                        )}
                      </td>
                      <td>{s.totalAttempts}</td>
                      <td>
                        <button className="btn small alt" type="button" onClick={() => openDetail(s)}>
                          Ver detalle
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <div style={{ padding: '12px 0' }}>
            <p className="muted" style={{ marginBottom: 12 }}>
              Vincula estudiantes desde "Asignar tarea" para ver aquí su avance y actividades.
            </p>
            <button className="btn small olive" type="button" onClick={() => navigate('/asignaciones/nueva')}>
              Vincular estudiante
            </button>
          </div>
        )}
      </div>

      <div className="card mt">
        <h3 className="card-title">Acciones</h3>
        <div className="teacher-actions">
          <button className="btn coffee-outline" type="button" onClick={() => navigate('/actividades/nueva')}>
            Crear actividad
          </button>
          <button className="btn coffee-outline" type="button" onClick={() => navigate('/actividades')}>
            Banco de actividades
          </button>
          <button className="btn coffee-outline" type="button" onClick={() => navigate('/asignaciones/nueva')}>
            Asignar tarea
          </button>
          <button className="btn coffee-outline" type="button" onClick={() => navigate('/resultados')}>
            Ver resultados
          </button>
        </div>
      </div>

      {selected ? (
        <div className="overlay" onClick={(e) => e.target === e.currentTarget && setSelected(null)}>
          <section className="modal modal-wide" role="dialog" aria-modal="true" aria-labelledby="home-progress-title">
            <p className="eyebrow">ENGLISH LAB / PROGRESO</p>
            <h2 id="home-progress-title">{selected.name}</h2>
            {progressBusy ? (
              <Spinner small />
            ) : progressError ? (
              <p className="error" role="alert">{progressError}</p>
            ) : progress ? (
              <div className="table-wrap" style={{ marginTop: 12 }}>
                <table>
                  <thead>
                    <tr>
                      <th>Nivel</th>
                      <th>Objetivo</th>
                      <th>Intentos</th>
                      <th>Dominio</th>
                    </tr>
                  </thead>
                  <tbody>
                    {progress.map((it, i) => {
                      const mastery = Number(it.mastery) || 0;
                      const attempts = Number(it.attemptsCount) || 0;
                      const correct = Number(it.correctCount) || 0;
                      const blocked = String(it.state || '').toUpperCase() === 'BLOCKED';
                      const pct = Math.round(mastery * 100);
                      const badgeColor = blocked
                        ? 'gray'
                        : mastery >= MASTERED_THRESHOLD
                          ? 'green'
                          : attempts === 0
                            ? 'gray'
                            : mastery >= 0.5
                              ? 'gold'
                              : 'red';
                      const badgeText = blocked
                        ? 'Bloqueada'
                        : attempts === 0
                          ? `${pct}% · Sin intentos`
                          : `${pct}%`;
                      return (
                        <tr key={it.objectiveId ?? i}>
                          <td><Badge color="gray">{it.cefrLevel || '—'}</Badge></td>
                          <td className={blocked ? 'muted' : undefined}>
                            {it.code ? `${it.code} — ${it.name}` : it.name}
                          </td>
                          <td>{attempts > 0 ? `${correct}/${attempts}` : '0'}</td>
                          <td><Badge color={badgeColor}>{badgeText}</Badge></td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            ) : null}
            <div className="btn-row" style={{ justifyContent: 'flex-end', marginTop: 20 }}>
              <button className="btn alt" type="button" onClick={() => setSelected(null)}>
                Cerrar
              </button>
            </div>
          </section>
        </div>
      ) : null}
    </>
  );
}
