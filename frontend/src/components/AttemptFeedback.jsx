/** Normaliza las opciones a [{id,text}]. La API student devuelve `[{id,text}]`. */
function normalizeOptions(options) {
  if (!Array.isArray(options)) return [];
  return options.map((o, i) =>
    typeof o === 'string'
      ? { id: o, text: o }
      : { id: o.id ?? i, text: o.text ?? o.label ?? String(o) }
  );
}

function formatAnswer(value, activityType, options) {
  if (value == null || value === '') return '';
  if (activityType !== 'MULTIPLE_CHOICE' || !options.length) return String(value);
  const found = options.find((o) => String(o.id) === String(value));
  if (!found) return String(value);
  return String(found.id) === String(found.text) ? String(found.text) : `${found.id} — ${found.text}`;
}

/** Calificación del intento en porcentaje (acotada a 0–100%). */
function scorePercent(result, correct) {
  const raw = result.score != null ? Number(result.score) : NaN;
  if (!Number.isFinite(raw)) return correct ? 100 : 0;
  return Math.round(Math.min(1, Math.max(0, raw)) * 100);
}

export default function AttemptFeedback({
  result = {},
  answer: rawAnswer,
  activityType,
  options: rawOptions,
  showSummary = false,
  children,
}) {
  const correct = Boolean(result.correct);
  const explanation = result.explanation;
  const mastery = result.mastery;

  const options = normalizeOptions(rawOptions);
  const solution = formatAnswer(result.solution, activityType, options);
  const answerValue = result.answer ?? rawAnswer;
  const answer = formatAnswer(answerValue, activityType, options);
  const hasAnswerValue = answerValue != null;
  const pct = scorePercent(result, correct);
  const masteryPct = mastery != null ? Math.round(Number(mastery) * 100) : null;
  const wrong = !correct;

  return (
    <div
      className={`card light${wrong ? ' feedback-wrong' : ''}`}
      style={{ maxWidth: 750, margin: 'auto' }}
    >
      <p className="feedback-status-line">
        {correct ? 'Respuesta correcta' : hasAnswerValue && !answer ? 'Sin respuesta' : 'Respuesta incorrecta'}
      </p>
      <h2 className="feedback-heading">
        {correct ? '¡Muy bien!' : 'Sigue practicando'}
      </h2>
      {hasAnswerValue ? (
        <p className="feedback-detail">
          {answer ? (
            <>
              Tu respuesta: <b>{answer}</b>
            </>
          ) : (
            <b>Sin respuesta</b>
          )}
        </p>
      ) : null}
      {!correct && solution ? (
        <p className="feedback-detail">
          Respuesta correcta: <b>{solution}</b>
        </p>
      ) : null}
      <div className="feedback-note">
        <b>Explicación</b>
        <p>
          {explanation ||
            'Revisa la explicación de la actividad y vuelve a intentarlo: cada intento ajusta tu dominio del objetivo.'}
        </p>
      </div>
      {showSummary ? (
        <>
          <div className="divider" />
          <div className="grid g2">
            <div className="card">
              <div className="stat-label">Calificación</div>
              <div className="stat-number">{pct}%</div>
            </div>
            <div className="card">
              <div className="stat-label">Dominio del tema</div>
              <div className="stat-number">{masteryPct != null ? `${masteryPct}%` : '—'}</div>
            </div>
          </div>
        </>
      ) : null}
      {children ? <div className="btn-row mt">{children}</div> : null}
    </div>
  );
}
