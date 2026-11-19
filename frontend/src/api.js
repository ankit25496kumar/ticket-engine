const BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

async function request(path, options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options,
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const msg = data?.error?.message || data?.error?.code || res.statusText;
    throw new Error(msg);
  }
  return data;
}

export const api = {
  dashboardSummary: () => request('/api/v1/dashboard/summary'),
  listTickets: (params) => {
    const q = new URLSearchParams(params).toString();
    return request(`/api/v1/tickets${q ? `?${q}` : ''}`);
  },
  getTicket: (id) => request(`/api/v1/tickets/${id}`),
  createTicket: (body) =>
    request('/api/v1/tickets', { method: 'POST', body: JSON.stringify(body) }),
  updateTicket: (id, body) =>
    request(`/api/v1/tickets/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
  changeStatus: (id, status) =>
    request(`/api/v1/tickets/${id}/status`, {
      method: 'PATCH',
      body: JSON.stringify({ status }),
    }),
  listComments: (id) => request(`/api/v1/tickets/${id}/comments`),
  addComment: (id, body) =>
    request(`/api/v1/tickets/${id}/comments`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  ragQuery: (query) =>
    request('/api/v1/rag/query', { method: 'POST', body: JSON.stringify({ query }) }),
};