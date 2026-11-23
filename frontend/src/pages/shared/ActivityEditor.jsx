import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { Badge, ErrorState, PageHeader, Spinner } from '../../components/ui.jsx';
import Input from '../../components/Input.jsx';
import { useToast } from '../../components/Toast.jsx';
import { useRemote, REMOTE_STATUS } from '../../api/useRemote.js';
import { getActivity, listContentObjectives, createActivity, updateActivity } from '../../api/activities.js';
import { listCategories } from '../../api/categories.js';
import { extractApiError } from '../../api/client.js';
import { required } from '../../lib/validators.js';
import { useAuth } from '../../auth/AuthContext.jsx';
import { LEVELS, SKILLS, ACTIVITY_TYPE_CODES, activityTypeLabel } from '../../lib/constants.js';

const MIN_MC_OPTIONS = 3;

function optionIdForIndex(idx) {
  return idx < 26 ? String.fromCharCode(97 + idx) : `o${idx + 1}`;
}

function optionLetter(idx) {
  return idx < 26 ? String.fromCharCode(65 + idx) : String(idx + 1);
}

function extractOptionText(opt) {
  if (opt && typeof opt === 'object') return String(opt.text ?? '').trim();
  return String(opt ?? '').trim();
}

function activityToValues(a) {
  const content = a?.content || {};
  const rawOptions = Array.isArray(content.options) ? content.options : [];

  let options = ['', '', ''];
  let correctIndex = -1;

  if (a?.type === 'MULTIPLE_CHOICE' && rawOptions.length) {
    options = rawOptions.map(extractOptionText);
    while (options.length < MIN_MC_OPTIONS) options.push('');
    const answerKey = content.answer != null ? String(content.answer) : '';
    const idxById = rawOptions.findIndex(
      (o) => o && typeof o === 'object' && String(o.id) === answerKey
    );
    if (idxById >= 0) {
      correctIndex = idxById;
    } else if (answerKey) {
      correctIndex = options.findIndex((t) => t === answerKey.trim());
    }
  }

  let accepted = [''];
  if (a?.type === 'FILL_BLANK') {
    if (Array.isArray(content.accepted) && content.accepted.length) {
      accepted = content.accepted.map((x) => String(x ?? ''));
    } else if (typeof content.accepted === 'string' && content.accepted.trim()) {
      accepted = [content.accepted.trim()];
    }
  }

  return {
    title: a?.title || '',
    level: a?.level || '',
    categoryId: a?.categoryId != null ? String(a.categoryId) : '',
    skill: a?.skill || '',
    objectiveId: a?.objectiveId != null ? String(a.objectiveId) : '',
    type: a?.type || '',
    statement: content.prompt || '',
    options,
    correctIndex,
    accepted,
    explanation: content.explanation || '',
  };
}

/** Pantalla completa (no modal) conectada a GET/POST/PUT /content/activities.
 *  Constructor estilo Forms: en MULTIPLE_CHOICE se agregan opciones una a una y
 *  se marca la correcta con radio; en FILL_BLANK se agregan respuestas aceptadas. */
