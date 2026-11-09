package dev.quarkos.capes.sim;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON through Gson. Numbers that are integers stay longs so the model checks can reject a fraction. */
public final class JsonValues {
    private JsonValues() {
    }

    public static final class Bad extends IllegalArgumentException {
        public Bad(String message) {
            super(message);
        }
    }

    public static Object parse(String text) {
        try {
            return box(JsonParser.parseString(text));
        } catch (JsonSyntaxException e) {
            throw new Bad(e.getMessage() == null ? "bad json" : e.getMessage());
        }
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new Bad("expected an object");
    }

    public static String text(Object value, String name) {
        if (value instanceof String s) {
            return s;
        }
        throw new Bad(name + " must be a string");
    }

    public static boolean bool(Object value, String name) {
        if (value instanceof Boolean b) {
            return b;
        }
        throw new Bad(name + " must be a boolean");
    }

    private static Object box(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), box(entry.getValue()));
            }
            return map;
        }
        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement item : element.getAsJsonArray()) {
                list.add(box(item));
            }
            return list;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isString()) {
            return primitive.getAsString();
        }
        if (primitive.isNumber()) {
            String raw = primitive.getAsString();
            if (raw.indexOf('.') < 0 && raw.indexOf('e') < 0 && raw.indexOf('E') < 0) {
                return primitive.getAsLong();
            }
            return primitive.getAsDouble();
        }
        throw new Bad("unsupported json");
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            out.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> out.append(c);
                }
            }
            out.append('"');
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(String.valueOf(entry.getKey()), out);
                out.append(':');
                write(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(list.get(i), out);
            }
            out.append(']');
        } else if (value instanceof int[] numbers) {
            out.append('[');
            for (int i = 0; i < numbers.length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(numbers[i]);
            }
            out.append(']');
        } else {
            throw new Bad("cannot write " + value.getClass().getSimpleName());
        }
    }
}
