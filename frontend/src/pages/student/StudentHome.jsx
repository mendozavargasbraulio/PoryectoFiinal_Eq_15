import { useNavigate } from 'react-router-dom';
import { Badge, ErrorState, PageHeader, Spinner } from '../../components/ui.jsx';
import MasteryChart from '../../components/MasteryChart.jsx';
import { useRemote, REMOTE_STATUS } from '../../api/useRemote.js';
import { listAssignments, listObjectives } from '../../api/learning.js';

const COMPLETED = ['COMPLETED', 'DONE', 'Completada'];

function formatAssignedDate(iso) {
  if (!iso) return 'Sin fecha';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return 'Sin fecha';
  return new Intl.DateTimeFormat('es-MX', { day: 'numeric', month: 'short' }).format(d);
}

/** Inicio del estudiante: dominio por nivel (GET /me/objectives) y
 *  tareas recientes (GET /me/assignments). */
export default function StudentHome() {
  const navigate = useNavigate();

  const objectives = useRemote({ fetcher: () => listObjectives() });
  const assignments = useRemote({
    fetcher: () => listAssignments({ page: 0, size: 3, sort: 'assignedAt,desc' }),
  });

  const taskRows = Array.isArray(assignments.data) ? assignments.data : assignments.data?.content || [];
  const objectiveRows = Array.isArray(objectives.data) ? objectives.data : objectives.data?.content || [];

  /** Dominio medio: media de `mastery` de los objetivos. */
  const averageMastery = objectiveRows.length
    ? Math.round(
        (objectiveRows.reduce((sum, o) => sum + Number(o.mastery || 0), 0) / objectiveRows.length) * 100
      )
    : null;

  function openTask(t) {
    const id = t.assignmentId ?? t.id;
    const activityId = t.activityId ?? t.activity?.id;
    const done = COMPLETED.includes(t.status);
    if (done) {
      navigate(`/estudiante/resultado/${id}`, {
        state: { assignmentId: id, activityId, fromAssignment: true },
      });
    } else {
      navigate(`/estudiante/actividad/${activityId}`, {
        state: {
          assignmentId: id,
          activityId,
          activity: t.activity
            ? {
                id: activityId,
                title: t.activity.title,
                type: t.activity.type,
                level: t.activity.level,
                skill: t.activity.skill,
                prompt: t.activity.prompt,
                options: t.activity.options,
              }
            : null,
        },
      });
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="PORTAL / ESTUDIANTE"
        title="Tu espacio para aprender"
        subtitle="Practica un poco cada día. Tus ejercicios, tareas y resultados están aquí."
      />

      <div className="mastery-inline">
        <span>Dominio medio:</span>
        <b>{averageMastery == null ? '—' : `${averageMastery}%`}</b>
        {averageMastery != null ? (
          <div
            className="progress-track"
            role="img"
            aria-label={`Dominio medio: ${averageMastery}%`}
          >
            <div
              className="progress-fill"
              style={{ width: `${Math.min(100, Math.max(0, averageMastery))}%` }}
            />
          </div>
        ) : null}
      </div>

      <div className="grid g2">
        <div className="card">
          <h3 className="card-title">Dominio por nivel</h3>
          <MasteryChart
            status={objectives.status}
            rows={objectiveRows}
            error={objectives.error}
            onRetry={objectives.reload}
          />
        </div>
        <div className="card">
          <h3 className="card-title">Tareas recientes</h3>
          {assignments.status === REMOTE_STATUS.loading ? (
            <Spinner small />
          ) : assignments.status === REMOTE_STATUS.error ? (
            <ErrorState
              message={assignments.error?.detail || 'No se pudieron cargar tus tareas.'}
              onRetry={assignments.reload}
            />
          ) : taskRows.length ? (
            taskRows.slice(0, 3).map((t) => {
              const id = t.assignmentId ?? t.id;
              const done = COMPLETED.includes(t.status);
              return (
                <div className="panel-row" key={id}>
                  <div>
                    <b>{t.activity?.title || 'Actividad asignada'}</b>
                    <div className="smallprint">
                      {t.teacherName || 'Profesor'} · {formatAssignedDate(t.assignedAt)}
                      {t.dueAt ? ` · Entrega: ${formatAssignedDate(t.dueAt)}` : ''}
                    </div>
                  </div>
                  <div className="btn-row">
                    <Badge color={done ? 'green' : 'gold'}>{done ? 'Completada' : 'Pendiente'}</Badge>
                    <button className="btn alt small" type="button" onClick={() => openTask(t)}>
                      {done ? 'Ver' : 'Resolver'}
                    </button>
                  </div>
                </div>
              );
            })
          ) : (
            <p className="muted">Aún no tienes tareas asignadas.</p>
          )}
          <div style={{ marginTop: 14 }}>
            <button className="btn alt small" type="button" onClick={() => navigate('/estudiante/tareas')}>
              Ver todas las tareas
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
