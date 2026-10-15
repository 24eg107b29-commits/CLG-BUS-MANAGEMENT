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

public class PaymentHandler {
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

        if (path.equals("/api/fees") && method.equalsIgnoreCase("GET")) {
            listFees(exchange);
            return;
        }
        if (path.equals("/api/fees") && method.equalsIgnoreCase("POST")) {
            if (!AuthHandler.requireAdmin(exchange)) {
                JsonUtil.sendJson(exchange, 403, Map.of("status", "error", "message", "Admin access required"));
                return;
            }
            createFee(exchange);
            return;
        }
        if (path.startsWith("/api/fees/") && method.equalsIgnoreCase("PUT")) {
            if (!AuthHandler.requireAdmin(exchange)) {
                JsonUtil.sendJson(exchange, 403, Map.of("status", "error", "message", "Admin access required"));
                return;
            }
            updateFee(exchange, parseId(path));
            return;
        }
        if (path.equals("/api/payments") && method.equalsIgnoreCase("GET")) {
            listPayments(exchange);
            return;
        }
        if (path.equals("/api/payments") && method.equalsIgnoreCase("POST")) {
            if (!AuthHandler.requireAdmin(exchange)) {
                JsonUtil.sendJson(exchange, 403, Map.of("status", "error", "message", "Admin access required"));
                return;
            }
            createPayment(exchange);
            return;
        }

