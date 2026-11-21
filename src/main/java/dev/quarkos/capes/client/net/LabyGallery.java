package dev.quarkos.capes.client.net;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.sim.JsonValues;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The Browse gallery's LabyMod source. The client reads laby.net directly, one page at a time and only
 * while the gallery is open and online, the same way laby.net's own cloak pages load. Nothing is bundled,
 * stored on disk, or sent to any other service, and LabyMod textures can only be tried on locally.
 * Only LabyMod cloaks are listed: laby.net's "cape" list is official Minecraft capes, which this mod
 * does not offer as a gallery.
 */
public final class LabyGallery {
    public static final String SOURCE = "labymod";
    public static final String SEARCH = "https://laby.net/api/v3/search/textures/cloak";
    public static final String TEXTURE = "https://texture.laby.net/";
    public static final String CREDIT = "https://laby.net/cloaks/";
    public static final int PAGE = 20;
    /** Cloak uploads that copy official Minecraft event capes. Not listed (reversible: empty this set). */
    static final Set<String> HIDDEN_TAGS = Set.of("minecon", "mojang", "migrator", "vanilla cape", "optifine");

    private LabyGallery() {
    }

    /** Builds the one request for a page; offset is the cursor. Empty when the kind or style can't match. */
    public static String pageUrl(String kind, String query, String sort, String style, String cursor) {
        if ("cape".equals(kind) || "frames".equals(style)) return "";
        int offset = 0;
        try { if (cursor != null && !cursor.isBlank()) offset = Math.max(0, Integer.parseInt(cursor)); }
        catch (NumberFormatException ignored) { return ""; }
        String order = "newest".equals(sort) ? "latest" : "most_used";
        String url = SEARCH + "?order=" + order + "&size=" + PAGE + "&offset=" + offset;
        if (query != null && !query.isBlank()) url += "&input=" + URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        return url;
    }

    /** Parses laby.net's search JSON into Browse items. The next cursor is null on a short page. */
    public static CatalogClient.Page parse(String json, String cursor, String sort) {
        int offset = 0;
        try { if (cursor != null && !cursor.isBlank()) offset = Integer.parseInt(cursor); } catch (NumberFormatException ignored) { }
        List<CatalogClient.Item> items = new ArrayList<>();
        int raw = 0;
        try {
            Map<String, Object> root = JsonValues.object(JsonValues.parse(json));
            if (root.get("results") instanceof List<?> results) {
                for (Object result : results) {
                    raw++;
                    if (!(result instanceof Map<?, ?> map)) continue;
                    if (!(map.get("image_hash") instanceof String hash) || !LabySource.hashOk(hash)) continue;
                    String tags = map.get("tags") instanceof String value ? value : "";
                    String name = map.get("name") instanceof String value ? value : "";
                    if (hidden(name + " " + tags)) continue;
                    List<String> top = new ArrayList<>();
                    if (map.get("top_tags") instanceof List<?> values) for (Object value : values) {
                        if (value instanceof String tag && !tag.isBlank() && top.size() < 3) top.add(tag.trim());
                    }
                    String title = !name.isBlank() ? name : !top.isEmpty() ? String.join(" ", top)
                            : !tags.isBlank() ? tags : hash.substring(0, 8);
                    String id = hash.toLowerCase(Locale.ROOT);
                    items.add(new CatalogClient.Item(SOURCE, "cloak:" + id, "cloak", title, "", "flat",
                            false, 1, 64, 32, new int[] {100}, TEXTURE + id + ".png", "", null, false));
                }
            }
        } catch (JsonValues.Bad bad) {
            return new CatalogClient.Page(List.of(), null, Map.of(SOURCE, "unavailable"));
        }
        if ("name".equals(sort)) items.sort((a, b) -> a.title().compareToIgnoreCase(b.title()));
        String next = raw >= PAGE ? Integer.toString(offset + raw) : null;
        return new CatalogClient.Page(List.copyOf(items), next, Map.of(SOURCE, "available"));
    }

    static boolean hidden(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String tag : HIDDEN_TAGS) if (lower.contains(tag)) return true;
        return false;
    }

    /** laby.net page for one listed cloak, shown as the credit link. */
    public static String creditUrl(CatalogClient.Item item) {
        String id = item.id().startsWith("cloak:") ? item.id().substring(6) : item.id();
        return CREDIT + id;
    }

    public static void page(CatalogClient.Query query, Consumer<CatalogClient.Page> ok, Consumer<String> fail) {
        if (ClientSettings.get().localOnly) { fail.accept(SiteApi.message("offline")); return; }
        String url = pageUrl(query.kind(), query.q(), query.sort(), query.style(), query.cursor());
        if (url.isEmpty()) {
            ok.accept(new CatalogClient.Page(List.of(), null, Map.of(SOURCE, "available")));
            return;
        }
        if (!CapeHttp.execute(() -> {
            CatalogClient.Page page;
            String error = null;
            try {
                LabySource.Hit hit = LabySource.exchange(url, "gallery", CapeHttp::labyExchange);
                if (hit == null || hit.status() == 429) throw new CatalogClient.Failure(429, "retry");
                if (hit.status() / 100 != 2 || hit.body() == null) throw new CatalogClient.Failure(hit.status(), "error");
                page = parse(new String(hit.body(), StandardCharsets.UTF_8), query.cursor(), query.sort());
            } catch (Exception e) {
                page = null;
                error = e instanceof CatalogClient.Failure f ? SiteApi.message(f.key) : SiteApi.message("error");
            }
            CatalogClient.Page done = page;
            String message = error;
            Minecraft.getInstance().execute(() -> {
                if (ClientSettings.get().localOnly) fail.accept(SiteApi.message("offline"));
                else if (done == null) fail.accept(message);
                else ok.accept(done);
            });
        })) fail.accept(SiteApi.message("busy"));
    }

    /** Memory-only texture read, converted from LabyMod's cloak net into a vanilla cape atlas. */
    public static void image(CatalogClient.Item item, Consumer<byte[]> ok, Consumer<String> fail) {
        if (ClientSettings.get().localOnly) { fail.accept(SiteApi.message("offline")); return; }
        String hash = item.id().startsWith("cloak:") ? item.id().substring(6) : "";
        if (!LabySource.hashOk(hash)) { fail.accept(SiteApi.message("missing")); return; }
        if (!CapeHttp.download(() -> {
            byte[] png = null;
            try {
                LabySource.Hit hit = LabySource.exchange(TEXTURE + hash + ".png", "gallery-texture", CapeHttp::labyExchange);
                if (hit != null && hit.status() / 100 == 2 && hit.body() != null) png = LabySource.present(hit.body());
            } catch (IOException ignored) {
                png = null;
            }
            byte[] shown = png;
            Minecraft.getInstance().execute(() -> {
                if (ClientSettings.get().localOnly) fail.accept(SiteApi.message("offline"));
                else if (shown == null) fail.accept(SiteApi.message("image_error"));
                else ok.accept(shown);
            });
        })) fail.accept(SiteApi.message("busy"));
    }
}
