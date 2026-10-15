import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class AuthHandler {
    private static final String COOKIE_NAME = Database.getEnv("AUTH_COOKIE_NAME", Database.getEnv("auth.cookie_name", "cbm_session"));
    private static final int SESSION_TTL_SECONDS = Integer.parseInt(Database.getEnv("SESSION_TTL_SECONDS", Database.getEnv("session.ttl_seconds", "43200")));

    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (method.equalsIgnoreCase("OPTIONS")) {
            JsonUtil.sendJson(exchange, 200, Map.of("status", "ok"));
            return;
        }

        if (path.equals("/api/auth/login") && method.equalsIgnoreCase("POST")) {
            login(exchange);
            return;
        }
        if (path.equals("/api/auth/logout") && method.equalsIgnoreCase("POST")) {
            logout(exchange);
            return;
        }
        if (path.equals("/api/auth/me") && method.equalsIgnoreCase("GET")) {
            me(exchange);
            return;
        }
        if (path.equals("/api/auth/init-admin") && method.equalsIgnoreCase("POST")) {
            initAdmin(exchange);
            return;
        }

        JsonUtil.sendJson(exchange, 404, Map.of("status", "error", "message", "Auth endpoint not found"));
    }

    private void login(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readBody(exchange);
        String username = String.valueOf(body.getOrDefault("username", "")).trim();
        String password = String.valueOf(body.getOrDefault("password", "")).trim();

        if (username.isEmpty() || password.isEmpty()) {
            JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Username and password are required"));
            return;
        }

        try (Connection conn = Database.getConnection()) {
            String sql = "SELECT id, username, full_name, email, password_hash, role FROM users WHERE username = ? OR email = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, username);
                ps.setString(2, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        JsonUtil.sendJson(exchange, 401, Map.of("status", "error", "message", "Invalid credentials"));
                        return;
                    }

                    String storedHash = rs.getString("password_hash");
                    if (!PasswordUtil.verifyPassword(password, storedHash)) {
                        JsonUtil.sendJson(exchange, 401, Map.of("status", "error", "message", "Invalid credentials"));
                        return;
                    }

                    String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
                    String tokenHash = sha256Hex(token);
                    String insertSql = "INSERT INTO sessions (user_id, token_hash, expires_at) VALUES (?, ?, DATE_ADD(NOW(), INTERVAL ? SECOND))";
                    try (PreparedStatement insert = conn.prepareStatement(insertSql)) {
                        insert.setLong(1, rs.getLong("id"));
                        insert.setString(2, tokenHash);
                        insert.setInt(3, SESSION_TTL_SECONDS);
                        insert.executeUpdate();
                    }

                    String cookieValue = token;
                    exchange.getResponseHeaders().add("Set-Cookie", COOKIE_NAME + "=" + cookieValue + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + SESSION_TTL_SECONDS + ";");

                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("id", rs.getLong("id"));
                    user.put("username", rs.getString("username"));
                    user.put("full_name", rs.getString("full_name"));
                    user.put("email", rs.getString("email"));
                    user.put("role", rs.getString("role"));

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("status", "success");
                    response.put("message", "Login successful");
                    response.put("user", user);
                    JsonUtil.sendJson(exchange, 200, response);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Login failed due to a server error"));
        }
    }

    private void logout(HttpExchange exchange) throws IOException {
        String token = getSessionToken(exchange);
        if (token != null) {
            String tokenHash = sha256Hex(token);
            try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement("DELETE FROM sessions WHERE token_hash = ?")) {
                ps.setString(1, tokenHash);
                ps.executeUpdate();
            } catch (Exception ignored) {
            }
        }
        exchange.getResponseHeaders().add("Set-Cookie", COOKIE_NAME + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
        JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "message", "Logged out"));
    }

    private void me(HttpExchange exchange) throws IOException {
        Map<String, Object> user = getAuthenticatedUser(exchange);
        if (user == null) {
            JsonUtil.sendJson(exchange, 401, Map.of("status", "error", "message", "Unauthorized"));
            return;
        }
        JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "user", user));
    }

    private void initAdmin(HttpExchange exchange) throws IOException {
        try (Connection conn = Database.getConnection()) {
            String countSql = "SELECT COUNT(*) AS total FROM users WHERE role = 'admin'";
            try (PreparedStatement ps = conn.prepareStatement(countSql); ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt("total") > 0) {
                    JsonUtil.sendJson(exchange, 409, Map.of("status", "error", "message", "An admin account already exists"));
                    return;
                }
            }

            String username = Database.getEnv("INITIAL_ADMIN_USERNAME", "admin");
            String fullName = Database.getEnv("INITIAL_ADMIN_FULL_NAME", "System Administrator");
            String email = Database.getEnv("INITIAL_ADMIN_EMAIL", "admin@college.edu");
            String password = Database.getEnv("INITIAL_ADMIN_PASSWORD", "");

            if (password == null || password.trim().isEmpty()) {
                JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Set INITIAL_ADMIN_PASSWORD in the environment before bootstrapping the admin"));
                return;
            }

            String hash = PasswordUtil.hashPassword(password);
            String insertSql = "INSERT INTO users (username, full_name, email, password_hash, role) VALUES (?, ?, ?, ?, 'admin')";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, username);
                ps.setString(2, fullName);
                ps.setString(3, email);
                ps.setString(4, hash);
                ps.executeUpdate();
            }

            JsonUtil.sendJson(exchange, 201, Map.of("status", "success", "message", "Administrator created successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to initialize administrator"));
        }
    }

    public static Map<String, Object> getAuthenticatedUser(HttpExchange exchange) {
        String token = getSessionToken(exchange);
        if (token == null || token.isEmpty()) {
            return null;
        }
        String tokenHash = sha256Hex(token);
        String sql = "SELECT u.id, u.username, u.full_name, u.email, u.role FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ? AND s.expires_at > NOW()";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("id", rs.getLong("id"));
                    user.put("username", rs.getString("username"));
                    user.put("full_name", rs.getString("full_name"));
                    user.put("email", rs.getString("email"));
                    user.put("role", rs.getString("role"));
                    return user;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean requireAdmin(HttpExchange exchange) {
        Map<String, Object> user = getAuthenticatedUser(exchange);
        return user != null && "admin".equals(user.get("role"));
    }

    public static boolean requireLogin(HttpExchange exchange) {
        return getAuthenticatedUser(exchange) != null;
    }

    private static Map<String, Object> readBody(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body == null || body.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> parsed = JsonUtil.parseObject(body);
        return parsed == null ? new LinkedHashMap<>() : parsed;
    }

    private static String getSessionToken(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return null;
        }
        String[] parts = cookieHeader.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith(COOKIE_NAME + "=")) {
                return trimmed.substring((COOKIE_NAME + "=").length());
            }
        }
        return null;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hashing error", e);
        }
    }
}
