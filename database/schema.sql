CREATE DATABASE IF NOT EXISTS college_bus_management;
USE college_bus_management;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL UNIQUE,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('admin','student') NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS students (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id VARCHAR(50) NOT NULL UNIQUE,
    user_id BIGINT NULL,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    phone VARCHAR(30) NOT NULL,
    department VARCHAR(100) NOT NULL,
    academic_year VARCHAR(50) NOT NULL,
    status ENUM('active','inactive','pending') NOT NULL DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_students_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS drivers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    license_number VARCHAR(80) NOT NULL UNIQUE,
    status ENUM('available','on_leave','unavailable') NOT NULL DEFAULT 'available',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS buses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    bus_number VARCHAR(50) NOT NULL UNIQUE,
    registration_number VARCHAR(50) NOT NULL UNIQUE,
    capacity INT NOT NULL,
    model VARCHAR(100) NOT NULL,
    status ENUM('active','maintenance','inactive') NOT NULL DEFAULT 'active',
    driver_id BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_buses_driver FOREIGN KEY (driver_id) REFERENCES drivers(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS routes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    route_name VARCHAR(150) NOT NULL,
    start_location VARCHAR(150) NOT NULL,
    destination VARCHAR(150) NOT NULL,
    distance_km DECIMAL(8,2) NOT NULL,
    duration_minutes INT NOT NULL,
    assigned_bus_id BIGINT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_routes_bus FOREIGN KEY (assigned_bus_id) REFERENCES buses(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS route_stops (
    id BIGINT NOT NULL AUTO_INCREMENT,
    route_id BIGINT NOT NULL,
    stop_name VARCHAR(150) NOT NULL,
    stop_sequence INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_route_stops_route FOREIGN KEY (route_id) REFERENCES routes(id) ON DELETE CASCADE,
    UNIQUE KEY unique_route_stop_sequence (route_id, stop_sequence)
);

CREATE TABLE IF NOT EXISTS transport_registrations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    route_id BIGINT NOT NULL,
    bus_id BIGINT NULL,
    registration_status ENUM('pending','approved','rejected') NOT NULL DEFAULT 'pending',
    registered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    approved_by BIGINT NULL,
    notes VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reg_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    CONSTRAINT fk_reg_route FOREIGN KEY (route_id) REFERENCES routes(id) ON DELETE RESTRICT,
    CONSTRAINT fk_reg_bus FOREIGN KEY (bus_id) REFERENCES buses(id) ON DELETE SET NULL,
    CONSTRAINT fk_reg_approved_by FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE KEY unique_active_student_route (student_id, route_id)
);

CREATE TABLE IF NOT EXISTS transport_fees (
    id BIGINT NOT NULL AUTO_INCREMENT,
    registration_id BIGINT NOT NULL,
    fee_amount DECIMAL(10,2) NOT NULL,
    due_date DATE NOT NULL,
    fee_status ENUM('pending','paid','overdue') NOT NULL DEFAULT 'pending',
    payment_date DATE NULL,
    notes VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_fees_registration FOREIGN KEY (registration_id) REFERENCES transport_registrations(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    registration_id BIGINT NOT NULL,
    fee_id BIGINT NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_date DATE NOT NULL,
    payment_method VARCHAR(50) NOT NULL DEFAULT 'cash',
    status ENUM('completed','pending','failed','refunded') NOT NULL DEFAULT 'completed',
    notes VARCHAR(255) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_payments_registration FOREIGN KEY (registration_id) REFERENCES transport_registrations(id) ON DELETE CASCADE,
    CONSTRAINT fk_payments_fee FOREIGN KEY (fee_id) REFERENCES transport_fees(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_students_department ON students(department);
CREATE INDEX idx_students_status ON students(status);
CREATE INDEX idx_buses_status ON buses(status);
CREATE INDEX idx_routes_destination ON routes(destination);
CREATE INDEX idx_reg_status ON transport_registrations(registration_status);
CREATE INDEX idx_fees_status ON transport_fees(fee_status);
