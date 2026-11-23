import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { ErrorState, PageHeader, Spinner } from '../../components/ui.jsx';
import AttemptFeedback from '../../components/AttemptFeedback.jsx';
import { getAssignmentResult, getMyAttempt } from '../../api/learning.js';
import { extractApiError } from '../../api/client.js';

/** Muestra el resultado real de un intento (POST /attempts o recarga vía
 *  GET /me/attempts/{id}) o de una asignación completada (GET /me/assignments/{id}/result). */
export default function StudentResult() {
  const { intentoId } = useParams();
  const location = useLocation();
  const state = location.state || {};

  const hasInitial = Boolean(state.real && state.result);
  const [loaded, setLoaded] = useState(() =>
    hasInitial
      ? {
          result: state.result,
          answer: state.answer,
          activityTitle: state.activity?.title,
          activityType: state.activity?.type,
          options: state.activity?.options,
        }
      : null
  );
  const [loading, setLoading] = useState(!hasInitial);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    if (hasInitial) return undefined;
    let alive = true;
    setLoading(true);
    setError('');
    const request = state.fromAssignment && state.assignmentId != null
      ? getAssignmentResult(state.assignmentId)
      : getMyAttempt(intentoId);

    request
      .then(({ data }) => {
        if (!alive) return;
        setLoaded({
          result: {
            correct: data.correct,
            score: data.score,
            solution: data.solution,
            explanation: data.explanation,
            mastery: data.mastery,
          },
          answer: data.answer,
          activityTitle: data.activityTitle,
          activityType: data.activityType,
          options: data.options,
        });
        setLoading(false);
      })
      .catch((err) => {
        if (!alive) return;
        setError(extractApiError(err).detail || 'No se pudo cargar el resultado.');
        setLoading(false);
      });

    return () => {
      alive = false;
    };
  }, [hasInitial, state.fromAssignment, state.assignmentId, intentoId, reloadKey]);

  return (
    <>
      <PageHeader
        eyebrow="PORTAL / ESTUDIANTE"
        title="Resultado de tu actividad"
        subtitle={loaded?.activityTitle || 'La retroalimentación te ayuda a entender cómo mejorar.'}
      />
      {loading ? (
        <Spinner />
      ) : error ? (
        <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />
      ) : loaded ? (
        <AttemptFeedback
          result={loaded.result}
          answer={loaded.answer}
          activityType={loaded.activityType}
          options={loaded.options}
          showSummary
        >
          <Link className="btn" to="/estudiante/tareas">
            Mis tareas
          </Link>
          <Link className="btn alt" to="/estudiante/progreso">
            Mi progreso
          </Link>
        </AttemptFeedback>
      ) : null}
    </>
  );
}
