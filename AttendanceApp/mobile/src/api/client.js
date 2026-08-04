import { API_BASE_URL } from '../config';

class ApiError extends Error {
  constructor(message, status, body) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

export async function apiRequest(path, { method = 'GET', token, body } = {}) {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });

  const isJson = res.headers.get('content-type')?.includes('application/json');
  const data = isJson ? await res.json() : null;

  if (!res.ok) {
    throw new ApiError(data?.error || `Request failed (${res.status})`, res.status, data);
  }

  return data;
}

export function login(employeeId, password) {
  return apiRequest('/api/auth/login', { method: 'POST', body: { employeeId, password } });
}

export function getStatus(token) {
  return apiRequest('/api/attendance/status', { token });
}

export function punch(token, { lat, lng, accuracyMeters }) {
  return apiRequest('/api/attendance/punch', {
    method: 'POST',
    token,
    body: { lat, lng, accuracyMeters },
  });
}

export function getHistory(token, limit = 50) {
  return apiRequest(`/api/attendance/history?limit=${limit}`, { token });
}

export { ApiError };
