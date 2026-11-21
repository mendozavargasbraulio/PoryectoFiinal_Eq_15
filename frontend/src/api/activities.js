import client from './client.js';

/**
 * Servicio del banco de actividades (ADMIN/TEACHER). El `content` cuadra con el
 * `type`: MULTIPLE_CHOICE {prompt, options (≥3), answer}; FILL_BLANK {prompt,
 * accepted}. Paginación y filtros siempre en servidor.
 */

export function listActivities({
  page = 0,
  size = 10,
  sort = 'title,asc',
  level,
  skill,
  type,
  categoryId,
  text,
} = {}) {
  return client.get('/content/activities', {
    params: {
      page,
      size,
      sort,
      ...(level ? { level } : {}),
      ...(skill ? { skill } : {}),
      ...(type ? { type } : {}),
      ...(categoryId ? { categoryId } : {}),
      ...(text ? { text } : {}),
    },
  });
}

export function getActivity(id) {
  return client.get(`/content/activities/${id}`);
}

export function createActivity(payload) {
  return client.post('/content/activities', payload);
}

export function updateActivity(id, payload) {
  return client.put(`/content/activities/${id}`, payload);
}

export function removeActivity(id) {
  return client.delete(`/content/activities/${id}`);
}

/** Objetivos de contenido para el select del editor. */
export function listContentObjectives({ page = 0, size = 50 } = {}) {
  return client.get('/content/objectives', { params: { page, size } });
}
