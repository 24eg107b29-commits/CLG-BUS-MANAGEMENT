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

public class RegistrationHandler {
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

        if (path.equals("/api/registrations") && method.equalsIgnoreCase("GET")) {
            listRegistrations(exchange);
            return;
        }
        if (path.startsWith("/api/registrations/") && method.equalsIgnoreCase("GET")) {
            getRegistration(exchange, parseId(path));
            return;
        }
        if (path.equals("/api/registrations") && method.equalsIgnoreCase("POST")) {
            createRegistration(exchange);
            return;
        }
        if (path.startsWith("/api/registrations/") && method.equalsIgnoreCase("PUT")) {
            updateRegistration(exchange, parseId(path));
            return;
        }
        if (path.matches("/api/registrations/.*/approval") && method.equalsIgnoreCase("PATCH")) {
            approveRegistration(exchange, parseId(path.replace("/approval", "")));
            return;
        }

        JsonUtil.sendJson(exchange, 404, Map.of("status", "error", "message", "Registration endpoint not found"));
    }

    private void listRegistrations(HttpExchange exchange) throws IOException {
        String sql = "SELECT r.*, s.name AS student_name, s.student_id, ro.route_name, b.bus_number FROM transport_registrations r " +
                "LEFT JOIN students s ON s.id = r.student_id " +
                "LEFT JOIN routes ro ON ro.id = r.route_id " +
                "LEFT JOIN buses b ON b.id = r.bus_id " +
                "ORDER BY r.id DESC";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> registrations = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", rs.getLong("id"));
                item.put("student_id", rs.getLong("student_id"));
                item.put("student_name", rs.getString("student_name"));
                item.put("student_code", rs.getString("student_id"));
                item.put("route_id", rs.getLong("route_id"));
                item.put("route_name", rs.getString("route_name"));
                item.put("bus_id", rs.getObject("bus_id"));
                item.put("bus_number", rs.getString("bus_number"));
                item.put("registration_status", rs.getString("registration_status"));
                item.put("registered_at", rs.getTimestamp("registered_at"));
                item.put("notes", rs.getString("notes"));
                registrations.add(item);
            }
            JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "registrations", registrations));
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to load registrations"));
        }
    }

    private void getRegistration(HttpExchange exchange, long id) throws IOException {
        String sql = "SELECT r.*, s.name AS student_name, s.student_id, ro.route_name, b.bus_number FROM transport_registrations r " +
                "LEFT JOIN students s ON s.id = r.student_id " +
                "LEFT JOIN routes ro ON ro.id = r.route_id " +
                "LEFT JOIN buses b ON b.id = r.bus_id WHERE r.id = ?";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    JsonUtil.sendJson(exchange, 404, Map.of("status", "error", "message", "Registration not found"));
                    return;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", rs.getLong("id"));
                item.put("student_id", rs.getLong("student_id"));
                item.put("student_name", rs.getString("student_name"));
                item.put("student_code", rs.getString("student_id"));
                item.put("route_id", rs.getLong("route_id"));
                item.put("route_name", rs.getString("route_name"));
                item.put("bus_id", rs.getObject("bus_id"));
                item.put("bus_number", rs.getString("bus_number"));
                item.put("registration_status", rs.getString("registration_status"));
                item.put("registered_at", rs.getTimestamp("registered_at"));
                item.put("notes", rs.getString("notes"));
                JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "registration", item));
            }
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to load registration"));
        }
    }

    private void createRegistration(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readBody(exchange);
        Long studentId = parseLong(body.get("student_id"));
        Long routeId = parseLong(body.get("route_id"));
        Long busId = parseLong(body.get("bus_id"));
        String notes = valueString(body.get("notes"));

        if (studentId == null || routeId == null) {
            JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Student and route are required"));
            return;
        }

        try (Connection conn = Database.getConnection()) {
            if (!exists(conn, "SELECT 1 FROM students WHERE id = ?", studentId)) {
                JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Student not found"));
                return;
            }
            if (!exists(conn, "SELECT 1 FROM routes WHERE id = ?", routeId)) {
                JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Route not found"));
                return;
            }
            if (busId != null && !exists(conn, "SELECT 1 FROM buses WHERE id = ?", busId)) {
                JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Selected bus does not exist"));
                return;
            }
            if (exists(conn, "SELECT 1 FROM transport_registrations WHERE student_id = ? AND route_id = ? AND registration_status IN ('pending', 'approved')", studentId, routeId)) {
                JsonUtil.sendJson(exchange, 409, Map.of("status", "error", "message", "This student already has an active registration for this route"));
                return;
            }

            String insertSql = "INSERT INTO transport_registrations (student_id, route_id, bus_id, registration_status, notes) VALUES (?, ?, ?, 'pending', ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setLong(1, studentId);
                ps.setLong(2, routeId);
                if (busId == null) {
                    ps.setNull(3, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(3, busId);
                }
                ps.setString(4, notes);
                ps.executeUpdate();
            }
            JsonUtil.sendJson(exchange, 201, Map.of("status", "success", "message", "Registration submitted successfully"));
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to submit registration"));
        }
    }

    private void updateRegistration(HttpExchange exchange, long id) throws IOException {
        Map<String, Object> body = readBody(exchange);
        try (Connection conn = Database.getConnection()) {
            String existingSql = "SELECT * FROM transport_registrations WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(existingSql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        JsonUtil.sendJson(exchange, 404, Map.of("status", "error", "message", "Registration not found"));
                        return;
                    }
                    String status = nonNullString(body.get("registration_status"), rs.getString("registration_status"));
                    String notes = nonNullString(body.get("notes"), rs.getString("notes"));
                    Long busId = body.containsKey("bus_id") ? parseLong(body.get("bus_id")) : (rs.getObject("bus_id") == null ? null : rs.getLong("bus_id"));

                    String sql = "UPDATE transport_registrations SET registration_status = ?, bus_id = ?, notes = ? WHERE id = ?";
                    try (PreparedStatement update = conn.prepareStatement(sql)) {
                        update.setString(1, status);
                        if (busId == null) {
                            update.setNull(2, java.sql.Types.BIGINT);
                        } else {
                            update.setLong(2, busId);
                        }
                        update.setString(3, notes);
                        update.setLong(4, id);
                        update.executeUpdate();
                    }
                    JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "message", "Registration updated successfully"));
                }
            }
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to update registration"));
        }
    }

    private void approveRegistration(HttpExchange exchange, long id) throws IOException {
        if (!AuthHandler.requireAdmin(exchange)) {
            JsonUtil.sendJson(exchange, 403, Map.of("status", "error", "message", "Admin access required"));
            return;
        }

        Map<String, Object> body = readBody(exchange);
        String action = String.valueOf(body.getOrDefault("status", "approved")).trim();
        String notes = String.valueOf(body.getOrDefault("notes", "")).trim();
        try (Connection conn = Database.getConnection()) {
            String selectSql = "SELECT * FROM transport_registrations WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        JsonUtil.sendJson(exchange, 404, Map.of("status", "error", "message", "Registration not found"));
                        return;
                    }
                    Long studentId = rs.getLong("student_id");
                    Long routeId = rs.getLong("route_id");
                    Long busId = rs.getObject("bus_id") == null ? null : rs.getLong("bus_id");

                    if ("approved".equalsIgnoreCase(action)) {
                        if (busId == null) {
                            Long routeBusId = getRouteBus(conn, routeId);
                            if (routeBusId == null) {
                                JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "No bus is assigned to the selected route"));
                                return;
                            }
                            busId = routeBusId;
                        }
                        if (!hasCapacity(conn, busId, studentId)) {
                            JsonUtil.sendJson(exchange, 409, Map.of("status", "error", "message", "Bus capacity reached for this route"));
                            return;
                        }
                    }

                    String updateSql = "UPDATE transport_registrations SET registration_status = ?, bus_id = ?, notes = ?, approved_by = ? WHERE id = ?";
                    Long userId = (Long) AuthHandler.getAuthenticatedUser(exchange).get("id");
                    try (PreparedStatement update = conn.prepareStatement(updateSql)) {
                        update.setString(1, action.equalsIgnoreCase("approved") ? "approved" : "rejected");
                        if (busId == null) {
                            update.setNull(2, java.sql.Types.BIGINT);
                        } else {
                            update.setLong(2, busId);
                        }
                        update.setString(3, notes.isEmpty() ? rs.getString("notes") : notes);
                        update.setLong(4, userId);
                        update.setLong(5, id);
                        update.executeUpdate();
                    }
                    JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "message", "Registration " + (action.equalsIgnoreCase("approved") ? "approved" : "rejected") + " successfully"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to update registration status"));
        }
    }

    private boolean hasCapacity(Connection conn, long busId, long studentId) throws Exception {
        String busSql = "SELECT capacity FROM buses WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(busSql)) {
            ps.setLong(1, busId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                int capacity = rs.getInt("capacity");
                String countSql = "SELECT COUNT(*) AS total FROM transport_registrations WHERE bus_id = ? AND registration_status = 'approved'";
                try (PreparedStatement countPs = conn.prepareStatement(countSql)) {
                    countPs.setLong(1, busId);
                    try (ResultSet countRs = countPs.executeQuery()) {
                        if (countRs.next()) {
                            int used = countRs.getInt("total");
                            return used < capacity;
                        }
                    }
                }
            }
        }
        return false;
    }

    private Long getRouteBus(Connection conn, long routeId) throws Exception {
        String sql = "SELECT assigned_bus_id FROM routes WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, routeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getObject("assigned_bus_id") == null ? null : rs.getLong("assigned_bus_id");
                }
            }
        }
        return null;
    }

    private Map<String, Object> readBody(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body == null || body.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        return JsonUtil.parseObject(body);
    }

    private long parseId(String path) {
        String[] sections = path.split("/");
        try {
            return Long.parseLong(sections[sections.length - 1].replace("/approval", ""));
        } catch (Exception ignored) {
            return -1;
        }
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String valueString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean exists(Connection conn, String sql, Object... params) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            Database.bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String nonNullString(Object value, String fallback) {
        return (value == null || String.valueOf(value).trim().isEmpty()) ? fallback : String.valueOf(value).trim();
    }
}
