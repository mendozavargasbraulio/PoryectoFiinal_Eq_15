import axios from 'axios';

/**
 * Cliente HTTP central: baseURL por VITE_API_URL (o el proxy de Vite).
 * El interceptor añade el JWT; un 401 fuera de /auth/* limpia la sesión y
 * redirige a /login.
 */

export const API_BASE_URL = import.meta.env.VITE_API_URL || '/api/v1';

export const TOKEN_KEY = 'englishlab.auth';

/** La pone el interceptor 401 para que Login avise de la sesión expirada una
 *  sola vez (Login la consume y la borra). */
export const SESSION_EXPIRED_KEY = 'englishlab.sessionExpired';

export function readStoredAuth() {
  try {
    const raw = localStorage.getItem(TOKEN_KEY);
    if (!raw) return { token: null, user: null };
    const parsed = JSON.parse(raw);
    if (!parsed?.token) return { token: null, user: null };
    return { token: parsed.token, user: parsed.user || null };
  } catch {
    return { token: null, user: null };
  }
}

export function writeStoredAuth(auth) {
  if (!auth?.token) {
    localStorage.removeItem(TOKEN_KEY);
  } else {
    localStorage.setItem(TOKEN_KEY, JSON.stringify(auth));
  }
}

export function clearStoredAuth() {
  localStorage.removeItem(TOKEN_KEY);
}

const client = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
});

client.interceptors.request.use((config) => {
  const { token } = readStoredAuth();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

client.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status;
    const url = error?.config?.url || '';
    const isAuthCall = url.includes('/auth/');
    if (status === 401 && !isAuthCall) {
      clearStoredAuth();
      if (!window.location.pathname.startsWith('/login')) {
        try {
          window.sessionStorage.setItem(SESSION_EXPIRED_KEY, '1');
        } catch {
          /* sessionStorage no disponible (modo privado): seguimos sin aviso */
        }
        window.location.assign('/login');
      }
    }
    return Promise.reject(error);
  }
);

/** Normaliza el ProblemDetail del backend a {title, detail, status, fieldErrors}. */
export function extractApiError(error) {
  const data = error?.response?.data;
  if (!error.response) {
    return {
      status: 0,
      title: 'Sin conexión',
      detail: 'No se pudo contactar al servidor. Revisa tu conexión e intenta de nuevo.',
      fieldErrors: [],
    };
  }
  const fieldErrors = Array.isArray(data?.fieldErrors) ? data.fieldErrors : [];
  return {
    status: data?.status ?? error.response.status,
    title: data?.title || 'Error',
    detail: data?.detail || 'Ocurrió un problema. Intenta de nuevo.',
    fieldErrors: fieldErrors.map((f) => ({ field: f.field, message: f.message })),
  };
}

export default client;
