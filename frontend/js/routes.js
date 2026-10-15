document.addEventListener('DOMContentLoaded', async () => {
  await checkAuth();
  await loadBusesForRouteSelect();
  await loadRoutes();

  document.getElementById('routeForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const payload = {
      route_name: document.getElementById('route_name').value.trim(),
      start_location: document.getElementById('start_location').value.trim(),
      destination: document.getElementById('destination').value.trim(),
      distance_km: Number(document.getElementById('distance_km').value),
      duration_minutes: Number(document.getElementById('duration_minutes').value),
      assigned_bus_id: document.getElementById('assigned_bus_id').value || null,
      stops: (document.getElementById('stops').value || '').split(',').map((item) => item.trim()).filter(Boolean)
    };
    const routeId = document.getElementById('routeId').value;
    try {
      if (routeId) {
        await apiFetch(`/api/routes/${routeId}`, { method: 'PUT', body: JSON.stringify(payload) });
        showToast('Route updated successfully', 'success');
      } else {
        await apiFetch('/api/routes', { method: 'POST', body: JSON.stringify(payload) });
        showToast('Route created successfully', 'success');
      }
      resetRouteForm();
      await loadRoutes();
    } catch (error) {
      showToast(error.message || 'Unable to save route', 'error');
    }
  });

  document.getElementById('resetRouteForm').addEventListener('click', resetRouteForm);
  document.getElementById('routeSearch').addEventListener('input', (event) => {
    const query = event.target.value.toLowerCase();
    const rows = document.querySelectorAll('#routeTableBody tr');
    rows.forEach((row) => row.style.display = row.textContent.toLowerCase().includes(query) ? '' : 'none');
  });
});

async function loadRoutes() {
  try {
    const data = await apiFetch('/api/routes');
    const tbody = document.getElementById('routeTableBody');
    tbody.innerHTML = '';
    (data.routes || []).forEach((route) => {
      const row = document.createElement('tr');
      row.innerHTML = `
        <td>${escapeHtml(route.route_name)}</td>
        <td>${escapeHtml(route.start_location)}</td>
        <td>${escapeHtml(route.destination)}</td>
        <td>${escapeHtml(route.distance_km)}</td>
        <td>${escapeHtml(route.duration_minutes)}</td>
        <td>${escapeHtml(route.bus_number || 'Unassigned')}</td>
        <td>
          <div class="action-row">
            <button class="btn btn-secondary" data-action="edit" data-id="${route.id}">Edit</button>
            <button class="btn btn-danger" data-action="delete" data-id="${route.id}">Delete</button>
          </div>
        </td>
      `;
      tbody.appendChild(row);
    });

    tbody.querySelectorAll('button[data-action="edit"]').forEach((btn) => btn.addEventListener('click', () => editRoute(btn.dataset.id)));
    tbody.querySelectorAll('button[data-action="delete"]').forEach((btn) => {
      btn.addEventListener('click', async () => {
        if (!confirm('Delete this route?')) return;
        try {
          await apiFetch(`/api/routes/${btn.dataset.id}`, { method: 'DELETE' });
          showToast('Route deleted successfully', 'success');
          await loadRoutes();
        } catch (error) {
          showToast(error.message || 'Unable to delete route', 'error');
        }
      });
    });
  } catch (error) {
    showToast(error.message || 'Unable to load routes', 'error');
  }
}

async function editRoute(id) {
  try {
    const data = await apiFetch(`/api/routes/${id}`);
    const route = data.route;
    document.getElementById('routeId').value = route.id;
    document.getElementById('route_name').value = route.route_name || '';
    document.getElementById('start_location').value = route.start_location || '';
    document.getElementById('destination').value = route.destination || '';
    document.getElementById('distance_km').value = route.distance_km || 0;
    document.getElementById('duration_minutes').value = route.duration_minutes || 0;
    document.getElementById('assigned_bus_id').value = route.assigned_bus_id || '';
    const stops = (route.stops || []).map((item) => item.stop_name || item).join(', ');
    document.getElementById('stops').value = stops;
    window.scrollTo({ top: 0, behavior: 'smooth' });
  } catch (error) {
    showToast(error.message || 'Unable to load route', 'error');
  }
}

function resetRouteForm() {
  document.getElementById('routeForm').reset();
  document.getElementById('routeId').value = '';
}

async function loadBusesForRouteSelect() {
  try {
    const data = await apiFetch('/api/buses');
    const select = document.getElementById('assigned_bus_id');
    if (!select) return;
    select.innerHTML = '<option value="">Unassigned</option>' + (data.buses || []).map((bus) => `<option value="${bus.id}">${escapeHtml(bus.bus_number)} (${escapeHtml(bus.model)})</option>`).join('');
  } catch (error) {
    console.error(error);
  }
}