export default function ActivityEditor() {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { showToast } = useToast();
  const { role } = useAuth();

  const [initial, setInitial] = useState(null);
  const [loading, setLoading] = useState(Boolean(id));
  const [loadError, setLoadError] = useState(null);

  const [values, setValues] = useState(() => activityToValues(null));
  const [errors, setErrors] = useState({});
  const [touched, setTouched] = useState({});
  const [submitError, setSubmitError] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!id) {
      setInitial(null);
      setLoadError(null);
      setValues(activityToValues(null));
      setErrors({});
      setTouched({});
      setSubmitError('');
      setLoading(false);
      return;
    }
    if (location.state?.activity && String(location.state.activity.id) === String(id)) {
      setInitial(location.state.activity);
      setLoadError(null);
      setErrors({});
      setTouched({});
      setSubmitError('');
      setLoading(false);
      return;
    }
    let alive = true;
    setLoading(true);
    setLoadError(null);
    getActivity(id)
      .then((res) => {
        if (alive) {
          setInitial(res.data || null);
          setLoadError(res.data ? null : { detail: 'No se encontró la actividad solicitada.' });
          setLoading(false);
        }
      })
      .catch((err) => {
        if (alive) {
          setLoadError({ detail: extractApiError(err).detail || 'No se pudo cargar la actividad.' });
          setLoading(false);
        }
      });
    return () => {
      alive = false;
    };
  }, [id, location.state?.activity]);

  useEffect(() => {
    if (initial !== null) {
      setValues(activityToValues(initial));
      setErrors({});
      setTouched({});
      setSubmitError('');
    }
  }, [initial]);

  const categoriesRemote = useRemote({
    fetcher: () => listCategories({ page: 0, size: 100, sort: 'name,asc' }),
  });
  const categories = categoriesRemote.status === REMOTE_STATUS.real
    ? categoriesRemote.data?.content || []
    : [];

  const objectivesRemote = useRemote({
    fetcher: () => listContentObjectives({ page: 0, size: 100 }),
  });
  const objectives = objectivesRemote.status === REMOTE_STATUS.real
    ? objectivesRemote.data?.content || []
    : [];

  function validate(v) {
    const e = {};
    e.title = required(v.title, 'El título');
    e.level = required(v.level, 'El nivel');
    e.skill = required(v.skill, 'La habilidad');
    e.objectiveId = required(v.objectiveId, 'El objetivo');
    e.type = required(v.type, 'El tipo de ejercicio');
    e.statement = required(v.statement, 'El enunciado');

    if (v.type === 'MULTIPLE_CHOICE') {
      const trimmed = v.options.map((o) => String(o || '').trim());
      const nonEmpty = trimmed.filter(Boolean);
      if (nonEmpty.length < MIN_MC_OPTIONS) {
        e.options = `Completa al menos ${MIN_MC_OPTIONS} opciones de respuesta.`;
      } else if (trimmed.some((t) => !t)) {
        e.options = 'Completa o elimina las opciones vacías.';
      } else if (new Set(nonEmpty.map((t) => t.toLowerCase())).size !== nonEmpty.length) {
        e.options = 'Las opciones no pueden repetirse.';
      } else {
        e.options = '';
      }
      if (v.correctIndex < 0 || v.correctIndex >= trimmed.length || !trimmed[v.correctIndex]) {
        e.answer = 'Marca el círculo de la opción correcta.';
      } else {
        e.answer = '';
      }
    } else if (v.type === 'FILL_BLANK') {
      e.options = '';
      const cleaned = v.accepted.map((a) => String(a || '').trim()).filter(Boolean);
      if (!cleaned.length) {
        e.answer = 'Escribe al menos una respuesta aceptada.';
      } else if (new Set(cleaned.map((t) => t.toLowerCase())).size !== cleaned.length) {
        e.answer = 'Las respuestas aceptadas no pueden repetirse.';
      } else {
        e.answer = '';
      }
    } else {
      e.options = '';
      e.answer = '';
    }
    return e;
  }

  function updateValues(next, fieldsToValidate = []) {
    setValues(next);
    setSubmitError('');
    const nextErrors = validate(next);
    setErrors((errs) => {
      const updated = { ...errs };
      fieldsToValidate.forEach((f) => {
        if (touched[f]) updated[f] = nextErrors[f];
      });
      return updated;
    });
  }

  const handleChange = (e) => {
    const { name, value } = e.target;
    const next = { ...values, [name]: value };
    setValues(next);
    setSubmitError('');
    const nextErrors = validate(next);
    setErrors((errs) => {
      const updated = { ...errs };
      if (touched[name]) updated[name] = nextErrors[name];
      if (name === 'type') {
        updated.options = touched.options ? nextErrors.options : '';
        updated.answer = touched.answer ? nextErrors.answer : '';
      }
      return updated;
    });
  };

  const handleBlur = (e) => {
    const { name, value } = e.target;
    const next = { ...values, [name]: value };
    setTouched((t) => ({ ...t, [name]: true }));
    const nextErrors = validate(next);
    setErrors((errs) => ({ ...errs, [name]: nextErrors[name] }));
  };

  function handleOptionChange(idx, text) {
    const nextOptions = values.options.map((o, i) => (i === idx ? text : o));
    updateValues({ ...values, options: nextOptions }, ['options', 'answer']);
  }

  function handleSelectCorrect(idx) {
    const next = { ...values, correctIndex: idx };
    setValues(next);
    setSubmitError('');
    setTouched((t) => ({ ...t, answer: true }));
    const nextErrors = validate(next);
    setErrors((errs) => ({ ...errs, answer: nextErrors.answer }));
  }

  function addOption() {
    updateValues({ ...values, options: [...values.options, ''] }, ['options']);
  }

  function removeOption(idx) {
    if (values.options.length <= MIN_MC_OPTIONS) return;
    const nextOptions = values.options.filter((_, i) => i !== idx);
    let nextCorrect = values.correctIndex;
    if (idx === values.correctIndex) nextCorrect = -1;
    else if (idx < values.correctIndex) nextCorrect = values.correctIndex - 1;
    updateValues({ ...values, options: nextOptions, correctIndex: nextCorrect }, ['options', 'answer']);
  }

  function handleAcceptedChange(idx, text) {
    const nextAccepted = values.accepted.map((a, i) => (i === idx ? text : a));
    updateValues({ ...values, accepted: nextAccepted }, ['answer']);
  }

  function addAccepted() {
    updateValues({ ...values, accepted: [...values.accepted, ''] }, ['answer']);
  }

  function removeAccepted(idx) {
    if (values.accepted.length <= 1) return;
    const nextAccepted = values.accepted.filter((_, i) => i !== idx);
    updateValues({ ...values, accepted: nextAccepted }, ['answer']);
  }

  function buildContent(v) {
    const prompt = String(v.statement).trim();
    const explanation = String(v.explanation).trim();
    if (v.type === 'MULTIPLE_CHOICE') {
      const options = v.options.map((text, idx) => ({
        id: optionIdForIndex(idx),
        text: String(text).trim(),
      }));
      const answerId = optionIdForIndex(v.correctIndex);
      return {
        prompt,
        options,
        answer: answerId,
        ...(explanation ? { explanation } : {}),
      };
    }
    return {
      prompt,
      accepted: v.accepted.map((a) => String(a).trim()).filter(Boolean),
      ...(explanation ? { explanation } : {}),
    };
  }

  async function handleSubmit(e) {
    e.preventDefault();
    const nextErrors = validate(values);
    setErrors(nextErrors);
    setTouched({
      title: true,
      level: true,
      skill: true,
      objectiveId: true,
      type: true,
      statement: true,
      options: true,
      answer: true,
    });
    if (Object.values(nextErrors).some(Boolean)) return;

    const payload = {
      title: values.title.trim(),
      level: values.level,
      skill: values.skill,
      objectiveId: Number(values.objectiveId),
      type: values.type,
      categoryId: values.categoryId ? Number(values.categoryId) : null,
      content: buildContent(values),
    };

    setBusy(true);
    setSubmitError('');
    try {
      if (id) {
        await updateActivity(id, payload);
      } else {
        await createActivity(payload);
      }
      showToast(id ? 'Actividad actualizada.' : 'Actividad creada.');
      navigate('/actividades');
    } catch (err) {
      const apiError = extractApiError(err);
      const fieldErrors = {};
      (apiError.fieldErrors || []).forEach((f) => {
        if (f.field in values) fieldErrors[f.field] = f.message;
      });
      setErrors((prev) => ({ ...prev, ...fieldErrors }));
      setSubmitError(Object.keys(fieldErrors).length ? '' : apiError.detail || 'No se pudo guardar la actividad.');
    } finally {
      setBusy(false);
    }
  }

  if (loading) return <Spinner />;
  if (loadError && !initial) return <ErrorState message={loadError.detail} onRetry={() => navigate('/actividades')} />;

  const selectField = (name, label, options, value) => (
    <div>
      <label className="form-label" htmlFor={`editor-${name}`}>{label}</label>
      <select
        id={`editor-${name}`}
        className={`input${touched[name] && errors[name] ? ' invalid' : ''}`}
        name={name}
        value={value}
        onChange={handleChange}
        onBlur={handleBlur}
      >
        <option value="">Selecciona…</option>
        {options.map((o) => (
          <option key={o.value ?? o} value={o.value ?? o}>
            {o.label ?? o}
          </option>
        ))}
      </select>
      {touched[name] && errors[name] ? <p className="error" role="alert">{errors[name]}</p> : null}
    </div>
  );

  return (
    <>
      <PageHeader
        eyebrow={role === 'ADMIN' ? 'PORTAL / ADMINISTRADOR' : 'PORTAL / PROFESOR'}
        title={id ? 'Editar actividad' : 'Crear actividad'}
      />
      {objectivesRemote.status === REMOTE_STATUS.error ? (
        <ErrorState
          message={objectivesRemote.error?.detail || 'No se pudieron cargar los objetivos.'}
          onRetry={objectivesRemote.reload}
        />
      ) : null}
      <form className="card light" style={{ maxWidth: 900 }} onSubmit={handleSubmit} noValidate>
        <div className="form-2">
          <Input
            label="Título"
            name="title"
            placeholder="Ej. Present Simple A1"
            value={values.title}
            onChange={handleChange}
            onBlur={handleBlur}
            error={touched.title ? errors.title : ''}
          />
          {selectField('level', 'Nivel', LEVELS, values.level)}
          {selectField('skill', 'Habilidad', SKILLS, values.skill)}
          {selectField(
            'type',
            'Tipo de ejercicio',
            ACTIVITY_TYPE_CODES.map((t) => ({ value: t, label: activityTypeLabel(t) })),
            values.type
          )}
          {selectField(
            'objectiveId',
            'Objetivo',
            objectives.map((o) => ({ value: o.id, label: `${o.code} — ${o.name} (${o.cefrLevel})` })),
            values.objectiveId
          )}
          {selectField(
            'categoryId',
            'Categoría (opcional)',
            categories.map((c) => ({ value: c.id, label: c.name })),
            values.categoryId
          )}
        </div>

        <div>
          <label className="form-label" htmlFor="statement">Enunciado</label>
          <textarea
            id="statement"
            className={`input textarea${touched.statement && errors.statement ? ' invalid' : ''}`}
            name="statement"
            placeholder="Escribe la instrucción que verá el estudiante..."
            value={values.statement}
            onChange={handleChange}
            onBlur={handleBlur}
          />
          {touched.statement && errors.statement ? <p className="error" role="alert">{errors.statement}</p> : null}
        </div>

        {values.type === 'MULTIPLE_CHOICE' ? (
          <div>
            <span className="form-label">Opciones de respuesta (marca la correcta)</span>
            <p className="help" style={{ marginTop: 0 }}>
              Agrega al menos {MIN_MC_OPTIONS} opciones y selecciona el círculo de la respuesta correcta.
            </p>
            <div className="option-builder">
              {values.options.map((opt, idx) => {
                const isCorrect = values.correctIndex === idx;
                return (
                  <div className={`option-row${isCorrect ? ' is-correct' : ''}`} key={idx}>
                    <input
                      type="radio"
                      className="option-radio"
                      name="correctOption"
                      aria-label={`Marcar opción ${optionLetter(idx)} como correcta`}
                      checked={isCorrect}
                      onChange={() => handleSelectCorrect(idx)}
                    />
                    <span className="option-key" aria-hidden="true">{optionLetter(idx)}</span>
                    <input
                      className="input"
                      aria-label={`Texto de la opción ${optionLetter(idx)}`}
                      placeholder={`Opción ${idx + 1}`}
                      value={opt}
                      onChange={(e) => handleOptionChange(idx, e.target.value)}
                      onBlur={() => {
                        setTouched((t) => ({ ...t, options: true }));
                        setErrors((errs) => ({ ...errs, options: validate(values).options }));
                      }}
                    />
                    {isCorrect ? <Badge color="green">Correcta</Badge> : null}
                    {values.options.length > MIN_MC_OPTIONS ? (
                      <button
                        className="btn alt small option-remove"
                        type="button"
                        title={`Quitar opción ${optionLetter(idx)}`}
                        aria-label={`Quitar opción ${optionLetter(idx)}`}
                        onClick={() => removeOption(idx)}
                      >
                        ×
                      </button>
                    ) : null}
                  </div>
                );
              })}
            </div>
            <div style={{ marginTop: 10 }}>
              <button className="btn alt small" type="button" onClick={addOption}>
                + Agregar opción
              </button>
            </div>
            {touched.options && errors.options ? <p className="error" role="alert">{errors.options}</p> : null}
            {touched.answer && errors.answer ? <p className="error" role="alert">{errors.answer}</p> : null}
          </div>
        ) : values.type === 'FILL_BLANK' ? (
          <div>
            <span className="form-label">Respuestas aceptadas</span>
            <p className="help" style={{ marginTop: 0 }}>
              Escribe la palabra o frase válida. Puedes agregar alternativas si hay más de una forma correcta.
            </p>
            <div className="option-builder">
              {values.accepted.map((ans, idx) => (
                <div className="option-row" key={idx}>
                  <span className="option-key" aria-hidden="true">{idx + 1}</span>
                  <input
                    className="input"
                    aria-label={`Respuesta aceptada ${idx + 1}`}
                    placeholder={idx === 0 ? 'Ej. are' : 'Otra respuesta válida'}
                    value={ans}
                    onChange={(e) => handleAcceptedChange(idx, e.target.value)}
                    onBlur={() => {
                      setTouched((t) => ({ ...t, answer: true }));
                      setErrors((errs) => ({ ...errs, answer: validate(values).answer }));
                    }}
                  />
                  {values.accepted.length > 1 ? (
                    <button
                      className="btn alt small option-remove"
                      type="button"
                      title={`Quitar respuesta ${idx + 1}`}
                      aria-label={`Quitar respuesta ${idx + 1}`}
                      onClick={() => removeAccepted(idx)}
                    >
                      ×
                    </button>
                  ) : null}
                </div>
              ))}
            </div>
            <div style={{ marginTop: 10 }}>
              <button className="btn alt small" type="button" onClick={addAccepted}>
                + Agregar respuesta aceptada
              </button>
            </div>
            {touched.answer && errors.answer ? <p className="error" role="alert">{errors.answer}</p> : null}
          </div>
        ) : (
          <p className="help" style={{ marginTop: 16 }}>
            Selecciona primero el <b>Tipo de ejercicio</b> para agregar las opciones o respuestas.
          </p>
        )}

        <div>
          <label className="form-label" htmlFor="explanation">Explicación pedagógica</label>
          <textarea
            id="explanation"
            className="input textarea"
            name="explanation"
            placeholder="Explica por qué la respuesta es correcta."
            value={values.explanation}
            onChange={handleChange}
          />
        </div>
        {submitError ? <p className="form-status" role="alert">{submitError}</p> : null}
        <div className="btn-row mt">
          <button className="btn" type="submit" disabled={busy}>
            {busy ? 'Guardando…' : 'Guardar actividad'}
          </button>
          <button className="btn alt" type="button" onClick={() => navigate('/actividades')}>
            Cancelar
          </button>
        </div>
      </form>
    </>
  );
}
