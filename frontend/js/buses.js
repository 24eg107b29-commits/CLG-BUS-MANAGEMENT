document.addEventListener('DOMContentLoaded', async () => {
  await checkAuth();
  await loadDriversForSelect();
  await loadBuses();

  document.getElementById('busForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const payload = {
      bus_number: document.getElementById('bus_number').value.trim(),
      registration_number: document.getElementById('registration_number').value.trim(),
      capacity: Number(document.getElementById('capacity').value),
      model: document.getElementById('model').value.trim(),
      status: document.getElementById('bus_status').value,
      driver_id: document.getElementById('driver_id').value || null,
    };
    const busId = document.getElementById('busId').value;

    try {
      if (busId) {
        await apiFetch(`/api/buses/${busId}`, { method: 'PUT', body: JSON.stringify(payload) });
        showToast('Bus updated successfully', 'success');
      } else {
        await apiFetch('/api/buses', { method: 'POST', body: JSON.stringify(payload) });
        showToast('Bus created successfully', 'success');
      }
      resetBusForm();
      await loadBuses();
    } catch (error) {
      showToast(error.message || 'Unable to save bus', 'error');
    }
  });

  document.getElementById('resetBusForm').addEventListener('click', resetBusForm);
  document.getElementById('busSearch').addEventListener('input', (event) => {
    const query = event.target.value.toLowerCase();
    const rows = document.querySelectorAll('#busTableBody tr');
    rows.forEach((row) => row.style.display = row.textContent.toLowerCase().includes(query) ? '' : 'none');
  });
});

async function loadBuses() {
  try {
    const data = await apiFetch('/api/buses');
    const tbody = document.getElementById('busTableBody');
    tbody.innerHTML = '';
    (data.buses || []).forEach((bus) => {
      const row = document.createElement('tr');
      row.innerHTML = `
        <td>${escapeHtml(bus.bus_number)}</td>
        <td>${escapeHtml(bus.registration_number)}</td>
        <td>${escapeHtml(bus.capacity)}</td>
        <td>${escapeHtml(bus.model)}</td>
        <td>${getStatusBadge(bus.status)}</td>
        <td>${escapeHtml(bus.driver_name || 'Unassigned')}</td>
        <td>
          <div class="action-row">
            <button class="btn btn-secondary" data-action="edit" data-id="${bus.id}">Edit</button>
            <button class="btn btn-danger" data-action="delete" data-id="${bus.id}">Delete</button>
          </div>
        </td>
      `;
      tbody.appendChild(row);
    });

    tbody.querySelectorAll('button[data-action="edit"]').forEach((btn) => btn.addEventListener('click', () => editBus(btn.dataset.id)));
    tbody.querySelectorAll('button[data-action="delete"]').forEach((btn) => {
      btn.addEventListener('click', async () => {
        if (!confirm('Delete this bus?')) return;
        try {
          await apiFetch(`/api/buses/${btn.dataset.id}`, { method: 'DELETE' });
          showToast('Bus deleted successfully', 'success');
          await loadBuses();
        } catch (error) {
          showToast(error.message || 'Unable to delete bus', 'error');
        }
      });
    });
  } catch (error) {
    showToast(error.message || 'Unable to load buses', 'error');
  }
}

async function editBus(id) {
  try {
    const data = await apiFetch(`/api/buses/${id}`);
    const bus = data.bus;
    document.getElementById('busId').value = bus.id;
    document.getElementById('bus_number').value = bus.bus_number || '';
    document.getElementById('registration_number').value = bus.registration_number || '';
    document.getElementById('capacity').value = bus.capacity || 0;
    document.getElementById('model').value = bus.model || '';
    document.getElementById('bus_status').value = bus.status || 'active';
    document.getElementById('driver_id').value = bus.driver_id || '';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  } catch (error) {
    showToast(error.message || 'Unable to load bus', 'error');
  }
}

function resetBusForm() {
  document.getElementById('busForm').reset();
  document.getElementById('busId').value = '';
  document.getElementById('bus_status').value = 'active';
}

async function loadDriversForSelect() {
  try {
    const selectIds = ['driver_id', 'registration_bus_id'];
    const data = await apiFetch('/api/drivers');
    selectIds.forEach((id) => {
      const select = document.getElementById(id);
      if (!select) return;
      select.innerHTML = '<option value="">None</option>' + (data.drivers || []).map((driver) => `<option value="${driver.id}">${escapeHtml(driver.name)} (${escapeHtml(driver.status)})</option>`).join('');
    });
  } catch (error) {
    console.error(error);
  }
}
