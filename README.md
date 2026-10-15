<<<<<<< HEAD
# College Bus Management System

A full-stack college transportation management system built with HTML, CSS, JavaScript, a Java built-in HTTP server, and MySQL 8.

## Project overview

This project helps a college manage students, buses, drivers, routes, transport registrations, and fee tracking. The frontend is a responsive dashboard for administrators and students, while the backend exposes REST APIs backed by MySQL.

## Features

- Admin and student login
- Secure password hashing with PBKDF2-based hashing
- Dashboard with real statistics from MySQL
- Student CRUD operations
- Bus and driver management
- Route and stop planning
- Transport registration approvals
- Fee tracking and payment history
- Responsive frontend for desktop, tablet, and mobile

## Technology stack

- Frontend: HTML5, CSS3, Vanilla JavaScript
- Backend: Java 17+ with `com.sun.net.httpserver.HttpServer`
- Database: MySQL 8
- Connector: MySQL Connector/J
- Authentication: secure session cookies and PBKDF2 password hashing

## Folder structure

```text
college-bus-management/
├── backend/
│   ├── lib/
│   ├── src/
│   ├── compile.bat
│   └── run.bat
├── database/
│   ├── schema.sql
│   └── sample_data.sql
├── frontend/
│   ├── css/
│   ├── js/
│   ├── index.html
│   ├── login.html
│   ├── dashboard.html
│   ├── students.html
│   ├── buses.html
│   ├── routes.html
│   ├── drivers.html
│   ├── registrations.html
│   └── payments.html
├── .env.example
├── .gitignore
├── README.md
├── DEPLOYMENT.md
└──
```

## Prerequisites

- Java 17 or newer (Java 25 is also compatible)
- MySQL 8 installed locally or on a remote server
- MySQL Connector/J JAR file
- Python 3 for serving static frontend files locally

## MySQL setup

1. Open MySQL Workbench or the command line.
2. Create the database:

```sql
CREATE DATABASE college_bus_management;
```

3. Import the schema:

```bash
mysql -u root -p college_bus_management < database/schema.sql
```

4. Import sample data:

```bash
mysql -u root -p college_bus_management < database/sample_data.sql
```

## JDBC driver installation

Download the official MySQL Connector/J JAR from:

https://dev.mysql.com/downloads/connector/j/

After download, place the JAR in:

```text
college-bus-management/backend/lib/
```

Example filename:

```text
backend/lib/mysql-connector-j-9.1.0.jar
```

## Environment variable configuration

Copy the example file:

```bash
copy .env.example .env
```

Then update the file with your values:

```env
DB_HOST=localhost
DB_PORT=3306
DB_NAME=college_bus_management
DB_USER=root
DB_PASSWORD=your_mysql_password
PORT=8080
CORS_ALLOWED_ORIGINS=http://localhost:8000,http://127.0.0.1:8000,http://localhost
INITIAL_ADMIN_USERNAME=admin
INITIAL_ADMIN_FULL_NAME=System Administrator
INITIAL_ADMIN_EMAIL=admin@college.edu
INITIAL_ADMIN_PASSWORD=ChangeMeStrongPassword123!
```

Windows note: use a real password, not a placeholder, and keep the file local to your machine.

## Backend compilation and startup

From the project root, run:

```cmd
cd backend
compile.bat
run.bat
```

The backend runs on port 8080 by default. If needed, change the port by editing the `.env` file or the `run.bat` command.

## Frontend startup

Serve the frontend locally from the project root:

```bash
cd frontend
python -m http.server 8000
```

Then open:

```text
http://localhost:8000/index.html
```

The frontend calls the backend at:

```text
http://localhost:8080
```

## API endpoints

### Authentication

- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/logout`
- `POST /api/auth/init-admin`

### Dashboard

- `GET /api/dashboard/stats`

### Students

- `GET /api/students`
- `GET /api/students/{id}`
- `POST /api/students`
- `PUT /api/students/{id}`
- `DELETE /api/students/{id}`

### Buses

- `GET /api/buses`
- `GET /api/buses/{id}`
- `POST /api/buses`
- `PUT /api/buses/{id}`
- `DELETE /api/buses/{id}`

### Routes

- `GET /api/routes`
- `GET /api/routes/{id}`
- `POST /api/routes`
- `PUT /api/routes/{id}`
- `DELETE /api/routes/{id}`

### Drivers

- `GET /api/drivers`
- `GET /api/drivers/{id}`
- `POST /api/drivers`
- `PUT /api/drivers/{id}`
- `DELETE /api/drivers/{id}`

### Registrations

- `GET /api/registrations`
- `GET /api/registrations/{id}`
- `POST /api/registrations`
- `PUT /api/registrations/{id}`
- `PATCH /api/registrations/{id}/approval`

### Fees and payments

- `GET /api/fees`
- `POST /api/fees`
- `PUT /api/fees/{id}`
- `GET /api/payments`
- `POST /api/payments`

## Administrator initialization

The system includes a safe bootstrap endpoint to create the first admin user using environment variables. Do not hardcode the production admin password. Use this process:

1. Set `INITIAL_ADMIN_USERNAME`, `INITIAL_ADMIN_EMAIL`, `INITIAL_ADMIN_PASSWORD` in `.env`.
2. Start the backend.
3. Send a POST request to:

```http
POST /api/auth/init-admin
```

Only one admin is created. The endpoint refuses to overwrite an existing admin if one exists.

## Testing procedures

1. Confirm the backend health endpoint responds at `http://localhost:8080/health`.
2. Log in with the admin user.
3. Create a student, bus, route, and driver.
4. Register a student for a route and approve it.
5. Verify the dashboard counts update.
6. Create a fee and payment record.
7. Confirm unauthorized users cannot access admin endpoints.

## Common errors and fixes

- MySQL connection error: verify `.env` values and ensure the MySQL server is running.
- JDBC driver not found: copy the connector JAR into `backend/lib`.
- Login fails: ensure the `password_hash` was created with the Java hash function and the user exists.
- Browser blocks API request: confirm the backend is running and CORS is configured.
- 404 on page: open the correct page path from the frontend folder.

## Deployment instructions

See `DEPLOYMENT.md` for a practical deployment plan including GitHub, hosting, MySQL, frontend hosting, and environment configuration.

## Notes

- The project does not use localStorage for authentication; it uses secure HTTP-only session cookies.
- The app keeps database credentials in environment variables instead of source code.
- Sample data is included for easy setup, but production credentials should be replaced with secure values.
=======
# CLG-BUS-MANAGEMENT
>>>>>>> dbacd6a20ef4acce17cb117db615d51ccd4147b3
