package dev.quarkos.capes.sim;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates the cube model carried beside a cape. Unknown fields are rejected. */
public final class CapeModelJson {
    public static final int MAX_BYTES = 16 * 1024;
    public static final int MAX_CUBES = 64;

    private static final Set<String> CUBE_FIELDS = Set.of("x", "y", "z", "w", "h", "d", "u", "v");

    public record Cube(int x, int y, int z, int w, int h, int d, int u, int v) {
    }

    public static final class Invalid extends IllegalArgumentException {
        public Invalid(String message) {
            super(message);
        }
    }

    private CapeModelJson() {
    }

    public static List<Cube> parse(String json, int frameWidth, int frameHeight) {
        if (json == null) {
            throw new Invalid("model is missing");
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new Invalid("model exceeds 16 KiB");
        }
        Object parsed;
        try {
            parsed = JsonValues.parse(json);
        } catch (JsonValues.Bad bad) {
            throw new Invalid(bad.getMessage());
        }
        Map<String, Object> root = JsonValues.object(parsed);
        if (root.size() != 1 || !root.containsKey("cubes")) {
            throw new Invalid("model has an unknown field");
        }
        Object cubesValue = root.get("cubes");
        if (!(cubesValue instanceof List<?> cubes)) {
            throw new Invalid("cubes must be an array");
        }
        if (cubes.size() > MAX_CUBES) {
            throw new Invalid("too many cubes");
        }
        List<Cube> out = new ArrayList<>(cubes.size());
        for (Object item : cubes) {
            out.add(cube(item, frameWidth, frameHeight));
        }
        return List.copyOf(out);
    }

    private static Cube cube(Object item, int frameWidth, int frameHeight) {
        Map<String, Object> map = JsonValues.object(item);
        for (String key : map.keySet()) {
            if (!CUBE_FIELDS.contains(key)) {
                throw new Invalid("cube has an unknown field");
            }
        }
        if (!map.keySet().containsAll(CUBE_FIELDS)) {
            throw new Invalid("cube is missing a field");
        }
        int x = integer(map.get("x"), "x", -32, 32);
        int y = integer(map.get("y"), "y", -32, 32);
        int z = integer(map.get("z"), "z", -32, 32);
        int w = integer(map.get("w"), "w", 1, 32);
        int h = integer(map.get("h"), "h", 1, 32);
        int d = integer(map.get("d"), "d", 1, 32);
        int u = integer(map.get("u"), "u", 0, Math.max(0, frameWidth - 1));
        int v = integer(map.get("v"), "v", 0, Math.max(0, frameHeight - 1));
        return new Cube(x, y, z, w, h, d, u, v);
    }

    private static int integer(Object value, String name, int min, int max) {
        if (!(value instanceof Long number)) {
            throw new Invalid(name + " must be an integer");
        }
        if (number < min || number > max) {
            throw new Invalid(name + " is out of range");
        }
        return number.intValue();
    }
}
