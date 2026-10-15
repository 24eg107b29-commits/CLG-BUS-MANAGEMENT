import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) throws IOException {
        Database.loadDotEnvIfPresent();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC driver not found. Download MySQL Connector/J and place the JAR in backend/lib");
            System.exit(1);
        }

        int port = Integer.parseInt(Database.getEnv("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/health", Main::handleHealth);
        server.createContext("/api/auth", exchange -> new AuthHandler().handle(exchange));
        server.createContext("/api/dashboard", Main::handleDashboard);
        server.createContext("/api/students", exchange -> new StudentHandler().handle(exchange));
        server.createContext("/api/buses", exchange -> new BusHandler().handle(exchange));
        server.createContext("/api/routes", exchange -> new RouteHandler().handle(exchange));
        server.createContext("/api/drivers", exchange -> new DriverHandler().handle(exchange));
        server.createContext("/api/registrations", exchange -> new RegistrationHandler().handle(exchange));
        server.createContext("/api/fees", exchange -> new PaymentHandler().handle(exchange));
        server.createContext("/api/payments", exchange -> new PaymentHandler().handle(exchange));

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("College Bus Management System running on http://localhost:" + port);
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            JsonUtil.sendJson(exchange, 200, Map.of("status", "ok"));
            return;
        }

        try (Connection conn = Database.getConnection()) {
            boolean dbOk = conn.isValid(3);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", dbOk ? "ok" : "degraded");
            response.put("service", "college-bus-management");
            response.put("database", dbOk ? "connected" : "not connected");
            JsonUtil.sendJson(exchange, dbOk ? 200 : 503, response);
        } catch (Exception e) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "error");
            response.put("service", "college-bus-management");
            response.put("database", "not connected");
            response.put("message", "Database connection failed");
            JsonUtil.sendJson(exchange, 503, response);
        }
    }

    private static void handleDashboard(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            JsonUtil.sendJson(exchange, 405, Map.of("status", "error", "message", "Method not allowed"));
            return;
        }

        if (!AuthHandler.requireLogin(exchange)) {
            JsonUtil.sendJson(exchange, 401, Map.of("status", "error", "message", "Unauthorized"));
            return;
        }

        try (Connection conn = Database.getConnection()) {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("total_students", count(conn, "SELECT COUNT(*) FROM students"));
            stats.put("total_buses", count(conn, "SELECT COUNT(*) FROM buses"));
            stats.put("total_drivers", count(conn, "SELECT COUNT(*) FROM drivers"));
            stats.put("total_routes", count(conn, "SELECT COUNT(*) FROM routes"));
            stats.put("active_registrations", count(conn, "SELECT COUNT(*) FROM transport_registrations WHERE registration_status = 'approved'"));
            stats.put("pending_fees", count(conn, "SELECT COUNT(*) FROM transport_fees WHERE fee_status IN ('pending', 'overdue')"));
            JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "stats", stats));
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to load dashboard statistics"));
        }
    }

    private static int count(Connection conn, String sql) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }
}
