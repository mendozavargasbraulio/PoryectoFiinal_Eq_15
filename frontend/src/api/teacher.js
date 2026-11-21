import client from './client.js';

/**
 * Servicio del profesor (TEACHER): alumnos propios, vinculación por correo y
 * asignaciones. POST /teacher/students responde 404 si el usuario no existe y
 * 422 si no es STUDENT o ya tiene profesor. Paginación y filtros en servidor.
 */

export function listStudents({ page = 0, size = 10, sort = 'name,asc', text } = {}) {
  return client.get('/teacher/students', {
    params: { page, size, sort, ...(text ? { text } : {}) },
  });
}

export function listStudentsOverview({ page = 0, size = 5, sort = 'name,asc', text } = {}) {
  return client.get('/teacher/students/overview', {
    params: { page, size, sort, ...(text ? { text } : {}) },
  });
}

export function linkStudent({ email }) {
  return client.post('/teacher/students', { email });
}

export function listAssignments({ page = 0, size = 10, sort = 'assignedAt,desc', status } = {}) {
  return client.get('/teacher/assignments', {
    params: { page, size, sort, ...(status ? { status } : {}) },
  });
}

export function createAssignment({ studentId, activityId }) {
  return client.post('/teacher/assignments', { studentId, activityId });
}

export function getStudentProgress(id) {
  return client.get(`/teacher/students/${id}/progress`);
}
