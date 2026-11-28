import client from './client.js';

/**
 * Servicio del estudiante: /me/assignments, /me/activities/{id}, /attempts,
 * /me/attempts/{id}, /me/assignments/{id}/result, /me/objectives y
 * /me/objectives/{id}/practice-session.
 * POST /attempts devuelve {attemptId, correct, score, solution, explanation, mastery};
 * en MULTIPLE_CHOICE `solution` es el ID de la opción correcta. `mode` es "NORMAL"
 * (por defecto) o "CHALLENGE" (acierto +1.0, fallo −0.5, sin responder 0.0).
 */

export function listAssignments({ page = 0, size = 10, sort = 'assignedAt,desc' } = {}) {
  return client.get('/me/assignments', {
    params: { page, size, sort },
  });
}

export function getMyActivity(id) {
  return client.get(`/me/activities/${id}`);
}

export function submitAttempt({ activityId, answer, assignmentId, mode }) {
  return client.post('/attempts', {
    activityId,
    answer,
    // En práctica libre no se envía assignmentId.
    ...(assignmentId != null ? { assignmentId } : {}),
    // Omitir `mode` equivale a NORMAL; "CHALLENGE" activa +1.0 / −0.5 / 0.0.
    ...(mode != null ? { mode } : {}),
  });
}

export function getMyAttempt(id) {
  return client.get(`/me/attempts/${id}`);
}

export function getAssignmentResult(assignmentId) {
  return client.get(`/me/assignments/${assignmentId}/result`);
}

export function listObjectives() {
  return client.get('/me/objectives');
}

/** Sesión de práctica: el servidor elige hasta `size` actividades y devuelve
 *  {activityIds}. 422 si el objetivo está bloqueado. */
export function getPracticeSession(objectiveId, { size = 10 } = {}) {
  return client.get(`/me/objectives/${objectiveId}/practice-session`, { params: { size } });
}

export function getMyClass() {
  return client.get('/me/class');
}

export function joinTeacher(teacherId) {
  return client.post(`/me/class/${teacherId}`);
}

export function leaveClass() {
  return client.delete('/me/class');
}
