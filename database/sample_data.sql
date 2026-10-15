USE college_bus_management;

INSERT INTO users (username, full_name, email, password_hash, role) VALUES
('admin', 'System Administrator', 'admin@college.edu', 'PBKDF2$120000$Y29sbGVnZS1idXMtc2FsdC0wMQ==$6y+1dCON4oLCFrGwJBTUkue5OUlVeGjVu68ozAcyNkU=', 'admin'),
('student1', 'Aarav Sharma', 'aarav.sharma@college.edu', 'PBKDF2$120000$c3R1ZGVudC1idXMtc2FsdC0wMQ==$57+McVsIPr2i9Nvn67NHHems8lM3bXYBtKQ4t692wHc=', 'student');

INSERT INTO students (student_id, user_id, name, email, phone, department, academic_year, status) VALUES
('CS-2024-001', 2, 'Aarav Sharma', 'aarav.sharma@college.edu', '+91 9876543210', 'Computer Science', '2024', 'active'),
('EE-2024-045', NULL, 'Meera Nair', 'meera.nair@college.edu', '+91 9988776655', 'Electrical Engineering', '2024', 'active'),
('ME-2024-012', NULL, 'Rohan Iyer', 'rohan.iyer@college.edu', '+91 9123456780', 'Mechanical Engineering', '2024', 'active');

INSERT INTO drivers (name, phone, license_number, status) VALUES
('Karthik Reddy', '+91 9000012345', 'DL-IND-1001', 'available'),
('Priya Nair', '+91 9000098765', 'DL-IND-1002', 'available'),
('Ramesh Kumar', '+91 9000043210', 'DL-IND-1003', 'on_leave');

INSERT INTO buses (bus_number, registration_number, capacity, model, status, driver_id) VALUES
('CB-101', 'TN-12-AB-1011', 40, 'Ashok Leyland', 'active', 1),
('CB-102', 'TN-12-AB-1022', 35, 'Volvo', 'active', 2),
('CB-103', 'TN-12-AB-1033', 30, 'Tata', 'maintenance', NULL);

INSERT INTO routes (route_name, start_location, destination, distance_km, duration_minutes, assigned_bus_id) VALUES
('North Campus Loop', 'College Main Gate', 'North Hostel', 12.50, 35, 1),
('City Shuttle', 'Main Bus Stand', 'Engineering Block', 18.20, 45, 2),
('Hostel Express', 'Hostel Block A', 'Central Library', 9.30, 25, NULL);

INSERT INTO route_stops (route_id, stop_name, stop_sequence) VALUES
(1, 'College Main Gate', 1),
(1, 'Library Corner', 2),
(1, 'North Hostel', 3),
(2, 'Main Bus Stand', 1),
(2, 'Market Road', 2),
(2, 'Engineering Block', 3),
(3, 'Hostel Block A', 1),
(3, 'Audit Hall', 2),
(3, 'Central Library', 3);

INSERT INTO transport_registrations (student_id, route_id, bus_id, registration_status, approved_by, notes) VALUES
(1, 1, 1, 'approved', 1, 'Approved for semester 2024-25'),
(2, 2, 2, 'pending', NULL, 'Awaiting approval'),
(3, 1, 1, 'pending', NULL, 'Waiting for route confirmation');

INSERT INTO transport_fees (registration_id, fee_amount, due_date, fee_status, payment_date, notes) VALUES
(1, 4500.00, '2026-10-15', 'paid', '2026-10-12', 'Paid in full'),
(2, 4500.00, '2026-10-20', 'pending', NULL, 'Awaiting payment'),
(3, 4500.00, '2026-10-25', 'overdue', NULL, 'Fee due');

INSERT INTO payments (registration_id, fee_id, amount, payment_date, payment_method, status, notes) VALUES
(1, 1, 4500.00, '2026-10-12', 'cash', 'completed', 'Semester transport fee paid');
