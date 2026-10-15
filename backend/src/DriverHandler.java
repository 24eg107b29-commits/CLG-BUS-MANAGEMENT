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

public class DriverHandler {
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

        if (path.equals("/api/drivers") && method.equalsIgnoreCase("GET")) {
            listDrivers(exchange);
            return;
        }
        if (path.startsWith("/api/drivers/") && method.equalsIgnoreCase("GET")) {
            getDriver(exchange, parseId(path));
            return;
        }
        if (path.equals("/api/drivers") && method.equalsIgnoreCase("POST")) {
            if (!AuthHandler.requireAdmin(exchange)) {
                JsonUtil.sendJson(exchange, 403, Map.of("status", "error", "message", "Admin access required"));
                return;
            }
            createDriver(exchange);
            return;
        }
        if (path.startsWith("/api/drivers/") && method.equalsIgnoreCase("PUT")) {
            if (!AuthHandler.requireAdmin(exchange)) {
                JsonUtil.sendJson(exchange, 403, Map.of("status", "error", "message", "Admin access required"));
                return;
            }
            updateDriver(exchange, parseId(path));
            return;
        }
        if (path.startsWith("/api/drivers/") && method.equalsIgnoreCase("DELETE")) {
            if (!AuthHandler.requireAdmin(exchange)) {
                JsonUtil.sendJson(exchange, 403, Map.of("status", "error", "message", "Admin access required"));
                return;
            }
            deleteDriver(exchange, parseId(path));
            return;
        }

        JsonUtil.sendJson(exchange, 404, Map.of("status", "error", "message", "Driver endpoint not found"));
    }

    private void listDrivers(HttpExchange exchange) throws IOException {
        String sql = "SELECT * FROM drivers ORDER BY id DESC";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> drivers = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> driver = new LinkedHashMap<>();
                driver.put("id", rs.getLong("id"));
                driver.put("name", rs.getString("name"));
                driver.put("phone", rs.getString("phone"));
                driver.put("license_number", rs.getString("license_number"));
                driver.put("status", rs.getString("status"));
                drivers.add(driver);
            }
            JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "drivers", drivers));
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to load drivers"));
        }
    }

    private void getDriver(HttpExchange exchange, long id) throws IOException {
        String sql = "SELECT * FROM drivers WHERE id = ?";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    JsonUtil.sendJson(exchange, 404, Map.of("status", "error", "message", "Driver not found"));
                    return;
                }
                Map<String, Object> driver = new LinkedHashMap<>();
                driver.put("id", rs.getLong("id"));
                driver.put("name", rs.getString("name"));
                driver.put("phone", rs.getString("phone"));
                driver.put("license_number", rs.getString("license_number"));
                driver.put("status", rs.getString("status"));
                JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "driver", driver));
            }
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to load driver"));
        }
    }

    private void createDriver(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readBody(exchange);
        String name = requiredString(body.get("name"), "Name");
        String phone = requiredString(body.get("phone"), "Phone");
        String licenseNumber = requiredString(body.get("license_number"), "License number");
        String status = requiredString(body.get("status"), "Status");

        if (name.isEmpty() || phone.isEmpty() || licenseNumber.isEmpty()) {
            JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Driver name, phone, and license number are required"));
            return;
        }

        try (Connection conn = Database.getConnection()) {
            if (exists(conn, "SELECT 1 FROM drivers WHERE license_number = ?", licenseNumber)) {
                JsonUtil.sendJson(exchange, 409, Map.of("status", "error", "message", "License number already exists"));
                return;
            }
            String sql = "INSERT INTO drivers (name, phone, license_number, status) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, phone);
                ps.setString(3, licenseNumber);
                ps.setString(4, status.isEmpty() ? "available" : status);
                ps.executeUpdate();
            }
            JsonUtil.sendJson(exchange, 201, Map.of("status", "success", "message", "Driver created successfully"));
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to create driver"));
        }
    }

    private void updateDriver(HttpExchange exchange, long id) throws IOException {
        Map<String, Object> body = readBody(exchange);
        try (Connection conn = Database.getConnection()) {
            String existingSql = "SELECT * FROM drivers WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(existingSql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        JsonUtil.sendJson(exchange, 404, Map.of("status", "error", "message", "Driver not found"));
                        return;
                    }
                    String name = nonNullString(body.get("name"), rs.getString("name"));
                    String phone = nonNullString(body.get("phone"), rs.getString("phone"));
                    String licenseNumber = nonNullString(body.get("license_number"), rs.getString("license_number"));
                    String status = nonNullString(body.get("status"), rs.getString("status"));

                    if (exists(conn, "SELECT 1 FROM drivers WHERE license_number = ? AND id != ?", licenseNumber, id)) {
                        JsonUtil.sendJson(exchange, 409, Map.of("status", "error", "message", "License number already exists"));
                        return;
                    }

                    String sql = "UPDATE drivers SET name = ?, phone = ?, license_number = ?, status = ? WHERE id = ?";
                    try (PreparedStatement update = conn.prepareStatement(sql)) {
                        update.setString(1, name);
                        update.setString(2, phone);
                        update.setString(3, licenseNumber);
                        update.setString(4, status);
                        update.setLong(5, id);
                        update.executeUpdate();
                    }
                    JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "message", "Driver updated successfully"));
                }
            }
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to update driver"));
        }
    }

    private void deleteDriver(HttpExchange exchange, long id) throws IOException {
        try (Connection conn = Database.getConnection()) {
            String busCheck = "SELECT COUNT(*) AS total FROM buses WHERE driver_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(busCheck)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt("total") > 0) {
                        JsonUtil.sendJson(exchange, 409, Map.of("status", "error", "message", "Driver is assigned to a bus and cannot be deleted"));
                        return;
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM drivers WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "message", "Driver deleted successfully"));
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to delete driver"));
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
}
