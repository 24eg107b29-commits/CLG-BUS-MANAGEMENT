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

public class RouteHandler {
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

        if (path.equals("/api/routes") && method.equalsIgnoreCase("GET")) {
            listRoutes(exchange);
            return;
        }
        if (path.startsWith("/api/routes/") && method.equalsIgnoreCase("GET")) {
            getRoute(exchange, parseId(path));
            return;
        }
        if (path.equals("/api/routes") && method.equalsIgnoreCase("POST")) {
            if (!AuthHandler.requireAdmin(exchange)) {
                JsonUtil.sendJson(exchange, 403, Map.of("status", "error", "message", "Admin access required"));
                return;
            }
            createRoute(exchange);
            return;
        }
        if (path.startsWith("/api/routes/") && method.equalsIgnoreCase("PUT")) {
            if (!AuthHandler.requireAdmin(exchange)) {
                JsonUtil.sendJson(exchange, 403, Map.of("status", "error", "message", "Admin access required"));
                return;
            }
            updateRoute(exchange, parseId(path));
            return;
        }
        if (path.startsWith("/api/routes/") && method.equalsIgnoreCase("DELETE")) {
            if (!AuthHandler.requireAdmin(exchange)) {
                JsonUtil.sendJson(exchange, 403, Map.of("status", "error", "message", "Admin access required"));
                return;
            }
            deleteRoute(exchange, parseId(path));
            return;
        }

