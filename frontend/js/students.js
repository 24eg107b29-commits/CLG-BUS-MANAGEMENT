document.addEventListener('DOMContentLoaded', async () => {
  await checkAuth();
  await loadStudents();
  await loadStudentOptions();

  document.getElementById('studentForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const payload = {
      student_id: document.getElementById('student_id').value.trim(),
      name: document.getElementById('name').value.trim(),
      email: document.getElementById('email').value.trim(),
      phone: document.getElementById('phone').value.trim(),
      department: document.getElementById('department').value.trim(),
      academic_year: document.getElementById('academic_year').value.trim(),
      status: document.getElementById('status').value
    };

    const studentId = document.getElementById('studentId').value;
    try {
      if (studentId) {
        await apiFetch(`/api/students/${studentId}`, {
          method: 'PUT',
          body: JSON.stringify(payload)
        });
        showToast('Student updated successfully', 'success');
      } else {
        await apiFetch('/api/students', {
          method: 'POST',
          body: JSON.stringify(payload)
        });
        showToast('Student created successfully', 'success');
      }
      resetStudentForm();
      await loadStudents();
    } catch (error) {
      showToast(error.message || 'Unable to save student', 'error');
    }
  });

  document.getElementById('studentSearch').addEventListener('input', async (event) => {
    const query = event.target.value.toLowerCase();
    const rows = document.querySelectorAll('#studentTableBody tr');
    rows.forEach((row) => {
      const text = row.textContent.toLowerCase();
      row.style.display = text.includes(query) ? '' : 'none';
    });
  });

  document.getElementById('resetStudentForm').addEventListener('click', resetStudentForm);
});

async function loadStudents() {
  try {
    const data = await apiFetch('/api/students');
    const tbody = document.getElementById('studentTableBody');
    tbody.innerHTML = '';

    (data.students || []).forEach((student) => {
      const row = document.createElement('tr');
      row.innerHTML = `
        <td>${escapeHtml(student.id)}</td>
        <td>${escapeHtml(student.student_id)}</td>
        <td>${escapeHtml(student.name)}</td>
        <td>${escapeHtml(student.department)}</td>
        <td>${escapeHtml(student.academic_year)}</td>
        <td>${getStatusBadge(student.status)}</td>
        <td>
          <div class="action-row">
            <button class="btn btn-secondary" data-action="edit" data-id="${student.id}">Edit</button>
            <button class="btn btn-danger" data-action="delete" data-id="${student.id}">Delete</button>
          </div>
        </td>
      `;
      tbody.appendChild(row);
    });

    tbody.querySelectorAll('button[data-action="edit"]').forEach((btn) => {
      btn.addEventListener('click', () => {
        editStudent(btn.dataset.id);
      });
    });

    tbody.querySelectorAll('button[data-action="delete"]').forEach((btn) => {
      btn.addEventListener('click', async () => {
        if (!confirm('Delete this student?')) return;
        try {
          await apiFetch(`/api/students/${btn.dataset.id}`, { method: 'DELETE' });
          showToast('Student deleted successfully', 'success');
          await loadStudents();
        } catch (error) {
          showToast(error.message || 'Unable to delete student', 'error');
        }
      });
    });
  } catch (error) {
    showToast(error.message || 'Unable to load students', 'error');
  }
}

async function editStudent(id) {
  try {
    const data = await apiFetch(`/api/students/${id}`);
    const student = data.student;
    document.getElementById('studentId').value = student.id;
    document.getElementById('student_id').value = student.student_id || '';
    document.getElementById('name').value = student.name || '';
    document.getElementById('email').value = student.email || '';
    document.getElementById('phone').value = student.phone || '';
    document.getElementById('department').value = student.department || '';
    document.getElementById('academic_year').value = student.academic_year || '';
    document.getElementById('status').value = student.status || 'active';
    window.scrollTo({ top: 0, behavior: 'smooth' });
  } catch (error) {
    showToast(error.message || 'Unable to load student details', 'error');
  }
}

function resetStudentForm() {
  document.getElementById('studentForm').reset();
  document.getElementById('studentId').value = '';
  document.getElementById('status').value = 'active';
}

async function loadStudentOptions() {
  try {
    const data = await apiFetch('/api/students');
    const select = document.getElementById('registration_student_id');
    if (!select) return;
    select.innerHTML = '<option value="">Select student</option>' + (data.students || []).map((item) => `<option value="${item.id}">${escapeHtml(item.name)} (${escapeHtml(item.student_id)})</option>`).join('');
  } catch (error) {
    console.error(error);
  }
}
