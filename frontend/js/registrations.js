document.addEventListener('DOMContentLoaded', async () => {
  await checkAuth();
  await loadRegistrationOptions();
  await loadRegistrations();

  document.getElementById('registrationForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const payload = {
      student_id: document.getElementById('registration_student_id').value,
      route_id: document.getElementById('registration_route_id').value,
      bus_id: document.getElementById('registration_bus_id').value || null,
      notes: document.getElementById('registration_notes').value.trim()
    };

    try {
      await apiFetch('/api/registrations', { method: 'POST', body: JSON.stringify(payload) });
      showToast('Registration submitted successfully', 'success');
      document.getElementById('registrationForm').reset();
      await loadRegistrations();
    } catch (error) {
      showToast(error.message || 'Unable to create registration', 'error');
    }
  });

  document.getElementById('registrationSearch').addEventListener('input', (event) => {
    const query = event.target.value.toLowerCase();
    const rows = document.querySelectorAll('#registrationTableBody tr');
    rows.forEach((row) => row.style.display = row.textContent.toLowerCase().includes(query) ? '' : 'none');
  });
});

async function loadRegistrations() {
  try {
    const data = await apiFetch('/api/registrations');
    const tbody = document.getElementById('registrationTableBody');
    tbody.innerHTML = '';
    (data.registrations || []).forEach((registration) => {
      const row = document.createElement('tr');
      row.innerHTML = `
        <td>${escapeHtml(registration.student_name || registration.student_code || 'Unknown')}</td>
        <td>${escapeHtml(registration.route_name || 'Unknown route')}</td>
        <td>${escapeHtml(registration.bus_number || 'Unassigned')}</td>
        <td>${getStatusBadge(registration.registration_status)}</td>
        <td>${escapeHtml(registration.notes || '—')}</td>
        <td>
          <div class="action-row">
            <button class="btn btn-secondary" data-action="approve" data-id="${registration.id}">Approve</button>
            <button class="btn btn-danger" data-action="reject" data-id="${registration.id}">Reject</button>
          </div>
        </td>
      `;
      tbody.appendChild(row);
    });

    tbody.querySelectorAll('button[data-action="approve"]').forEach((btn) => btn.addEventListener('click', async () => {
      try {
        await apiFetch(`/api/registrations/${btn.dataset.id}/approval`, {
          method: 'PATCH',
          body: JSON.stringify({ status: 'approved', notes: 'Approved by admin' })
        });
        showToast('Registration approved', 'success');
        await loadRegistrations();
      } catch (error) {
        showToast(error.message || 'Unable to approve registration', 'error');
      }
    }));

    tbody.querySelectorAll('button[data-action="reject"]').forEach((btn) => btn.addEventListener('click', async () => {
      try {
        await apiFetch(`/api/registrations/${btn.dataset.id}/approval`, {
          method: 'PATCH',
          body: JSON.stringify({ status: 'rejected', notes: 'Rejected by admin' })
        });
        showToast('Registration rejected', 'success');
        await loadRegistrations();
      } catch (error) {
        showToast(error.message || 'Unable to reject registration', 'error');
      }
    }));
  } catch (error) {
    showToast(error.message || 'Unable to load registrations', 'error');
  }
}

async function loadRegistrationOptions() {
  try {
    const students = await apiFetch('/api/students');
    const routes = await apiFetch('/api/routes');
    const buses = await apiFetch('/api/buses');

    const studentSelect = document.getElementById('registration_student_id');
    if (studentSelect) {
      studentSelect.innerHTML = '<option value="">Select student</option>' + (students.students || []).map((student) => `<option value="${student.id}">${escapeHtml(student.name)} (${escapeHtml(student.student_id)})</option>`).join('');
    }

    const routeSelect = document.getElementById('registration_route_id');
    if (routeSelect) {
      routeSelect.innerHTML = '<option value="">Select route</option>' + (routes.routes || []).map((route) => `<option value="${route.id}">${escapeHtml(route.route_name)} (${escapeHtml(route.start_location)} -> ${escapeHtml(route.destination)})</option>`).join('');
    }

    const busSelect = document.getElementById('registration_bus_id');
    if (busSelect) {
      busSelect.innerHTML = '<option value="">Any assigned bus</option>' + (buses.buses || []).map((bus) => `<option value="${bus.id}">${escapeHtml(bus.bus_number)}</option>`).join('');
    }
  } catch (error) {
    console.error(error);
  }
}
