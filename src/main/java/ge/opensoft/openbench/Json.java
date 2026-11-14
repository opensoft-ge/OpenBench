package ge.opensoft.openbench;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public final class Json {
    private Json() {}

    public static final JsonMapper MAPPER = JsonMapper.builder().build();

    public static Map<String, Object> map(Object... pairs) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("Expected an object");
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> objects(Object value) {
        return (List<Map<String, Object>>) value;
    }

    public static long integer(Object value) {
        if (!(value instanceof Integer || value instanceof Long))
            throw new IllegalArgumentException("Expected an integer");
        return ((Number) value).longValue();
    }

    public static String text(Object value) {
        if (!(value instanceof String text) || text.isBlank())
            throw new IllegalArgumentException("Expected nonempty text");
        return text;
    }

    public static String canonical(Object value) {
        if (value instanceof Map<?, ?> object)
            return "{"
                    + String.join(
                            ",",
                            new TreeMap<>(object(object))
                                    .entrySet().stream()
                                            .map(entry ->
                                                    canonical(entry.getKey())
                                                            + ":"
                                                            + canonical(entry.getValue()))
                                            .toList())
                    + "}";
        if (value instanceof List<?> list)
            return "[" + String.join(",", list.stream().map(Json::canonical).toList()) + "]";
        if (value instanceof Number number && !Double.isFinite(number.doubleValue()))
            throw new IllegalArgumentException("Nonfinite JSON numbers are forbidden");
        return MAPPER.writeValueAsString(value);
    }

    public static byte[] bytes(Object value) {
        return canonical(value).getBytes(StandardCharsets.UTF_8);
    }

    public static Object snapshot(Object value) {
        return MAPPER.readValue(bytes(value), Object.class);
    }

    public static Object read(Path path) throws IOException {
        return MAPPER.readValue(Files.readAllBytes(path), Object.class);
    }

    public static void write(Path path, Object value) throws IOException {
        Files.write(path, bytes(value), StandardOpenOption.CREATE_NEW);
    }

    public static String digest(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    public static String digest(Object value) throws NoSuchAlgorithmException {
        return digest(bytes(value));
    }
}
