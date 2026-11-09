package dev.quarkos.capes.client.net;

import dev.quarkos.capes.sim.JsonValues;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads gallery JSON. Every object must say {@code "api": 1}. */
public final class GalleryPages {
    public record Entry(String id, String title, String style, boolean animateFrames, int frameCount, String author) {
    }

    public record Page(List<Entry> entries, String nextCursor) {
    }

    public record Detail(
            String id,
            String title,
            String style,
            boolean animateFrames,
            int frameCount,
            int frameWidth,
            int frameHeight,
            int[] frameDelayMs,
            String modelJson
    ) {
    }

    private GalleryPages() {
    }

    public static Page page(String json) {
        Map<String, Object> root = api(json);
        Object raw = root.containsKey("items") ? root.get("items") : root.get("entries");
        if (!(raw instanceof List<?> list)) {
            throw new JsonValues.Bad("items must be an array");
        }
        List<Entry> entries = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = JsonValues.object(item);
            String style = text(map, "style", "flat");
            String author = text(map, "author", "");
            if (author.isBlank()) {
                author = text(map, "ownerName", "");
            }
            if (author.isBlank()) {
                author = text(map, "owner", "");
            }
            entries.add(new Entry(
                    text(map, "id", ""),
                    text(map, "title", text(map, "id", "")),
                    style,
                    bool(map, "animateFrames"),
                    number(map, "frameCount", framesStyle(style) ? 1 : 1),
                    author
            ));
        }
        String cursor = root.get("nextCursor") instanceof String s && !s.isBlank() ? s : null;
        return new Page(List.copyOf(entries), cursor);
    }

    public static Detail detail(String json) {
        Map<String, Object> root = api(json);
        String style = text(root, "style", "flat");
        int count = Math.clamp(number(root, "frameCount", 1), 1, 256);
        int[] delays = delays(root.get("frameDelayMs"), count);
        String model = null;
        Object modelValue = root.get("model");
        if (modelValue instanceof Map<?, ?>) {
            model = JsonValues.write(modelValue);
        }
        return new Detail(
                text(root, "id", ""),
                text(root, "title", text(root, "id", "")),
                style,
                bool(root, "animateFrames"),
                Math.max(1, count),
                number(root, "frameWidth", 64),
                number(root, "frameHeight", 32),
                delays,
                model
        );
    }

    private static Map<String, Object> api(String json) {
        Map<String, Object> root = JsonValues.object(JsonValues.parse(json));
        Object api = root.get("api");
        if (!(api instanceof Long value) || value != 1L) {
            throw new JsonValues.Bad("api must be 1");
        }
        return root;
    }

    private static String text(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        if (value instanceof String s) {
            return s;
        }
        return fallback;
    }

    private static boolean bool(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Boolean b && b;
    }

    private static int number(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        if (value instanceof Long n) {
            return n.intValue();
        }
        return fallback;
    }

    private static int[] delays(Object value, int count) {
        int n = Math.max(1, count);
        int[] delays = new int[n];
        if (value instanceof List<?> list && list.size() == n) {
            for (int i = 0; i < n; i++) {
                Object item = list.get(i);
                delays[i] = item instanceof Long number ? number.intValue() : 100;
            }
            return delays;
        }
        for (int i = 0; i < n; i++) {
            delays[i] = 100;
        }
        return delays;
    }

    private static boolean framesStyle(String style) {
        return "frames".equals(style);
    }
}
