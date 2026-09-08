/**
 * QueueCut API Client & SSE Stream Manager
 */

const API_BASE = '/api';

async function request(path, options = {}) {
  const url = `${API_BASE}${path}`;
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  };

  const response = await fetch(url, { ...options, headers });
  
  let data;
  try {
    data = await response.json();
  } catch (e) {
    data = null;
  }

  if (!response.ok) {
    const errorMsg = data?.message || `Request failed with status ${response.status}`;
    const err = new Error(errorMsg);
    err.status = response.status;
    err.data = data;
    throw err;
  }

  return data;
}

// --- Public / Student Endpoints ---

export async function fetchQueueStatus() {
  return request('/queue/status');
}

export async function joinQueue(studentName, studentId) {
  return request('/queue/join', {
    method: 'POST',
    body: JSON.stringify({ studentName, studentId }),
  });
}

export async function fetchMyStatus(entryId, studentToken) {
  return request(`/queue/my-status/${entryId}`, {
    headers: {
      'X-Student-Token': studentToken,
    },
  });
}

export async function cancelMyEntry(entryId, studentToken) {
  return request(`/queue/${entryId}/cancel`, {
    method: 'POST',
    headers: {
      'X-Student-Token': studentToken,
    },
  });
}

export async function fetchSettings() {
  return request('/settings');
}

// --- Barber Management Endpoints ---

export async function loginBarber(username, password) {
  return request('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}

export async function openSession(token) {
  return request('/barber/session/open', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function closeSession(token) {
  return request('/barber/session/close', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function getCurrentSession(token) {
  return request('/barber/session/current', {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function callNext(token) {
  return request('/barber/queue/call-next', {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function completeEntry(entryId, token) {
  return request(`/barber/queue/${entryId}/complete`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function skipEntry(entryId, token) {
  return request(`/barber/queue/${entryId}/skip`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function fetchEntries(statusFilter, token) {
  const query = statusFilter ? `?status=${statusFilter}` : '';
  return request(`/barber/queue/entries${query}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
}

export async function updateSettings(avgHaircutMin, token) {
  return request('/settings', {
    method: 'PUT',
    headers: { Authorization: `Bearer ${token}` },
    body: JSON.stringify({ avgHaircutMin: parseInt(avgHaircutMin, 10) }),
  });
}

// --- SSE Real-Time Stream Manager ---

export function connectSse(onEvent, onError) {
  let eventSource = null;
  let reconnectTimer = null;

  function connect() {
    eventSource = new EventSource(`${API_BASE}/queue/stream`);

    eventSource.addEventListener('INIT', (event) => {
      try {
        const data = JSON.parse(event.data);
        onEvent('INIT', data);
      } catch (e) {
        console.error('Failed to parse INIT event:', e);
      }
    });

    eventSource.addEventListener('QUEUE_UPDATED', (event) => {
      try {
        const data = JSON.parse(event.data);
        onEvent('QUEUE_UPDATED', data);
      } catch (e) {
        console.error('Failed to parse QUEUE_UPDATED event:', e);
      }
    });

    eventSource.addEventListener('SESSION_STATUS_CHANGED', (event) => {
      try {
        const data = JSON.parse(event.data);
        onEvent('SESSION_STATUS_CHANGED', data);
      } catch (e) {
        console.error('Failed to parse SESSION_STATUS_CHANGED event:', e);
      }
    });

    eventSource.onerror = (err) => {
      if (onError) onError(err);
      if (eventSource) {
        eventSource.close();
      }
      // Reconnect after 5 seconds
      reconnectTimer = setTimeout(connect, 5000);
    };
  }

  connect();

  return () => {
    if (reconnectTimer) clearTimeout(reconnectTimer);
    if (eventSource) eventSource.close();
  };
}
