const API_BASE_URL = 'http://localhost:8080';

async function apiFetch(path, options = {}) {
  const request = {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    ...options,
  };

  request.headers = {
    ...(request.headers || {}),
  };

  const response = await fetch(`${API_BASE_URL}${path}`, request);
  let payload = {};
  try {
    payload = await response.json();
  } catch (error) {
    payload = { message: 'Unable to parse response.' };
  }

  if (!response.ok) {
    throw new Error(payload.message || 'Request failed');
  }

  return payload;
}

function showToast(message, type = 'success') {
  const container = document.createElement('div');
  container.className = `toast ${type}`;
  container.textContent = message;
  document.body.appendChild(container);
  setTimeout(() => container.remove(), 3200);
}

function getStatusBadge(status) {
  const map = {
    active: 'badge-success',
    approved: 'badge-success',
    paid: 'badge-success',
    pending: 'badge-warning',
    overdue: 'badge-danger',
    rejected: 'badge-danger',
    maintenance: 'badge-warning',
    unavailable: 'badge-danger',
    on_leave: 'badge-info',
    inactive: 'badge-danger',
  };
  const label = status || 'unknown';
  return `<span class="badge ${map[label] || 'badge-info'}">${label}</span>`;
}

function formatDate(value) {
  if (!value) return '—';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleDateString();
}

function escapeHtml(value) {
  return String(value || '').replace(/[&<>"']/g, (char) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  }[char]));
}

function getCurrentUser() {
  return JSON.parse(sessionStorage.getItem('cbmUser') || 'null');
}

function setCurrentUser(user) {
  if (user) {
    sessionStorage.setItem('cbmUser', JSON.stringify(user));
  } else {
    sessionStorage.removeItem('cbmUser');
  }
}

function setUserBadge() {
  const badge = document.getElementById('userBadge');
  if (!badge) return;
  const user = getCurrentUser();
  badge.textContent = user ? user.full_name || user.username : 'Guest';
}

function redirectIfNotLoggedIn() {
  const currentPath = window.location.pathname.split('/').pop();
  if (currentPath === 'login.html') return;
  const user = getCurrentUser();
  if (!user) {
    window.location.href = 'login.html';
  }
}

async function hydrateUserSession() {
  try {
    const data = await apiFetch('/api/auth/me');
    if (data && data.user) {
      setCurrentUser(data.user);
      setUserBadge();
      return true;
    }
  } catch (error) {
    setCurrentUser(null);
    if (window.location.pathname.endsWith('login.html') === false) {
      window.location.href = 'login.html';
    }
    return false;
  }
  return false;
}
