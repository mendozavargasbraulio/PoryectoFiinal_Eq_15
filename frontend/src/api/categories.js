import client from './client.js';

/**
 * Servicio de categorías (ADMIN). El filtro `text` viaja al servidor; nunca se
 * filtra ni se pagina en memoria.
 */

export function listCategories({ page = 0, size = 10, sort = 'name,asc', text } = {}) {
  return client.get('/admin/categories', {
    params: { page, size, sort, ...(text ? { text } : {}) },
  });
}

export function createCategory({ name, description }) {
  return client.post('/admin/categories', { name, description });
}

export function updateCategory(id, { name, description }) {
  return client.put(`/admin/categories/${id}`, { name, description });
}

export function removeCategory(id) {
  return client.delete(`/admin/categories/${id}`);
}
