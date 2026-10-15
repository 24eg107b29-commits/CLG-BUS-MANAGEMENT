document.addEventListener('DOMContentLoaded', async () => {
  const loginForm = document.getElementById('loginForm');
  if (loginForm) {
    loginForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      const username = document.getElementById('username').value.trim();
      const password = document.getElementById('password').value;

      try {
        const data = await apiFetch('/api/auth/login', {
          method: 'POST',
          body: JSON.stringify({ username, password })
        });
        if (data.user) {
          setCurrentUser(data.user);
          window.location.href = 'dashboard.html';
        }
      } catch (error) {
        showToast(error.message || 'Login failed', 'error');
      }
    });
    return;
  }

  const logoutBtn = document.getElementById('logoutBtn');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', async () => {
      try {
        await apiFetch('/api/auth/logout', { method: 'POST' });
        setCurrentUser(null);
        window.location.href = 'login.html';
      } catch (error) {
        showToast('Logout failed', 'error');
      }
    });
  }

  setUserBadge();
  redirectIfNotLoggedIn();
  const user = getCurrentUser();
  if (!user) {
    try {
      await hydrateUserSession();
    } catch (error) {
      console.warn(error);
    }
  }
  setUserBadge();
});

async function checkAuth() {
  const user = getCurrentUser();
  if (!user) {
    try {
      await hydrateUserSession();
    } catch (error) {
      window.location.href = 'login.html';
    }
  }
  setUserBadge();
}
