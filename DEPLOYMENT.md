# Deployment Guide

## 1. Publish source code to GitHub

1. Initialize a Git repository in the project root.
2. Add all project files.
3. Commit the code.
4. Create a GitHub repository and push the repository.

```bash
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin <your-github-url>
git push -u origin main
```

## 2. Deploy the Java backend

This project uses a Java built-in HTTP server and does not rely on Spring Boot or Maven. That means the deployment provider must support running a Java command directly or a custom start script.

### Recommended strategy

- Backend hosting: Render or Railway
- MySQL hosting: Railway MySQL, Aiven free-tier database, or a managed MySQL instance

### Important limitations to check before choosing a host

- Free-tier Java hosting may sleep after inactivity.
- Some free plans do not allow persistent outbound connections to a database.
- Some services limit build time or require a custom Dockerfile or Java runtime configuration.
- Some managed MySQL plans are not truly free forever and may expire.

### Example deployment idea

Use Render for the backend and Railway for MySQL. Set the backend start command via the hosting UI or custom script. Example:

```bash
java -cp "out:lib/mysql-connector-j-9.1.0.jar" Main
```

If your hosting provider requires a different Java packaging format, note that this project does not change the stack automatically. A provider without a Java runtime or custom command support will not be a good fit.

## 3. Deploy MySQL

Choose a hosted MySQL plan with at least:

- database name: `college_bus_management`
- a user with full privileges
- network access enabled for the backend host

### Example setup steps

1. Create a new MySQL database on the host.
2. Import the schema:

```bash
mysql -u <user> -p <database> < database/schema.sql
mysql -u <user> -p <database> < database/sample_data.sql
```

3. Save connection variables in the deployment environment.

## 4. Host the frontend on a static site

Recommended free choices:

- GitHub Pages
- Netlify
- Vercel static hosting

For this project, a static HTML/CSS/JS frontend is ideal. Keep the frontend as static pages that call the backend API base URL.

### Configure the frontend API base URL

Edit the JavaScript configuration in the frontend for the live API host. Example:

```js
const API_BASE_URL = 'https://your-backend-domain.example.com';
```

If you use a static host that does not allow server-side env variables, keep this value in a small config file or a build-time constant.

## 5. Configure environment variables and secrets

Set secure values in the host environment, not in source files:

```env
DB_HOST=your-db-host
DB_PORT=3306
DB_NAME=college_bus_management
DB_USER=your_db_user
DB_PASSWORD=very_secure_password
PORT=8080
CORS_ALLOWED_ORIGINS=https://your-frontend-domain.example.com
INITIAL_ADMIN_USERNAME=admin
INITIAL_ADMIN_EMAIL=admin@college.edu
INITIAL_ADMIN_PASSWORD=UseAStrongPasswordHere
```

Do not commit `.env` files to GitHub.

## 6. Configure HTTPS and CORS

- Use HTTPS on the public frontend and backend.
- Set `CORS_ALLOWED_ORIGINS` to the exact frontend domain.
- Keep `SameSite=Lax` or stricter on the cookie configuration.
- On production, enable `Secure` cookies when the site is served over HTTPS.

## 7. Testing the live application

After deployment, test the following:

1. Backend health check
2. Admin login
3. Dashboard statistics
4. Create, edit, and delete student records
5. Create a route and assign a bus
6. Approve or reject a registration
7. Record a payment
8. Confirm CORS works across origins

Do not claim the app is live until all critical checks pass.

## 8. Practical deployment checklist

- GitHub repository created and pushed
- Java backend deployed on a Java-capable host
- MySQL database created and imported
- Frontend is served from a static host
- `API_BASE_URL` points to the backend
- Environment variables configured
- HTTPS enabled
- CORS allowed origins set
- Admin account initialized securely
- Final live testing completed

## 9. Summary

This project is suitable for a college demonstration and a small real deployment, but it is not a drop-in solution for every cloud provider. Free hosting often has reliability limits, so plan for either a small free-tier deployment or a paid managed plan for a production-ready public site.
