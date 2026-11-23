import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Badge, ErrorState, PageHeader, Spinner } from '../../components/ui.jsx';
import { useToast } from '../../components/Toast.jsx';
import { useRemote, REMOTE_STATUS } from '../../api/useRemote.js';
import { listAssignments, listObjectives, getPracticeSession } from '../../api/learning.js';
import { extractApiError } from '../../api/client.js';
import { activityTypeLabel, skillLabel } from '../../lib/constants.js';

const PAGE_SIZE = 10;
const MASTERED_THRESHOLD = 0.80;
const PRACTICE_SIZE = 10;
const COMPLETED = ['COMPLETED', 'DONE', 'Completada'];
const PRACTICABLE_STATES = ['AVAILABLE', 'RECOMMENDED', 'MASTERED'];

const STATE_LABELS = {
  AVAILABLE: 'Disponible',
  RECOMMENDED: 'Recomendado',
  MASTERED: 'Dominado',
  BLOCKED: 'Bloqueado',
};

function stateLabel(state) {
  return STATE_LABELS[String(state || '').toUpperCase()] || state || '—';
}

function stateBadgeColor(state) {
  switch (String(state || '').toUpperCase()) {
    case 'MASTERED':
      return 'green';
    case 'RECOMMENDED':
      return 'gold';
    default:
      return 'gray';
  }
}

function formatDate(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return new Intl.DateTimeFormat('es-MX', { day: 'numeric', month: 'short', year: 'numeric' }).format(d);
}

/** "Mis tareas" → GET /me/assignments (paginado en servidor);
 *  "Práctica libre" → GET /me/objectives con inicio directo de sesión. */
