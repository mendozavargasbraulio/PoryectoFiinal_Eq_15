import { useState } from 'react';
import { ErrorState, PageHeader, Spinner } from '../../components/ui.jsx';
import ConfirmModal from '../../components/ConfirmModal.jsx';
import Input from '../../components/Input.jsx';
import { useToast } from '../../components/Toast.jsx';
import { useRemote, REMOTE_STATUS } from '../../api/useRemote.js';
import {
  listCategories,
  createCategory,
  updateCategory,
  removeCategory,
} from '../../api/categories.js';
import { extractApiError } from '../../api/client.js';
import { required } from '../../lib/validators.js';

const PAGE_SIZE = 5;

/** CRUD de categorías (ADMIN): GET/POST/PUT/DELETE /admin/categories con
 *  paginación y búsqueda server-side, y conteo real de actividades. */
export default function AdminCategories() {
  const { showToast } = useToast();

  const [nameFilter, setNameFilter] = useState('');
  const [appliedFilter, setAppliedFilter] = useState('');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState(null); // null | {} (crear) | categoría (editar)
  const [toDelete, setToDelete] = useState(null);
  const [formBusy, setFormBusy] = useState(false);

  const { status, data, error, reload } = useRemote({
    fetcher: () =>
      listCategories({ page, size: PAGE_SIZE, sort: 'name,asc', text: appliedFilter || undefined }),
    deps: [page, appliedFilter],
  });

  const content = data?.content || [];
  const totalPages = Math.max(1, data?.totalPages || 1);
  const currentPage = data?.page ?? page;

  function applyFilter(e) {
    e.preventDefault();
    setAppliedFilter(nameFilter.trim());
    setPage(0);
  }

  async function saveCategory(payload) {
    const isNew = !editing?.id;
    setFormBusy(true);
    try {
      if (isNew) {
        await createCategory(payload);
      } else {
        await updateCategory(editing.id, payload);
      }
      showToast(isNew ? 'Categoría creada.' : 'Categoría actualizada.');
      setEditing(null);
      reload();
    } catch (err) {
      return extractApiError(err);
    } finally {
      setFormBusy(false);
    }
    return null;
  }

  async function confirmDelete() {
    setFormBusy(true);
    try {
      await removeCategory(toDelete.id);
      showToast('Categoría eliminada.');
      setToDelete(null);
      if (content.length === 1 && currentPage > 0) {
        setPage((p) => Math.max(0, p - 1));
      } else {
        reload();
      }
    } catch (err) {
      showToast(extractApiError(err).detail || 'No se pudo eliminar la categoría.');
    } finally {
      setFormBusy(false);
    }
  }

  return (
    <>
      <PageHeader
        eyebrow="PORTAL / ADMINISTRADOR"
        title="Categorías de aprendizaje"
        subtitle="Clasifica el contenido y controla la organización del banco de actividades."
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
              placeholder="Buscar por nombre"
              aria-label="Buscar por nombre"
              value={nameFilter}
              onChange={(e) => setNameFilter(e.target.value)}
            />
            <button className="btn alt small" type="submit">
              Filtrar
            </button>
            <button className="btn coffee-outline small" type="button" onClick={() => setEditing({})}>
              Crear categoría
            </button>
          </form>

          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Categoría</th>
                  <th>Descripción</th>
                  <th>Actividades</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {content.length ? (
                  content.map((c) => (
                    <tr key={c.id}>
                      <td>{c.name}</td>
                      <td>{c.description || '—'}</td>
                      <td>{c.activitiesCount ?? 0}</td>
                      <td>
                        <div className="btn-row">
                          <button className="btn alt small" type="button" onClick={() => setEditing(c)}>
                            Editar
                          </button>
                          <button className="btn rust small" type="button" onClick={() => setToDelete(c)}>
                            Eliminar
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={4}>
                      <span className="muted">Sin categorías para este filtro.</span>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="pagination">
            <span>
              Página {currentPage + 1} de {totalPages}
              {data?.totalElements != null ? ` · ${data.totalElements} categorías` : ''}
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
        <CategoryModal
          key={editing.id ?? 'new'}
          category={editing}
          busy={formBusy}
          onClose={() => setEditing(null)}
          onSave={saveCategory}
        />
      ) : null}

      <ConfirmModal
        open={Boolean(toDelete)}
        title="Eliminar categoría"
        description={
          toDelete ? `Confirma la eliminación de "${toDelete.name}".` : ''
        }
        confirmLabel="Eliminar"
        busy={formBusy}
        onConfirm={confirmDelete}
        onCancel={() => setToDelete(null)}
      />
    </>
  );
}

/** Alta/edición en modal con errores bajo cada input en tiempo real. */
function CategoryModal({ category, busy, onClose, onSave }) {
  const isNew = !category?.id;
  const [values, setValues] = useState({
    name: category?.name || '',
    description: category?.description || '',
  });
  const [errors, setErrors] = useState({ name: '', description: '' });
  const [touched, setTouched] = useState({});
  const [submitError, setSubmitError] = useState('');

  const rules = {
    name: (v) =>
      required(v, 'El nombre') || (String(v || '').trim().length > 120 ? 'Máximo 120 caracteres.' : ''),
    description: (v) => (String(v || '').length > 200 ? 'Máximo 200 caracteres.' : ''),
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setValues((v) => ({ ...v, [name]: value }));
    setSubmitError('');
    if (touched[name]) setErrors((errs) => ({ ...errs, [name]: rules[name](value) }));
  };

  const handleBlur = (e) => {
    const { name, value } = e.target;
    setTouched((t) => ({ ...t, [name]: true }));
    setErrors((errs) => ({ ...errs, [name]: rules[name](value) }));
  };

  async function handleSubmit(e) {
    e.preventDefault();
    const nextErrors = {};
    for (const key of Object.keys(rules)) nextErrors[key] = rules[key](values[key]);
    setErrors(nextErrors);
    setTouched({ name: true, description: true });
    if (Object.values(nextErrors).some(Boolean)) return;
    const apiError = await onSave({
      name: values.name.trim(),
      description: values.description.trim() || null,
    });
    if (apiError) {
      const fieldErrors = {};
      (apiError.fieldErrors || []).forEach((f) => {
        if (f.field in values) fieldErrors[f.field] = f.message;
      });
      setErrors((prev) => ({ ...prev, ...fieldErrors }));
      setSubmitError(Object.keys(fieldErrors).length ? '' : apiError.detail || 'No se pudo guardar la categoría.');
    }
  }

  return (
    <div
      className="overlay"
      onClick={(e) => {
        if (e.target === e.currentTarget && !busy) onClose();
      }}
    >
      <section className="modal" role="dialog" aria-modal="true" aria-labelledby="cat-modal-title">
        <p className="eyebrow">ENGLISH LAB / {isNew ? 'NUEVA' : 'EDITAR'} CATEGORÍA</p>
        <h2 id="cat-modal-title">{isNew ? 'Crear categoría' : 'Editar categoría'}</h2>
        <form onSubmit={handleSubmit} noValidate>
          <Input
            label="Nombre"
            name="name"
            placeholder="Ej. Grammar"
            value={values.name}
            onChange={handleChange}
            onBlur={handleBlur}
            error={touched.name ? errors.name : ''}
          />
          <Input
            label="Descripción"
            name="description"
            placeholder="Ej. Reglas gramaticales"
            value={values.description}
            onChange={handleChange}
            onBlur={handleBlur}
            error={touched.description ? errors.description : ''}
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
