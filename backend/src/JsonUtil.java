import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class JsonUtil {
    public static Map<String, Object> parseObject(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        String value = json.trim();
        if (value.startsWith("{")) {
            Object parsed = parseValue(value);
            if (parsed instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedMap = (Map<String, Object>) map;
                return typedMap;
            }
        }
        return new LinkedHashMap<>();
    }

    public static Object parseValue(String json) {
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        ValueParser parser = new ValueParser(trimmed, 0);
        Object value = parser.parse();
        parser.skipWhitespace();
        if (parser.getIndex() != trimmed.length()) {
            throw new IllegalArgumentException("Unexpected trailing content in JSON: " + trimmed.substring(parser.getIndex()));
        }
        return value;
    }

    private static String parseStringValue(String input, int startIndex) {
        if (startIndex >= input.length()) {
            return null;
        }
        char quote = input.charAt(startIndex);
        if (quote != '"' && quote != '\'') {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        int index = startIndex + 1;
        while (index < input.length()) {
            char ch = input.charAt(index);
            if (ch == '\\') {
                index++;
                if (index >= input.length()) {
                    return null;
                }
                char next = input.charAt(index);
                switch (next) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (index + 4 < input.length()) {
                            String hex = input.substring(index + 1, index + 5);
                            sb.append((char) Integer.parseInt(hex, 16));
                            index += 4;
                        }
                        break;
                    default: sb.append(next); break;
                }
                index++;
                continue;
            }
            if (ch == quote) {
                return sb.toString();
            }
            sb.append(ch);
            index++;
        }
        return null;
    }

    public static String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return quote((String) value);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            Map<?, ?> map = (Map<?, ?>) value;
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append(quote(String.valueOf(entry.getKey()))).append(":").append(toJson(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (value instanceof Iterable) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) sb.append(",");
                sb.append(toJson(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        if (value instanceof Date) {
            return quote(value.toString());
        }
        return quote(String.valueOf(value));
    }

    private static String quote(String value) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(ch); break;
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public static void sendJson(HttpExchange exchange, int statusCode, Object payload) throws IOException {
        byte[] responseBody = toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.sendResponseHeaders(statusCode, responseBody.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBody);
        }
    }

    private static class ValueParser {
        private final String input;
        private int index;

        public ValueParser(String input, int index) {
            this.input = input;
            this.index = index;
        }

        public Object parse() {
            skipWhitespace();
            if (index >= input.length()) {
                return null;
            }

            char ch = input.charAt(index);
            if (ch == '{') {
                return parseObjectValue();
            }
            if (ch == '[') {
                return parseArrayValue();
            }
            if (ch == '"' || ch == '\'') {
                String value = parseStringValue(input, index);
                if (value == null) {
                    throw new IllegalArgumentException("Invalid string literal at position " + index);
                }
                index = advanceStringEnd(index);
                return value;
            }

            int start = index;
            while (index < input.length()) {
                char current = input.charAt(index);
                if (Character.isWhitespace(current) || current == ',' || current == '}' || current == ']') {
                    break;
                }
                index++;
            }
            String token = input.substring(start, index).trim();
            if (token.isEmpty()) {
                return null;
            }
            if (token.equalsIgnoreCase("true")) return true;
            if (token.equalsIgnoreCase("false")) return false;
            if (token.equalsIgnoreCase("null")) return null;
            if (token.matches("-?\\d+")) return Long.parseLong(token);
            if (token.matches("-?\\d+\\.\\d+")) return Double.parseDouble(token);
            return token;
        }

        private Map<String, Object> parseObjectValue() {
            Map<String, Object> map = new LinkedHashMap<>();
            index++;
            skipWhitespace();
            while (index < input.length()) {
                if (input.charAt(index) == '}') {
                    index++;
                    return map;
                }

                if (input.charAt(index) != '"' && input.charAt(index) != '\'') {
                    throw new IllegalArgumentException("Invalid object key at position " + index);
                }

                String key = parseStringValue(input, index);
                if (key == null) {
                    throw new IllegalArgumentException("Invalid object key at position " + index);
                }
                index = advanceStringEnd(index);
                skipWhitespace();

                if (index >= input.length() || input.charAt(index) != ':') {
                    throw new IllegalArgumentException("Missing colon in JSON object at position " + index);
                }
                index++;
                skipWhitespace();

                Object value = parse();
                map.put(key, value);
                skipWhitespace();

                if (index >= input.length()) {
                    throw new IllegalArgumentException("Unterminated JSON object");
                }
                if (input.charAt(index) == ',') {
                    index++;
                    skipWhitespace();
                    continue;
                }
                if (input.charAt(index) == '}') {
                    index++;
                    return map;
                }
                throw new IllegalArgumentException("Expected ',' or '}' in JSON object at position " + index);
            }
            throw new IllegalArgumentException("Unterminated JSON object");
        }

        private List<Object> parseArrayValue() {
            List<Object> list = new ArrayList<>();
            index++;
            skipWhitespace();
            while (index < input.length()) {
                if (input.charAt(index) == ']') {
                    index++;
                    return list;
                }
                Object value = parse();
                list.add(value);
                skipWhitespace();
                if (index >= input.length()) {
                    throw new IllegalArgumentException("Unterminated JSON array");
                }
                if (input.charAt(index) == ',') {
                    index++;
                    skipWhitespace();
                    continue;
                }
                if (input.charAt(index) == ']') {
                    index++;
                    return list;
                }
                throw new IllegalArgumentException("Expected ',' or ']' in JSON array at position " + index);
            }
            throw new IllegalArgumentException("Unterminated JSON array");
        }

        private int advanceStringEnd(int start) {
            int i = start + 1;
            while (i < input.length()) {
                char c = input.charAt(i);
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == input.charAt(start)) {
                    return i + 1;
                }
                i++;
            }
            throw new IllegalArgumentException("Unterminated string literal at position " + start);
        }

        public void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }

        public int getIndex() {
            return index;
        }
    }
}
