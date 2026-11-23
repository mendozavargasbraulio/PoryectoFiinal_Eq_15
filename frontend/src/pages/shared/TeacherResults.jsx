import { useState } from 'react';
import { Badge, ErrorState, PageHeader, Spinner } from '../../components/ui.jsx';
import { useRemote, REMOTE_STATUS } from '../../api/useRemote.js';
import { listStudents, getStudentProgress } from '../../api/teacher.js';
import { extractApiError } from '../../api/client.js';
import { useAuth } from '../../auth/AuthContext.jsx';

const PAGE_SIZE = 10;
const MASTERED_THRESHOLD = 0.80;

/** GET /teacher/students (paginado y filtrable en servidor) y
 *  GET /teacher/students/{id}/progress bajo demanda (sin N+1). */
export default function TeacherResults() {
  const { role } = useAuth();

  const [searchText, setSearchText] = useState('');
  const [appliedText, setAppliedText] = useState('');
  const [page, setPage] = useState(0);

  const students = useRemote({
    fetcher: () =>
      listStudents({
        page,
        size: PAGE_SIZE,
        sort: 'name,asc',
        text: appliedText || undefined,
      }),
    deps: [page, appliedText],
  });

  const [selected, setSelected] = useState(null);
  const [progress, setProgress] = useState(null);
  const [progressBusy, setProgressBusy] = useState(false);
  const [progressError, setProgressError] = useState('');

  const rows = Array.isArray(students.data) ? students.data : students.data?.content || [];
  const totalPages = Math.max(1, students.data?.totalPages || 1);
  const currentPage = students.data?.page ?? page;

  function applySearch(e) {
    e.preventDefault();
    setAppliedText(searchText.trim());
    setPage(0);
  }

  async function openProgress(student) {
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
        eyebrow={role === 'ADMIN' ? 'PORTAL / ADMINISTRADOR' : 'PORTAL / PROFESOR'}
        title="Resultados de estudiantes"
        subtitle="Consulta el dominio por objetivo y los intentos de tus estudiantes."
      />

      {students.status === REMOTE_STATUS.error ? (
        <ErrorState message={students.error?.detail} onRetry={students.reload} />
      ) : students.status === REMOTE_STATUS.loading ? (
        <Spinner />
      ) : (
        <>
          <form className="filters" onSubmit={applySearch}>
            <input
              className="input search"
              placeholder="Buscar por nombre o correo"
              aria-label="Buscar por nombre o correo"
              value={searchText}
              onChange={(e) => setSearchText(e.target.value)}
            />
            <button className="btn alt small" type="submit">
              Buscar
            </button>
          </form>

          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Estudiante</th>
                  <th>Correo</th>
                  <th>Acción</th>
                </tr>
              </thead>
              <tbody>
                {rows.length ? (
                  rows.map((s) => (
                    <tr key={s.id}>
                      <td>{s.name}</td>
                      <td>{s.email || '—'}</td>
                      <td>
                        <button className="btn small" type="button" onClick={() => openProgress(s)}>
                          Ver progreso
                        </button>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={3}>
                      <span className="muted">
                        {appliedText
                          ? 'Sin estudiantes que coincidan con la búsqueda.'
                          : 'Aún no tienes estudiantes vinculados.'}
                      </span>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="pagination">
            <span>
              Página {currentPage + 1} de {totalPages}
              {students.data?.totalElements != null ? ` · ${students.data.totalElements} estudiantes` : ''}
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

      {selected ? (
        <div className="overlay" onClick={(e) => e.target === e.currentTarget && setSelected(null)}>
          <section className="modal modal-wide" role="dialog" aria-modal="true" aria-labelledby="progress-title">
            <p className="eyebrow">ENGLISH LAB / PROGRESO</p>
            <h2 id="progress-title">{selected.name}</h2>
            {progressBusy ? (
              <Spinner small />
            ) : progressError ? (
              <p className="error" role="alert">{progressError}</p>
            ) : progress ? (
              <ProgressSummary items={progress} />
            ) : null}
            <div className="btn-row" style={{ justifyContent: 'flex-end', marginTop: 24 }}>
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

/** Resumen de progreso a partir de List<ProgressResponse> del backend:
 *  [{objectiveId, code, name, cefrLevel, skill, mastery, attemptsCount, correctCount}]. */
function ProgressSummary({ items }) {
  const totalAttempts = items.reduce((acc, it) => acc + (Number(it.attemptsCount) || 0), 0);
  const totalCorrect = items.reduce((acc, it) => acc + (Number(it.correctCount) || 0), 0);
  const avgMastery = items.length
    ? items.reduce((acc, it) => acc + (Number(it.mastery) || 0), 0) / items.length
    : null;
  const masteredCount = items.filter((it) => Number(it.mastery) >= MASTERED_THRESHOLD).length;
  const accuracy = totalAttempts > 0 ? totalCorrect / totalAttempts : null;

  return (
    <div>
      <div className="grid g4">
        <div className="card">
          <div className="stat-label">Dominio medio</div>
          <div className="stat-number">
            {avgMastery != null ? `${Math.round(avgMastery * 100)}%` : '—'}
          </div>
        </div>
        <div className="card">
          <div className="stat-label">Precisión</div>
          <div className="stat-number">
            {accuracy != null ? `${Math.round(accuracy * 100)}%` : '—'}
          </div>
        </div>
        <div className="card">
          <div className="stat-label">Intentos</div>
          <div className="stat-number">{totalAttempts}</div>
        </div>
        <div className="card">
          <div className="stat-label">Dominados</div>
          <div className="stat-number">{masteredCount}/{items.length}</div>
        </div>
      </div>
      {items.length ? (
        <div className="table-wrap" style={{ marginTop: 16 }}>
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
              {items.map((it, i) => {
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
                    <td>
                      <Badge color="gray">{it.cefrLevel || '—'}</Badge>
                    </td>
                    <td className={blocked ? 'muted' : undefined}>
                      {it.code ? `${it.code} — ${it.name}` : it.name ?? `#${i + 1}`}
                    </td>
                    <td>{attempts > 0 ? `${correct}/${attempts}` : '0'}</td>
                    <td>
                      <Badge color={badgeColor}>{badgeText}</Badge>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="muted" style={{ marginTop: 12 }}>Sin objetivos registrados.</p>
      )}
    </div>
  );
}
