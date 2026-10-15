document.addEventListener('DOMContentLoaded', async () => {
  await checkAuth();
  await loadDrivers();

  document.getElementById('driverForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const payload = {
      name: document.getElementById('driver_name').value.trim(),
      phone: document.getElementById('driver_phone').value.trim(),
      license_number: document.getElementById('license_number').value.trim(),
      status: document.getElementById('driver_status').value
    };
    const driverId = document.getElementById('driverId').value;
    try {
      if (driverId) {
        await apiFetch(`/api/drivers/${driverId}`, { method: 'PUT', body: JSON.stringify(payload) });
        showToast('Driver updated successfully', 'success');
      } else {
        await apiFetch('/api/drivers', { method: 'POST', body: JSON.stringify(payload) });
        showToast('Driver created successfully', 'success');
      }
      resetDriverForm();
      await loadDrivers();
    } catch (error) {
      showToast(error.message || 'Unable to save driver', 'error');
    }
  });

  document.getElementById('resetDriverForm').addEventListener('click', resetDriverForm);
  document.getElementById('driverSearch').addEventListener('input', (event) => {
    const query = event.target.value.toLowerCase();
    const rows = document.querySelectorAll('#driverTableBody tr');
    rows.forEach((row) => row.style.display = row.textContent.toLowerCase().includes(query) ? '' : 'none');
  });
});

async function loadDrivers() {
  try {
    const data = await apiFetch('/api/drivers');
    const tbody = document.getElementById('driverTableBody');
    tbody.innerHTML = '';
    (data.drivers || []).forEach((driver) => {
      const row = document.createElement('tr');
      row.innerHTML = `
        <td>${escapeHtml(driver.name)}</td>
        <td>${escapeHtml(driver.phone)}</td>
        <td>${escapeHtml(driver.license_number)}</td>
        <td>${getStatusBadge(driver.status)}</td>
        <td>
          <div class="action-row">
            <button class="btn btn-secondary" data-action="edit" data-id="${driver.id}">Edit</button>
            <button class="btn btn-danger" data-action="delete" data-id="${driver.id}">Delete</button>
          </div>
        </td>
      `;
      tbody.appendChild(row);
    });

    tbody.querySelectorAll('button[data-action="edit"]').forEach((btn) => btn.addEventListener('click', () => editDriver(btn.dataset.id)));
    tbody.querySelectorAll('button[data-action="delete"]').forEach((btn) => {
      btn.addEventListener('click', async () => {
        if (!confirm('Delete this driver?')) return;
        try {
          await apiFetch(`/api/drivers/${btn.dataset.id}`, { method: 'DELETE' });
          showToast('Driver deleted successfully', 'success');
          await loadDrivers();
        } catch (error) {
          showToast(error.message || 'Unable to delete driver', 'error');
        }
      });
    });
  } catch (error) {
    showToast(error.message || 'Unable to load drivers', 'error');
  }
}

async function editDriver(id) {
  try {
    const data = await apiFetch(`/api/drivers/${id}`);
    const driver = data.driver;
    document.getElementById('driverId').value = driver.id;
    document.getElementById('driver_name').value = driver.name || '';
    document.getElementById('driver_phone').value = driver.phone || '';
    document.getElementById('license_number').value = driver.license_number || '';
    document.getElementById('driver_status').value = driver.status || 'available';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  } catch (error) {
    showToast(error.message || 'Unable to load driver', 'error');
  }
}

function resetDriverForm() {
  document.getElementById('driverForm').reset();
  document.getElementById('driverId').value = '';
  document.getElementById('driver_status').value = 'available';
}
