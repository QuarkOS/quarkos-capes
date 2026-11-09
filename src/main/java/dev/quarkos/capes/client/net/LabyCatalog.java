package dev.quarkos.capes.client.net;

import dev.quarkos.capes.sim.JsonValues;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dev catalog of the public cape and cloak lists. Off unless {@code CAPES_DEV_CATALOG}
 * is exactly {@code true}. A closed catalog builds no URL and sends no request.
 */
public final class LabyCatalog {
    public static final String FLAG = "CAPES_DEV_CATALOG";
    public static final String SEARCH = "https://laby.net/api/v3/search/textures/";
    public static final int PAGE = 36;
    private static final int MAX_PAGES = 40;

    private LabyCatalog() {
    }

    public record Item(String kind, String hash, String label, String slug, long uses) {
    }

    public static boolean enabled() {
        return "true".equals(System.getenv(FLAG)) || "true".equals(System.getProperty(FLAG));
    }

    /** Local-only still means no catalog request, even when the flag is on. */
    public static boolean allow(boolean localOnly) {
        return enabled() && !localOnly;
    }

    /** Empty when the catalog is off, so nothing has a URL to request. */
    public static String pageUrl(String kind, int size, int offset) {
        if (!enabled() || !kindOk(kind) || size < 1 || offset < 0) {
            return "";
        }
        int capped = Math.min(size, PAGE);
        return SEARCH + kind + "?order=most_used&size=" + capped + "&offset=" + offset;
    }

    public static List<Item> read(String kind, String json) {
        if (!enabled()) {
            return List.of();
        }
        return parse(kind, json);
    }

    public static List<Item> page(boolean allowed, String kind, int size, int offset, LabySource.Get http) throws IOException {
        if (!allowed || !enabled() || http == null) {
            return List.of();
        }
        String url = pageUrl(kind, size, offset);
        if (url.isEmpty()) {
            return List.of();
        }
        LabySource.Hit hit = LabySource.exchange(url, "search", http);
        if (hit == null || hit.status() / 100 != 2 || hit.body() == null || hit.body().length == 0) {
            return List.of();
        }
        return parse(kind, new String(hit.body(), StandardCharsets.UTF_8));
    }

    /** Walks pages until a short page or a repeated hash. The flag and {@code allowed} both have to be open. */
    public static List<Item> list(boolean allowed, String kind, int size, LabySource.Get http) throws IOException {
        if (!allowed || !enabled() || http == null) {
            return List.of();
        }
        int capped = Math.min(PAGE, Math.max(1, size));
        List<Item> all = new ArrayList<>();
        int offset = 0;
        for (int n = 0; n < MAX_PAGES; n++) {
            List<Item> batch = page(true, kind, capped, offset, http);
            if (batch.isEmpty() || seen(all, batch.get(0).hash())) {
                break;
            }
            all.addAll(batch);
            if (batch.size() < capped) {
                break;
            }
            offset += batch.size();
        }
        return List.copyOf(all);
    }

    private static List<Item> parse(String kind, String json) {
        if (!kindOk(kind) || json == null || json.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> root = JsonValues.object(JsonValues.parse(json));
            if (!(root.get("results") instanceof List<?> results)) {
                return List.of();
            }
            List<Item> items = new ArrayList<>();
            for (Object result : results) {
                if (!(result instanceof Map<?, ?> map)) {
                    continue;
                }
                if (!(map.get("image_hash") instanceof String hash) || !LabySource.hashOk(hash)) {
                    continue;
                }
                String name = map.get("name") instanceof String value ? value : "";
                String tags = map.get("tags") instanceof String value ? value : "";
                List<String> top = new ArrayList<>();
                if (map.get("top_tags") instanceof List<?> values) {
                    for (Object value : values) {
                        if (value instanceof String tag && !tag.isBlank()) {
                            top.add(tag);
                        }
                    }
                }
                long uses = map.get("use_count") instanceof Long count ? count : 0L;
                String label = !name.isBlank() ? name : (!tags.isBlank() ? tags : hash);
                items.add(new Item(kind, hash.toLowerCase(), label, slug(name, top, tags, hash), uses));
            }
            return List.copyOf(items);
        } catch (JsonValues.Bad ignored) {
            return List.of();
        }
    }

    static String slug(String name, List<String> top, String tags, String hash) {
        String source = name == null ? "" : name.trim();
        if (source.isEmpty() && top != null) {
            List<String> kept = new ArrayList<>();
            for (String tag : top) {
                if (tag == null || tag.isBlank()) {
                    continue;
                }
                String lower = tag.trim().toLowerCase();
                boolean folded = false;
                for (int i = 0; i < kept.size(); i++) {
                    String have = kept.get(i).toLowerCase();
                    if (lower.contains(have)) {
                        kept.set(i, tag.trim());
                        folded = true;
                        break;
                    }
                    if (have.contains(lower)) {
                        folded = true;
                        break;
                    }
                }
                if (!folded) {
                    kept.add(tag.trim());
                }
                if (kept.size() == 3) {
                    break;
                }
            }
            source = String.join(" ", kept);
        }
        if (source.isEmpty() && tags != null && !tags.isBlank()) {
            String[] words = tags.trim().split("\\s+");
            int count = Math.min(3, words.length);
            source = String.join(" ", java.util.Arrays.copyOf(words, count));
        }
        String kebab = kebab(source);
        if (!kebab.isEmpty()) {
            return kebab;
        }
        return hash == null || hash.length() < 8 ? "texture" : hash.substring(0, 8).toLowerCase();
    }

    private static String kebab(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean dash = false;
        for (int i = 0; i < raw.length() && out.length() < 48; i++) {
            char c = Character.toLowerCase(raw.charAt(i));
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
                dash = false;
            } else if (!dash && !out.isEmpty()) {
                out.append('-');
                dash = true;
            }
        }
        while (!out.isEmpty() && out.charAt(out.length() - 1) == '-') {
            out.setLength(out.length() - 1);
        }
        return out.toString();
    }

    private static boolean seen(List<Item> items, String hash) {
        for (Item item : items) {
            if (item.hash().equalsIgnoreCase(hash)) {
                return true;
            }
        }
        return false;
    }

    private static boolean kindOk(String kind) {
        return "cape".equals(kind) || "cloak".equals(kind);
    }
}
