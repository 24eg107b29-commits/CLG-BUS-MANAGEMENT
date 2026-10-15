document.addEventListener('DOMContentLoaded', async () => {
  await checkAuth();
  await loadFeeOptions();
  await loadPayments();

  document.getElementById('feeForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const payload = {
      student_id: document.getElementById('fee_student_id').value,
      route_id: document.getElementById('fee_route_id').value,
      amount: Number(document.getElementById('fee_amount').value),
      due_date: document.getElementById('fee_due_date').value,
      status: document.getElementById('fee_status').value,
      notes: document.getElementById('fee_notes').value.trim()
    };
    try {
      await apiFetch('/api/fees', { method: 'POST', body: JSON.stringify(payload) });
      showToast('Fee record saved successfully', 'success');
      document.getElementById('feeForm').reset();
      await loadPayments();
    } catch (error) {
      showToast(error.message || 'Unable to create fee record', 'error');
    }
  });

  document.getElementById('paymentForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const payload = {
      fee_id: document.getElementById('payment_fee_id').value,
      amount: Number(document.getElementById('payment_amount').value),
      payment_date: document.getElementById('payment_date').value,
      payment_method: document.getElementById('payment_method').value,
      notes: document.getElementById('payment_notes').value.trim()
    };
    try {
      await apiFetch('/api/payments', { method: 'POST', body: JSON.stringify(payload) });
      showToast('Payment recorded successfully', 'success');
      document.getElementById('paymentForm').reset();
      await loadPayments();
    } catch (error) {
      showToast(error.message || 'Unable to record payment', 'error');
    }
  });

  document.getElementById('paymentSearch').addEventListener('input', (event) => {
    const query = event.target.value.toLowerCase();
    const rows = document.querySelectorAll('#paymentTableBody tr');
    rows.forEach((row) => row.style.display = row.textContent.toLowerCase().includes(query) ? '' : 'none');
  });
});

async function loadPayments() {
  try {
    const data = await apiFetch('/api/payments');
    const tbody = document.getElementById('paymentTableBody');
    tbody.innerHTML = '';
    (data.payments || []).forEach((payment) => {
      const row = document.createElement('tr');
      row.innerHTML = `
        <td>${escapeHtml(payment.student_name || 'Unknown')}</td>
        <td>${escapeHtml(payment.route_name || 'Unknown')}</td>
        <td>${escapeHtml(payment.amount)}</td>
        <td>${escapeHtml(payment.payment_date || '—')}</td>
        <td>${getStatusBadge(payment.status || 'paid')}</td>
        <td>${escapeHtml(payment.notes || '—')}</td>
      `;
      tbody.appendChild(row);
    });
  } catch (error) {
    showToast(error.message || 'Unable to load payments', 'error');
  }
}

async function loadFeeOptions() {
  try {
    const students = await apiFetch('/api/students');
    const routes = await apiFetch('/api/routes');
    const fees = await apiFetch('/api/fees');

    const studentSelect = document.getElementById('fee_student_id');
    if (studentSelect) {
      studentSelect.innerHTML = '<option value="">Select student</option>' + (students.students || []).map((student) => `<option value="${student.id}">${escapeHtml(student.name)} (${escapeHtml(student.student_id)})</option>`).join('');
    }

    const routeSelect = document.getElementById('fee_route_id');
    if (routeSelect) {
      routeSelect.innerHTML = '<option value="">Select route</option>' + (routes.routes || []).map((route) => `<option value="${route.id}">${escapeHtml(route.route_name)}</option>`).join('');
    }

    const feeSelect = document.getElementById('payment_fee_id');
    if (feeSelect) {
      feeSelect.innerHTML = '<option value="">Select fee entry</option>' + (fees.fees || []).map((fee) => `<option value="${fee.id}">${escapeHtml(fee.student_name || 'Student')} - ${escapeHtml(fee.amount)}</option>`).join('');
    }
  } catch (error) {
    console.error(error);
  }
}