        JsonUtil.sendJson(exchange, 404, Map.of("status", "error", "message", "Route endpoint not found"));
    }

    private void listRoutes(HttpExchange exchange) throws IOException {
        String sql = "SELECT r.*, b.bus_number FROM routes r LEFT JOIN buses b ON b.id = r.assigned_bus_id ORDER BY r.id DESC";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> routes = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> route = new LinkedHashMap<>();
                route.put("id", rs.getLong("id"));
                route.put("route_name", rs.getString("route_name"));
                route.put("start_location", rs.getString("start_location"));
                route.put("destination", rs.getString("destination"));
                route.put("distance_km", rs.getDouble("distance_km"));
                route.put("duration_minutes", rs.getInt("duration_minutes"));
                route.put("assigned_bus_id", rs.getObject("assigned_bus_id"));
                route.put("bus_number", rs.getString("bus_number"));
                route.put("stops", getStops(conn, rs.getLong("id")));
                routes.add(route);
            }
            JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "routes", routes));
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to load routes"));
        }
    }

    private void getRoute(HttpExchange exchange, long id) throws IOException {
        String sql = "SELECT r.*, b.bus_number FROM routes r LEFT JOIN buses b ON b.id = r.assigned_bus_id WHERE r.id = ?";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    JsonUtil.sendJson(exchange, 404, Map.of("status", "error", "message", "Route not found"));
                    return;
                }
                Map<String, Object> route = new LinkedHashMap<>();
                route.put("id", rs.getLong("id"));
                route.put("route_name", rs.getString("route_name"));
                route.put("start_location", rs.getString("start_location"));
                route.put("destination", rs.getString("destination"));
                route.put("distance_km", rs.getDouble("distance_km"));
                route.put("duration_minutes", rs.getInt("duration_minutes"));
                route.put("assigned_bus_id", rs.getObject("assigned_bus_id"));
                route.put("bus_number", rs.getString("bus_number"));
                route.put("stops", getStops(conn, rs.getLong("id")));
                JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "route", route));
            }
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to load route"));
        }
    }

    private void createRoute(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readBody(exchange);
        String routeName = requiredString(body.get("route_name"), "Route name");
        String startLocation = requiredString(body.get("start_location"), "Start location");
        String destination = requiredString(body.get("destination"), "Destination");
        Object stopsValue = body.get("stops");
        double distance=0;
        try {
            distance = Double.parseDouble(String.valueOf(body.getOrDefault("distance_km", "0")));
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Distance must be a valid numeric value"));
            return;
        }
        int duration;
        try {
            duration = Integer.parseInt(String.valueOf(body.getOrDefault("duration_minutes", "0")));
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Duration must be a valid number"));
            return;
        }
        Long busId = parseLong(body.get("assigned_bus_id"));

        if (routeName.isEmpty() || startLocation.isEmpty() || destination.isEmpty() || distance <= 0 || duration <= 0) {
            JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Route name, start, destination, distance, and duration are required"));
            return;
        }

        try (Connection conn = Database.getConnection()) {
            if (busId != null && !exists(conn, "SELECT 1 FROM buses WHERE id = ?", busId)) {
                JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Assigned bus does not exist"));
                return;
            }

            String insertRouteSql = "INSERT INTO routes (route_name, start_location, destination, distance_km, duration_minutes, assigned_bus_id) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertRouteSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, routeName);
                ps.setString(2, startLocation);
                ps.setString(3, destination);
                ps.setDouble(4, distance);
                ps.setInt(5, duration);
                if (busId == null) {
                    ps.setNull(6, java.sql.Types.BIGINT);
                } else {
                    ps.setLong(6, busId);
                }
                ps.executeUpdate();
                Long routeId = null;
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        routeId = keys.getLong(1);
                    }
                }
                if (routeId != null) {
                    saveStops(conn, routeId, stopsValue);
                }
            }
            JsonUtil.sendJson(exchange, 201, Map.of("status", "success", "message", "Route created successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to create route"));
        }
    }

    private void updateRoute(HttpExchange exchange, long id) throws IOException {
        Map<String, Object> body = readBody(exchange);
        try (Connection conn = Database.getConnection()) {
            String sql = "SELECT * FROM routes WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        JsonUtil.sendJson(exchange, 404, Map.of("status", "error", "message", "Route not found"));
                        return;
                    }
                    String routeName = nonNullString(body.get("route_name"), rs.getString("route_name"));
                    String startLocation = nonNullString(body.get("start_location"), rs.getString("start_location"));
                    String destination = nonNullString(body.get("destination"), rs.getString("destination"));
                    double distance = body.containsKey("distance_km") ? Double.parseDouble(String.valueOf(body.get("distance_km"))) : rs.getDouble("distance_km");
                    int duration = body.containsKey("duration_minutes") ? Integer.parseInt(String.valueOf(body.get("duration_minutes"))) : rs.getInt("duration_minutes");
                    Long busId = body.containsKey("assigned_bus_id") ? parseLong(body.get("assigned_bus_id")) : (rs.getObject("assigned_bus_id") == null ? null : rs.getLong("assigned_bus_id"));

                    if (busId != null && !exists(conn, "SELECT 1 FROM buses WHERE id = ?", busId)) {
                        JsonUtil.sendJson(exchange, 400, Map.of("status", "error", "message", "Assigned bus does not exist"));
                        return;
                    }

                    try (PreparedStatement update = conn.prepareStatement("UPDATE routes SET route_name = ?, start_location = ?, destination = ?, distance_km = ?, duration_minutes = ?, assigned_bus_id = ? WHERE id = ?")) {
                        update.setString(1, routeName);
                        update.setString(2, startLocation);
                        update.setString(3, destination);
                        update.setDouble(4, distance);
                        update.setInt(5, duration);
                        if (busId == null) {
                            update.setNull(6, java.sql.Types.BIGINT);
                        } else {
                            update.setLong(6, busId);
                        }
                        update.setLong(7, id);
                        update.executeUpdate();
                    }
                    if (body.containsKey("stops")) {
                        try (PreparedStatement deleteOld = conn.prepareStatement("DELETE FROM route_stops WHERE route_id = ?")) {
                            deleteOld.setLong(1, id);
                            deleteOld.executeUpdate();
                        }
                        saveStops(conn, id, body.get("stops"));
                    }
                    JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "message", "Route updated successfully"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to update route"));
        }
    }

    private void deleteRoute(HttpExchange exchange, long id) throws IOException {
        try (Connection conn = Database.getConnection()) {
            String regCheck = "SELECT COUNT(*) AS total FROM transport_registrations WHERE route_id = ? AND registration_status IN ('pending', 'approved')";
            try (PreparedStatement ps = conn.prepareStatement(regCheck)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt("total") > 0) {
                        JsonUtil.sendJson(exchange, 409, Map.of("status", "error", "message", "Route has active registrations and cannot be deleted"));
                        return;
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM routes WHERE id = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            JsonUtil.sendJson(exchange, 200, Map.of("status", "success", "message", "Route deleted successfully"));
        } catch (Exception e) {
            JsonUtil.sendJson(exchange, 500, Map.of("status", "error", "message", "Unable to delete route"));
        }
    }

    private List<Map<String, Object>> getStops(Connection conn, long routeId) throws Exception {
        String stopSql = "SELECT stop_name, stop_sequence FROM route_stops WHERE route_id = ? ORDER BY stop_sequence ASC";
        try (PreparedStatement ps = conn.prepareStatement(stopSql)) {
            ps.setLong(1, routeId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> stops = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> stop = new LinkedHashMap<>();
                    stop.put("stop_name", rs.getString("stop_name"));
                    stop.put("stop_sequence", rs.getInt("stop_sequence"));
                    stops.add(stop);
                }
                return stops;
            }
        }
    }

    private void saveStops(Connection conn, long routeId, Object stopsValue) throws Exception {
        if (stopsValue == null) {
            return;
        }

        List<String> stops = new ArrayList<>();
        if (stopsValue instanceof List<?>) {
            for (Object item : (List<?>) stopsValue) {
                if (item != null) {
                    String trimmed = String.valueOf(item).trim();
                    if (!trimmed.isEmpty()) {
                        stops.add(trimmed);
                    }
                }
            }
        } else if (stopsValue instanceof String) {
            String[] split = String.valueOf(stopsValue).split(",");
            for (String item : split) {
                String trimmed = item.trim();
                if (!trimmed.isEmpty()) {
                    stops.add(trimmed);
                }
            }
        }

        int index = 1;
        for (String stopName : stops) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO route_stops (route_id, stop_name, stop_sequence) VALUES (?, ?, ?)")) {
                ps.setLong(1, routeId);
                ps.setString(2, stopName);
                ps.setInt(3, index++);
                ps.executeUpdate();
            }
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
