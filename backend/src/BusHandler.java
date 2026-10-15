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

public class BusHandler {
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

        if (path.equals("/api/buses") && method.equalsIgnoreCase("GET")) {
            listBuses(exchange);
            return;
        }
        if (path.startsWith("/api/buses/") && method.equalsIgnoreCase("GET")) {
            getBus(exchange, parseId(path));
            return;
        }
        if (path.equals("/api/buses") && method.equalsIgnoreCase("POST")) {
            if (!AuthHandler.requireAdmin(exchange)) {
                JsonUtil.sendJson(exchange, 403, Map.of("status", "error", "message", "Admin access required"));
                return;
            }
            createBus(exchange);
            return;
        }
        if (path.startsWith("/api/buses/") && method.equalsIgnoreCase("PUT")) {
            if (!AuthHandler.requireAdmin(exchange)) {
                JsonUtil.sendJson(exchange, 403, Map.of("status", "error", "message", "Admin access required"));
                return;
            }
            updateBus(exchange, parseId(path));
            return;
        }
        if (path.startsWith("/api/buses/") && method.equalsIgnoreCase("DELETE")) {
            if (!AuthHandler.requireAdmin(exchange)) {
                JsonUtil.sendJson(exchange, 403, Map.of("status", "error", "message", "Admin access required"));
                return;
            }
            deleteBus(exchange, parseId(path));
            return;
        }

