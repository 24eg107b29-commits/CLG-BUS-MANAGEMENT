import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Database {
    public static final String ENV_PATH = "../.env";

    public static void loadDotEnvIfPresent() {
        Path path = Path.of(ENV_PATH);
        if (!Files.exists(path)) {
            return;
        }
        try {
            Files.readAllLines(path).forEach(line -> {
                if (line == null || line.trim().isEmpty() || line.trim().startsWith("#")) {
                    return;
                }
                int index = line.indexOf('=');
                if (index <= 0) {
                    return;
                }
                String key = line.substring(0, index).trim();
                String value = line.substring(index + 1).trim();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                String normalizedKey = key.trim();
                if (System.getenv(normalizedKey) == null && System.getProperty(normalizedKey) == null) {
                    System.setProperty(normalizedKey, value);
                }
            });
        } catch (IOException e) {
            System.err.println("Could not read .env file: " + e.getMessage());
        }
    }

    public static String getEnv(String key, String defaultValue) {
        String normalizedKey = key == null ? "" : key.trim();
        if (normalizedKey.isEmpty()) {
            return defaultValue;
        }

        String[] candidates = new String[] {
            normalizedKey,
            normalizedKey.toUpperCase(Locale.ROOT),
            normalizedKey.replace('.', '_').replace('-', '_'),
            normalizedKey.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT),
            normalizedKey.replace('_', '.').replace('-', '.')
        };

        for (String candidate : candidates) {
            String value = System.getenv(candidate);
            if (value == null) {
                value = System.getProperty(candidate);
            }
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }

        return defaultValue;
    }

    public static String getJdbcUrl() {
        String host = getEnv("DB_HOST", "localhost");
        String port = getEnv("DB_PORT", "3306");
        String dbName = getEnv("DB_NAME", "college_bus_management");
        return "jdbc:mysql://" + host + ":" + port + "/" + dbName + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    public static Connection getConnection() throws SQLException {
        String user = getEnv("DB_USER", "root");
        String password = getEnv("DB_PASSWORD", "");
        return DriverManager.getConnection(getJdbcUrl(), user, password);
    }

    public static Map<String, Object> fetchSingle(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> row = new HashMap<>();
                int columnCount = rs.getMetaData().getColumnCount();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
                }
                return row;
            }
        }
    }

    public static int executeUpdate(String sql, Object... params) throws SQLException {
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindParams(ps, params);
            return ps.executeUpdate();
        }
    }

    public static ResultSet query(String sql, Object... params) throws SQLException {
        Connection conn = getConnection();
        PreparedStatement ps = conn.prepareStatement(sql);
        bindParams(ps, params);
        return ps.executeQuery();
    }

    public static void bindParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object param = params[i];
            if (param == null) {
                ps.setNull(i + 1, java.sql.Types.NULL);
            } else if (param instanceof Integer) {
                ps.setInt(i + 1, (Integer) param);
            } else if (param instanceof Long) {
                ps.setLong(i + 1, (Long) param);
            } else if (param instanceof Double) {
                ps.setDouble(i + 1, (Double) param);
            } else if (param instanceof Boolean) {
                ps.setBoolean(i + 1, (Boolean) param);
            } else {
                ps.setString(i + 1, String.valueOf(param));
            }
        }
    }
}