export default function StudentTasks() {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [tab, setTab] = useState('tasks');
  const [page, setPage] = useState(0);
  const [startingId, setStartingId] = useState(null);

  const { status, data, error, reload } = useRemote({
    fetcher: () =>
      tab === 'tasks'
        ? listAssignments({ page, size: PAGE_SIZE, sort: 'assignedAt,desc' })
        : listObjectives(),
    deps: [tab, page],
  });

  const raw = Array.isArray(data) ? data : data?.content || [];
  const totalPages = Math.max(1, data?.totalPages || 1);
  const currentPage = data?.page ?? page;

  const taskRows = tab === 'tasks'
    ? raw.map((a) => {
        const assignmentId = a.assignmentId ?? a.id;
        const activityId = a.activityId ?? a.activity?.id;
        return {
          id: assignmentId,
          activityId,
          activity: a.activity || null,
          activityTitle: a.activity?.title || 'Actividad asignada',
          status: a.status,
          assignedAt: a.assignedAt,
          completedAt: a.completedAt,
          teacherName: a.teacherName,
        };
      })
    : [];

  const objectiveRows = tab === 'free'
    ? raw.map((o) => ({
        objectiveId: o.objectiveId ?? o.id,
        code: o.code,
        name: o.name,
        cefrLevel: o.cefrLevel,
        skill: o.skill,
        mastery: o.mastery,
        state: o.state,
      }))
    : [];

  function switchTab(nextTab) {
    setTab(nextTab);
    setPage(0);
  }

  function openTask(row) {
    const done = COMPLETED.includes(row.status);
    if (done) {
      navigate(`/estudiante/resultado/${row.id}`, {
        state: { assignmentId: row.id, activityId: row.activityId, fromAssignment: true },
      });
    } else {
      navigate(`/estudiante/actividad/${row.activityId}`, {
        state: {
          assignmentId: row.id,
          activityId: row.activityId,
          activity: row.activity
            ? {
                id: row.activityId,
                title: row.activity.title,
                type: row.activity.type,
                level: row.activity.level,
                skill: row.activity.skill,
                prompt: row.activity.prompt,
                options: row.activity.options,
              }
            : null,
        },
      });
    }
  }

  async function startFreePractice(objectiveId) {
    if (startingId != null) return;
    setStartingId(objectiveId);
    try {
      const { data: session } = await getPracticeSession(objectiveId, { size: PRACTICE_SIZE });
      const activityIds = (Array.isArray(session?.activityIds) ? session.activityIds : []).filter(
        (value) => value != null
      );
      if (!activityIds.length) {
        showToast('Este objetivo todavía no tiene actividades para practicar.');
        return;
      }
      navigate(`/estudiante/actividad/${activityIds[0]}`, {
        state: { activityIds },
      });
    } catch (err) {
      showToast(extractApiError(err).detail || 'No se pudo iniciar la práctica.');
    } finally {
      setStartingId(null);
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="PORTAL / ESTUDIANTE"
        title="Mis tareas y práctica libre"
        subtitle="Accede a actividades asignadas o elige objetivos desbloqueados para practicar por tu cuenta."
      />

      <div className="tabs">
        <button className={`tab${tab === 'tasks' ? ' active' : ''}`} type="button" onClick={() => switchTab('tasks')}>
          Mis tareas
        </button>
        <button className={`tab${tab === 'free' ? ' active' : ''}`} type="button" onClick={() => switchTab('free')}>
          Práctica libre
        </button>
      </div>

      {status === REMOTE_STATUS.error ? (
        <ErrorState message={error?.detail} onRetry={reload} />
      ) : status === REMOTE_STATUS.loading ? (
        <Spinner />
      ) : (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  {tab === 'tasks' ? (
                    <>
                      <th>Actividad</th>
                      <th>Tipo</th>
                      <th>Estado</th>
                      <th>Asignada</th>
                      <th>Acción</th>
                    </>
                  ) : (
                    <>
                      <th>Objetivo</th>
                      <th>Nivel</th>
                      <th>Habilidad</th>
                      <th>Dominio</th>
                      <th>Estado</th>
                      <th>Acción</th>
                    </>
                  )}
                </tr>
              </thead>
              <tbody>
                {tab === 'tasks' ? (
                  taskRows.length ? (
                    taskRows.map((r) => (
                      <tr key={r.id}>
                        <td>
                          {r.activityTitle}
                          {r.teacherName ? <div className="smallprint">{r.teacherName}</div> : null}
                        </td>
                        <td><Badge color="gray">{activityTypeLabel(r.activity?.type)}</Badge></td>
                        <td>
                          <Badge color={COMPLETED.includes(r.status) ? 'green' : 'gold'}>
                            {COMPLETED.includes(r.status) ? 'Completada' : 'Pendiente'}
                          </Badge>
                        </td>
                        <td>{formatDate(r.assignedAt)}</td>
                        <td>
                          <button className="btn small" type="button" onClick={() => openTask(r)}>
                            {COMPLETED.includes(r.status) ? 'Ver resultado' : 'Resolver'}
                          </button>
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={5}><span className="muted">No hay tareas asignadas.</span></td>
                    </tr>
                  )
                ) : objectiveRows.length ? (
                  objectiveRows.map((o) => {
                    const canPractice =
                      o.objectiveId != null && PRACTICABLE_STATES.includes(String(o.state || '').toUpperCase());
                    return (
                      <tr key={o.objectiveId}>
                        <td>{o.code ? `${o.code} — ${o.name}` : o.name}</td>
                        <td><Badge color="gray">{o.cefrLevel || '—'}</Badge></td>
                        <td>{skillLabel(o.skill)}</td>
                        <td>
                          <Badge color={Number(o.mastery || 0) >= MASTERED_THRESHOLD ? 'green' : 'gold'}>
                            {o.mastery != null ? `${Math.round(Number(o.mastery) * 100)}%` : '—'}
                          </Badge>
                        </td>
                        <td><Badge color={stateBadgeColor(o.state)}>{stateLabel(o.state)}</Badge></td>
                        <td>
                          {canPractice ? (
                            <button
                              className="btn small"
                              type="button"
                              disabled={startingId != null}
                              onClick={() => startFreePractice(o.objectiveId)}
                            >
                              {startingId === o.objectiveId ? 'Preparando…' : 'Practicar'}
                            </button>
                          ) : (
                            <span className="muted">Bloqueado</span>
                          )}
                        </td>
                      </tr>
                    );
                  })
                ) : (
                  <tr>
                    <td colSpan={6}><span className="muted">No hay objetivos para mostrar.</span></td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          {tab === 'tasks' ? (
            <div className="pagination">
              <span>
                Página {currentPage + 1} de {totalPages}
                {data?.totalElements != null ? ` · ${data.totalElements} tareas` : ''}
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
          ) : null}
        </>
      )}
    </>
  );
}