        JsonUtil.sendJson(exchange, 404, Map.of("status", "error", "message", "Bus endpoint not found"));
    }

    private void listBuses(HttpExchange exchange) throws IOException {
        String sql = "SELECT b.*, d.name AS driver_name FROM buses b LEFT JOIN drivers d ON d.id = b.driver_id ORDER BY b.id DESC";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> buses = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> bus = new LinkedHashMap<>();
                bus.put("id", rs.getLong("id"));
                bus.put("bus_number", rs.getString("bus_number"));
                bus.put("registration_number", rs.getString("registration_number"));
                bus.put("capacity", rs.getInt("capacity"));
                bus.put("model", rs.getString("model"));
                bus.put("status", rs.getString("status"));
                bus.put("driver_id", rs.getObject("driver_id"));
                bus.put("driver_name", rs.getString("driver_name"));
                buses.add(bus);
            }
            JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "buses", buses));
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to load buses"));
        }
    }

    private void getBus(HttpExchange exchange, long id) throws IOException {
        String sql = "SELECT b.*, d.name AS driver_name FROM buses b LEFT JOIN drivers d ON d.id = b.driver_id WHERE b.id = ?";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    JsonUtil.sendJson(exchange, 404, Map.of("status", "error", "message", "Bus not found"));
                    return;
                }
                Map<String, Object> bus = new LinkedHashMap<>();
                bus.put("id", rs.getLong("id"));
                bus.put("bus_number", rs.getString("bus_number"));
                bus.put("registration_number", rs.getString("registration_number"));
                bus.put("capacity", rs.getInt("capacity"));
                bus.put("model", rs.getString("model"));
                bus.put("status", rs.getString("status"));
                bus.put("driver_id", rs.getObject("driver_id"));
                bus.put("driver_name", rs.getString("driver_name"));
                JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "bus", bus));
            }
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to load bus"));
        }
    }

    private void createBus(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readBody(exchange);
        String busNumber = requiredString(body.get("bus_number"), "Bus number");
        String registrationNumber = requiredString(body.get("registration_number"), "Registration number");
        String model = requiredString(body.get("model"), "Model");
        String status = requiredString(body.get("status"), "Status");
        int capacity;
        try {
            capacity = Integer.parseInt(String.valueOf(body.getOrDefault("capacity", "0")));
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Bus capacity must be a valid number"));
            return;
        }
        Long driverId = parseLong(body.get("driver_id"));

        if (busNumber.isEmpty() || registrationNumber.isEmpty() || model.isEmpty() || capacity <= 0) {
            JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Bus number, registration number, model, and capacity are required"));
            return;
        }

        try (Connection conn = Database.getConnection()) {
            if (exists(conn, "SELECT 1 FROM buses WHERE bus_number = ?", busNumber)) {
                JsonUtil.sendJson(exchange, 409, Map.of("status", "error", "message", "Bus number already exists"));
                return;
            }
            if (exists(conn, "SELECT 1 FROM buses WHERE registration_number = ?", registrationNumber)) {
                JsonUtil.sendJson(exchange, 409, Map.of("status", "error", "message", "Registration number already exists"));
                return;
            }
            if (driverId != null && !exists(conn, "SELECT 1 FROM drivers WHERE id = ?", driverId)) {
                JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Assigned driver does not exist"));
                return;
            }

            String sql = "INSERT INTO buses (bus_number, registration_number, capacity, model, status, driver_id) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, busNumber);
                ps.setString(2, registrationNumber);
                ps.setInt(3, capacity);
                ps.setString(4, model);
                ps.setString(5, status.isEmpty() ? "active" : status);
                if (driverId == null) {
                    ps.setNull(6, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(6, driverId);
                }
                ps.executeUpdate();
            }
            JsonUtil.sendJson(exchange, 201, Map.of("status", "success", "message", "Bus created successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to create bus"));
        }
    }

    private void updateBus(HttpExchange exchange, long id) throws IOException {
        Map<String, Object> body = readBody(exchange);
        try (Connection conn = Database.getConnection()) {
            String existingSql = "SELECT * FROM buses WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(existingSql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        JsonUtil.sendJson(exchange, 404, Map.of("status", "error", "message", "Bus not found"));
                        return;
                    }

                    String busNumber = nonNullString(body.get("bus_number"), rs.getString("bus_number"));
                    String regNumber = nonNullString(body.get("registration_number"), rs.getString("registration_number"));
                    int capacity = body.containsKey("capacity") ? Integer.parseInt(String.valueOf(body.get("capacity"))) : rs.getInt("capacity");
                    String model = nonNullString(body.get("model"), rs.getString("model"));
                    String status = nonNullString(body.get("status"), rs.getString("status"));
                    Long driverId = body.containsKey("driver_id") ? parseLong(body.get("driver_id")) : (rs.getObject("driver_id") == null ? null : rs.getLong("driver_id"));

                    if (exists(conn, "SELECT 1 FROM buses WHERE bus_number = ? AND id != ?", busNumber, id)) {
                        JsonUtil.sendJson(exchange, 409, Map.of("status", "error", "message", "Bus number already exists"));
                        return;
                    }
                    if (exists(conn, "SELECT 1 FROM buses WHERE registration_number = ? AND id != ?", regNumber, id)) {
                        JsonUtil.sendJson(exchange, 409, Map.of("status", "error", "message", "Registration number already exists"));
                        return;
                    }
                    if (driverId != null && !exists(conn, "SELECT 1 FROM drivers WHERE id = ?", driverId)) {
                        JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Assigned driver does not exist"));
                        return;
                    }

                    String sql = "UPDATE buses SET bus_number = ?, registration_number = ?, capacity = ?, model = ?, status = ?, driver_id = ? WHERE id = ?";
                    try (PreparedStatement update = conn.prepareStatement(sql)) {
                        update.setString(1, busNumber);
                        update.setString(2, regNumber);
                        update.setInt(3, capacity);
                        update.setString(4, model);
                        update.setString(5, status);
                        if (driverId == null) {
                            update.setNull(6, java.sql.Types.BIGINT);
                        } else {
                            update.setLong(6, driverId);
                        }
                        update.setLong(7, id);
                        update.executeUpdate();
                    }
                    JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "message", "Bus updated successfully"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to update bus"));
        }
    }

    private void deleteBus(HttpExchange exchange, long id) throws IOException {
        try (Connection conn = Database.getConnection()) {
            String checkSql = "SELECT COUNT(*) AS total FROM transport_registrations WHERE bus_id = ? AND registration_status = 'approved'";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt("total") > 0) {
                        JsonUtil.sendJson(exchange, 409, Map.of("status", "error", "message", "Bus has approved registrations and cannot be deleted"));
                        return;
                    }
                }
            }

            String routeCheck = "SELECT COUNT(*) AS total FROM routes WHERE assigned_bus_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(routeCheck)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt("total") > 0) {
                        JsonUtil.sendJson(exchange, 409, Map.of("status", "error", "message", "Bus is assigned to a route and cannot be deleted"));
                        return;
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM buses WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "message", "Bus deleted successfully"));
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to delete bus"));
        }
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
            return Long.parseLong(sections[sections.length - 1]);
        } catch (Exception ignored) {
            return -1;
        }
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
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nonNullString(Object value, String fallback) {
        return (value == null || String.valueOf(value).trim().isEmpty()) ? fallback : String.valueOf(value).trim();
    }

    private Long parseLong(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }
}
