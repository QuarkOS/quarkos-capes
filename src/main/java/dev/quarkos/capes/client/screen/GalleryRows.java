package dev.quarkos.capes.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Which gallery rows are on screen, and the shared-cape count line. */
public final class GalleryRows {
    public record Local(String name, String author) {
    }

    public record Shared(String id, String name, String author) {
    }

    public record Row(String name, String author, String tag, String id) {
    }

    private GalleryRows() {
    }

    public static String sharedCapes(int count) {
        return count + " shared " + (count == 1 ? "cape" : "capes");
    }

    public static String searchNote(String query, int count) {
        return "Search for \"" + query + "\" returned " + sharedCapes(count) + ".";
    }

    /**
     * A non-empty query keeps only rows whose name or id contains it.
     * The same cape is listed once. A shared hit wins over a local sample with the same name.
     */
    public static List<Row> visible(String query, List<Local> locals, List<Shared> shared) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Row> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (!needle.isEmpty()) {
            for (Shared item : shared) {
                if (matches(item.name(), needle) || matches(item.id(), needle)) {
                    add(rows, seen, item.name(), item.author(), "shared", item.id());
                }
            }
            for (Local local : locals) {
                if (matches(local.name(), needle)) {
                    add(rows, seen, local.name(), local.author(), "local", null);
                }
            }
        } else {
            for (Local local : locals) {
                add(rows, seen, local.name(), local.author(), "local", null);
            }
            for (Shared item : shared) {
                add(rows, seen, item.name(), item.author(), "shared", item.id());
            }
        }
        return List.copyOf(rows);
    }

    private static boolean matches(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static void add(List<Row> rows, Set<String> seen, String name, String author, String tag, String id) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || !seen.add(key)) {
            return;
        }
        rows.add(new Row(name.trim(), author == null ? "" : author, tag, id));
    }
}
