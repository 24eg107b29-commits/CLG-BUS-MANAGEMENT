import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StudentHandler {
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (method.equalsIgnoreCase("OPTIONS")) {
            JsonUtil.sendJson(exchange, 200, Map.of("status", "ok"));
            return;
        }

        if (!AuthHandler.requireLogin(exchange)) {
            JsonUtil.sendJson(exchange, 401, Map.of("status", "error", "message", "Unauthorized"));
            return;
        }

        if (path.equals("/api/students") && method.equalsIgnoreCase("GET")) {
            listStudents(exchange);
            return;
        }
        if (path.startsWith("/api/students/") && method.equalsIgnoreCase("GET")) {
            getStudent(exchange, parseId(path));
            return;
        }
        if (path.equals("/api/students") && method.equalsIgnoreCase("POST")) {
            if (!AuthHandler.requireAdmin(exchange)) {
                JsonUtil.sendJson(exchange, 403, Map.of("status", "error", "message", "Admin access required"));
                return;
            }
            createStudent(exchange);
            return;
        }
        if (path.startsWith("/api/students/") && method.equalsIgnoreCase("PUT")) {
            if (!AuthHandler.requireAdmin(exchange)) {
                JsonUtil.sendJson(exchange, 403, Map.of("status", "error", "message", "Admin access required"));
                return;
            }
            updateStudent(exchange, parseId(path));
            return;
        }
        if (path.startsWith("/api/students/") && method.equalsIgnoreCase("DELETE")) {
            if (!AuthHandler.requireAdmin(exchange)) {
                JsonUtil.sendJson(exchange, 403, Map.of("status", "error", "message", "Admin access required"));
                return;
            }
            deleteStudent(exchange, parseId(path));
            return;
        }

        JsonUtil.sendJson(exchange, 404, Map.of("status", "error", "message", "Student endpoint not found"));
    }

    private void listStudents(HttpExchange exchange) throws IOException {
        String sql = "SELECT s.*, u.username FROM students s LEFT JOIN users u ON u.id = s.user_id ORDER BY s.id DESC";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> students = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> student = new LinkedHashMap<>();
                student.put("id", rs.getLong("id"));
                student.put("student_id", rs.getString("student_id"));
                student.put("name", rs.getString("name"));
                student.put("email", rs.getString("email"));
                student.put("phone", rs.getString("phone"));
                student.put("department", rs.getString("department"));
                student.put("academic_year", rs.getString("academic_year"));
                student.put("status", rs.getString("status"));
                student.put("user_id", rs.getObject("user_id"));
                student.put("username", rs.getString("username"));
                students.add(student);
            }
            JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "students", students));
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to load students"));
        }
    }

    private void getStudent(HttpExchange exchange, long id) throws IOException {
        String sql = "SELECT s.*, u.username FROM students s LEFT JOIN users u ON u.id = s.user_id WHERE s.id = ?";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    JsonUtil.sendJson(exchange, 404, Map.of("status", "error", "message", "Student not found"));
                    return;
                }
                Map<String, Object> student = new LinkedHashMap<>();
                student.put("id", rs.getLong("id"));
                student.put("student_id", rs.getString("student_id"));
                student.put("name", rs.getString("name"));
                student.put("email", rs.getString("email"));
                student.put("phone", rs.getString("phone"));
                student.put("department", rs.getString("department"));
                student.put("academic_year", rs.getString("academic_year"));
                student.put("status", rs.getString("status"));
                student.put("user_id", rs.getObject("user_id"));
                student.put("username", rs.getString("username"));
                JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "student", student));
            }
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to load student"));
        }
    }

    private void createStudent(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readBody(exchange);
        String studentId = requiredString(body.get("student_id"), "Student ID");
        String name = requiredString(body.get("name"), "Student name");
        String email = requiredString(body.get("email"), "Email");
        String phone = requiredString(body.get("phone"), "Phone");
        String department = requiredString(body.get("department"), "Department");
        String academicYear = requiredString(body.get("academic_year"), "Academic year");
        String status = String.valueOf(body.getOrDefault("status", "active"));

        if (studentId.isEmpty() || name.isEmpty() || email.isEmpty() || phone.isEmpty() || department.isEmpty() || academicYear.isEmpty()) {
            JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "All required student fields must be provided"));
            return;
        }

        try (Connection conn = Database.getConnection()) {
            if (exists(conn, "SELECT 1 FROM students WHERE student_id = ?", studentId)) {
                JsonUtil.sendJson(exchange, 409, Map.of("status", "error", "message", "Student ID already exists"));
                return;
            }
            if (exists(conn, "SELECT 1 FROM students WHERE email = ?", email)) {
                JsonUtil.sendJson(exchange, 409, Map.of("status", "error", "message", "Email already exists"));
                return;
            }

            String userName = studentId.replace(" ", "").toLowerCase();
            String defaultPassword = Database.getEnv("INITIAL_STUDENT_PASSWORD", "Student@123!");
            String userHash = PasswordUtil.hashPassword(defaultPassword);
            String userSql = "INSERT INTO users (username, full_name, email, password_hash, role) VALUES (?, ?, ?, ?, 'student')";
            try (PreparedStatement userPs = conn.prepareStatement(userSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                userPs.setString(1, userName);
                userPs.setString(2, name);
                userPs.setString(3, email);
                userPs.setString(4, userHash);
                userPs.executeUpdate();
                Long userId = null;
                try (ResultSet keys = userPs.getGeneratedKeys()) {
                    if (keys.next()) {
                        userId = keys.getLong(1);
                    }
                }

                String sql = "INSERT INTO students (student_id, user_id, name, email, phone, department, academic_year, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, studentId);
                    if (userId == null) {
                        ps.setNull(2, java.sql.Types.BIGINT);
                    } else {
                        ps.setLong(2, userId);
                    }
                    ps.setString(3, name);
                    ps.setString(4, email);
                    ps.setString(5, phone);
                    ps.setString(6, department);
                    ps.setString(7, academicYear);
                    ps.setString(8, status);
                    ps.executeUpdate();
                }
            }

            JsonUtil.sendJson(exchange, 201, Map.of("status", "success", "message", "Student created successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to create student"));
        }
    }

    private void updateStudent(HttpExchange exchange, long id) throws IOException {
        Map<String, Object> body = readBody(exchange);
        try (Connection conn = Database.getConnection()) {
            String currentSql = "SELECT * FROM students WHERE id = ?";
            try (PreparedStatement currentPs = conn.prepareStatement(currentSql)) {
                currentPs.setLong(1, id);
                try (ResultSet rs = currentPs.executeQuery()) {
                    if (!rs.next()) {
                        JsonUtil.sendJson(exchange, 404, Map.of("status", "error", "message", "Student record not found"));
                        return;
                    }
                    String studentId = nonNullString(body.get("student_id"), rs.getString("student_id"));
                    String name = nonNullString(body.get("name"), rs.getString("name"));
                    String email = nonNullString(body.get("email"), rs.getString("email"));
                    String phone = nonNullString(body.get("phone"), rs.getString("phone"));
                    String department = nonNullString(body.get("department"), rs.getString("department"));
                    String academicYear = nonNullString(body.get("academic_year"), rs.getString("academic_year"));
                    String status = nonNullString(body.get("status"), rs.getString("status"));

                    if (exists(conn, "SELECT 1 FROM students WHERE student_id = ? AND id != ?", studentId, id)) {
                        JsonUtil.sendJson(exchange, 409, Map.of("status", "error", "message", "Student ID already exists"));
                        return;
                    }
                    if (exists(conn, "SELECT 1 FROM students WHERE email = ? AND id != ?", email, id)) {
                        JsonUtil.sendJson(exchange, 409, Map.of("status", "error", "message", "Email already exists"));
                        return;
                    }

                    String sql = "UPDATE students SET student_id = ?, name = ?, email = ?, phone = ?, department = ?, academic_year = ?, status = ? WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, studentId);
                        ps.setString(2, name);
                        ps.setString(3, email);
                        ps.setString(4, phone);
                        ps.setString(5, department);
                        ps.setString(6, academicYear);
                        ps.setString(7, status);
                        ps.setLong(8, id);
                        ps.executeUpdate();
                    }

                    Long userId = rs.getLong("user_id");
                    if (userId > 0) {
                        try (PreparedStatement userPs = conn.prepareStatement("UPDATE users SET full_name = ?, email = ? WHERE id = ?")) {
                            userPs.setString(1, name);
                            userPs.setString(2, email);
                            userPs.setLong(3, userId);
                            userPs.executeUpdate();
                        }
                    }

                    JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "message", "Student updated successfully"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to update student"));
        }
    }

    private void deleteStudent(HttpExchange exchange, long id) throws IOException {
        try (Connection conn = Database.getConnection()) {
            String checkSql = "SELECT COUNT(*) AS total FROM transport_registrations WHERE student_id = ? AND registration_status IN ('pending', 'approved')";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt("total") > 0) {
                        JsonUtil.sendJson(exchange, 409, Map.of("status", "error", "message", "Student has active registrations and cannot be deleted"));
                        return;
                    }
                }
            }

            String getUserSql = "SELECT user_id FROM students WHERE id = ?";
            Long userId = null;
            try (PreparedStatement ps = conn.prepareStatement(getUserSql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        userId = rs.getLong("user_id");
                    }
                }
            }

            String deleteSql = "DELETE FROM students WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }

            if (userId != null && userId > 0) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ? AND role = 'student'")) {
                    ps.setLong(1, userId);
                    ps.executeUpdate();
                }
            }

            JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "message", "Student deleted successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to delete student"));
        }
    }

    private Map<String, Object> readBody(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (requestBody == null || requestBody.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        return JsonUtil.parseObject(requestBody);
    }

    private long parseId(String path) {
        String[] sections = path.split("/");
        if (sections.length > 0) {
            try {
                return Long.parseLong(sections[sections.length - 1]);
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    private boolean exists(Connection conn, String sql, Object... params) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            Database.bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String requiredString(Object value, String fieldName) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private String nonNullString(Object value, String fallback) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return fallback;
        }
        return String.valueOf(value).trim();
    }
}
