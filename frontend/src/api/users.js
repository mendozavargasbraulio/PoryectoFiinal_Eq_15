import client from './client.js';

/**
 * Servicio de usuarios (ADMIN). Paginación y filtros siempre en servidor.
 */

export function listUsers({ page = 0, size = 10, sort = 'name,asc', role, text } = {}) {
  return client.get('/admin/users', {
    params: { page, size, sort, ...(role ? { role } : {}), ...(text ? { text } : {}) },
  });
}

export function createUser(payload) {
  return client.post('/admin/users', payload);
}

export function updateUser(id, payload) {
  return client.put(`/admin/users/${id}`, payload);
}

export function removeUser(id) {
  return client.delete(`/admin/users/${id}`);
}
