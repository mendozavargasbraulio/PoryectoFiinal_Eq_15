import { Link, useLocation, useParams } from 'react-router-dom';
import { PageHeader } from '../../components/ui.jsx';
import AttemptFeedback from '../../components/AttemptFeedback.jsx';

/** Muestra lo que devuelve POST /attempts ({correct, score, solution,
 *  explanation, mastery}). El cuerpo vive en <AttemptFeedback/>, compartido con
 *  el modo cola; aquí añade el resumen (Calificación y Dominio) por ser el final
 *  del intento. Sin resultado del servidor (vista directa) se marca como muestra. */
export default function StudentResult() {
  const { intentoId } = useParams();
  const location = useLocation();
  const state = location.state || {};

  const result = state.result || {};
  const isReal = Boolean(state.real);

  return (
    <>
      <PageHeader
        eyebrow="PORTAL / ESTUDIANTE"
        title="Resultado de tu actividad"
        subtitle="La retroalimentación te ayuda a entender cómo mejorar."
      />
      <AttemptFeedback
        result={result}
        answer={state.answer}
        activityType={state.activity?.type}
        options={state.activity?.options}
        showSummary={isReal}
      >
        <Link className="btn" to="/estudiante/tareas">
          Mis tareas
        </Link>
        <Link className="btn alt" to="/estudiante/progreso">
          Mi progreso
        </Link>
      </AttemptFeedback>
    </>
  );
}