        JsonUtil.sendJson(exchange, 404, Map.of("status", "error", "message", "Payment endpoint not found"));
    }

    private void listFees(HttpExchange exchange) throws IOException {
        String sql = "SELECT f.*, r.student_id, s.name AS student_name, route.route_name FROM transport_fees f " +
                "LEFT JOIN transport_registrations r ON r.id = f.registration_id " +
                "LEFT JOIN students s ON s.id = r.student_id " +
                "LEFT JOIN routes route ON route.id = r.route_id ORDER BY f.id DESC";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> fees = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> fee = new LinkedHashMap<>();
                fee.put("id", rs.getLong("id"));
                fee.put("registration_id", rs.getLong("registration_id"));
                fee.put("student_id", rs.getObject("student_id"));
                fee.put("student_name", rs.getString("student_name"));
                fee.put("route_name", rs.getString("route_name"));
                fee.put("fee_amount", rs.getBigDecimal("fee_amount"));
                fee.put("due_date", rs.getDate("due_date"));
                fee.put("fee_status", rs.getString("fee_status"));
                fee.put("payment_date", rs.getDate("payment_date"));
                fee.put("notes", rs.getString("notes"));
                fees.add(fee);
            }
            JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "fees", fees));
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to load fees"));
        }
    }

    private void createFee(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readBody(exchange);
        Long registrationId = parseLong(body.get("registration_id"));
        String feeAmount = String.valueOf(body.getOrDefault("fee_amount", "0"));
        String dueDate = String.valueOf(body.getOrDefault("due_date", ""));
        String status = String.valueOf(body.getOrDefault("fee_status", "pending"));
        String notes = String.valueOf(body.getOrDefault("notes", ""));

        if (registrationId == null || feeAmount.isEmpty() || dueDate.isEmpty()) {
            JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Registration ID, fee amount, and due date are required"));
            return;
        }

        try (Connection conn = Database.getConnection()) {
            if (!exists(conn, "SELECT 1 FROM transport_registrations WHERE id = ?", registrationId)) {
                JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Registration not found"));
                return;
            }
            String sql = "INSERT INTO transport_fees (registration_id, fee_amount, due_date, fee_status, notes) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, registrationId);
                ps.setBigDecimal(2, java.math.BigDecimal.valueOf(Double.parseDouble(feeAmount)));
                ps.setDate(3, java.sql.Date.valueOf(dueDate));
                ps.setString(4, status);
                ps.setString(5, notes);
                ps.executeUpdate();
            }
            JsonUtil.sendJson(exchange, 201, Map.of("status", "success", "message", "Transport fee record created successfully"));
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to create transport fee"));
        }
    }

    private void updateFee(HttpExchange exchange, long id) throws IOException {
        Map<String, Object> body = readBody(exchange);
        try (Connection conn = Database.getConnection()) {
            String sql = "UPDATE transport_fees SET fee_amount = ?, due_date = ?, fee_status = ?, payment_date = ?, notes = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBigDecimal(1, new java.math.BigDecimal(String.valueOf(body.getOrDefault("fee_amount", "0"))));
                ps.setDate(2, java.sql.Date.valueOf(String.valueOf(body.getOrDefault("due_date", "2026-01-01"))));
                ps.setString(3, String.valueOf(body.getOrDefault("fee_status", "pending")));
                if (body.get("payment_date") == null || String.valueOf(body.get("payment_date")).isEmpty()) {
                    ps.setNull(4, java.sql.Types.DATE);
                } else {
                    ps.setDate(4, java.sql.Date.valueOf(String.valueOf(body.get("payment_date"))));
                }
                ps.setString(5, String.valueOf(body.getOrDefault("notes", "")));
                ps.setLong(6, id);
                ps.executeUpdate();
            }
            JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "message", "Fee updated successfully"));
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to update fee"));
        }
    }

    private void listPayments(HttpExchange exchange) throws IOException {
        String sql = "SELECT p.*, f.fee_amount, s.name AS student_name FROM payments p LEFT JOIN transport_fees f ON f.id = p.fee_id LEFT JOIN transport_registrations r ON r.id = p.registration_id LEFT JOIN students s ON s.id = r.student_id ORDER BY p.id DESC";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> payments = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> payment = new LinkedHashMap<>();
                payment.put("id", rs.getLong("id"));
                payment.put("registration_id", rs.getLong("registration_id"));
                payment.put("fee_id", rs.getObject("fee_id"));
                payment.put("fee_amount", rs.getBigDecimal("fee_amount"));
                payment.put("student_name", rs.getString("student_name"));
                payment.put("amount", rs.getBigDecimal("amount"));
                payment.put("payment_date", rs.getDate("payment_date"));
                payment.put("payment_method", rs.getString("payment_method"));
                payment.put("status", rs.getString("status"));
                payment.put("notes", rs.getString("notes"));
                payments.add(payment);
            }
            JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "payments", payments));
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to load payments"));
        }
    }

    private void createPayment(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readBody(exchange);
        Long registrationId = parseLong(body.get("registration_id"));
        Long feeId = parseLong(body.get("fee_id"));
        String amount = String.valueOf(body.getOrDefault("amount", "0"));
        String paymentDate = String.valueOf(body.getOrDefault("payment_date", ""));
        String paymentMethod = String.valueOf(body.getOrDefault("payment_method", "cash"));
        String notes = String.valueOf(body.getOrDefault("notes", ""));

        if (registrationId == null || paymentDate.isEmpty() || amount.isEmpty()) {
            JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Registration ID, payment date, and amount are required"));
            return;
        }

        try (Connection conn = Database.getConnection()) {
            if (!exists(conn, "SELECT 1 FROM transport_registrations WHERE id = ?", registrationId)) {
                JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Registration not found"));
                return;
            }
            String sql = "INSERT INTO payments (registration_id, fee_id, amount, payment_date, payment_method, status, notes) VALUES (?, ?, ?, ?, ?, 'completed', ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, registrationId);
                if (feeId == null || feeId <= 0) {
                    ps.setNull(2, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(2, feeId);
                }
                ps.setBigDecimal(3, new java.math.BigDecimal(amount));
                ps.setDate(4, java.sql.Date.valueOf(paymentDate));
                ps.setString(5, paymentMethod);
                ps.setString(6, notes);
                ps.executeUpdate();
            }

            if (feeId != null && feeId > 0) {
                String feeUpdateSql = "UPDATE transport_fees SET fee_status = 'paid', payment_date = ? WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(feeUpdateSql)) {
                    ps.setDate(1, java.sql.Date.valueOf(paymentDate));
                    ps.setLong(2, feeId);
                    ps.executeUpdate();
                }
            }

            JsonUtil.sendJson(exchange, 201, Map.of("status", "success", "message", "Payment recorded successfully"));
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to record payment"));
        }
    }

    private Map<String, Object> readBody(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body == null || body.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        return JsonUtil.parseObject(body);
    }

    private boolean exists(Connection conn, String sql, Object... params) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            Database.bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private long parseId(String path) {
        String[] sections = path.split("/");
        try {
            return Long.parseLong(sections[sections.length - 1]);
        } catch (Exception ignored) {
            return -1;
        }
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
