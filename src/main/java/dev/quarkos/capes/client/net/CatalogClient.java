package dev.quarkos.capes.client.net;

import dev.quarkos.capes.sim.JsonValues;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;

/** HTTP contract without Minecraft dependencies; transport and clock are injectable for offline tests. */
public final class CatalogClient {
    public record Reply(int status, byte[] bytes, String etag, String retryAfter) {
        public String text() { return new String(bytes, StandardCharsets.UTF_8); }
    }
    @FunctionalInterface public interface Transport {
        Reply send(String method, String url, String bearer, String etag, byte[] body) throws Exception;
    }
    public record Query(String source, String kind, String q, String sort, String style, String tag, String cursor, int limit) {
        public String path() {
            return "/v1/catalog?source=" + encode(source) + "&kind=" + encode(kind) + "&q=" + encode(q)
                    + "&sort=" + encode(sort) + "&style=" + encode(style) + "&tag=" + encode(tag)
                    + "&cursor=" + encode(cursor) + "&limit=" + Math.clamp(limit, 1, 50);
        }
    }
    public record Item(String source, String id, String kind, String title, String author, String style,
                       boolean animateFrames, int frameCount, int frameWidth, int frameHeight, int[] delays,
                       String textureUrl, String sha256, String modelJson) { }
    public record Page(List<Item> items, String nextCursor, Map<String, String> sources) { }
    public static final class Failure extends IOException {
        public final String key;
        public final int status;
        public Failure(int status, String key) { super(key); this.status = status; this.key = key; }
    }
    private record Cached(String etag, Reply reply) { }
    private final Transport transport;
    private final LongSupplier clock;
    private final Map<String, Cached> cache = Collections.synchronizedMap(new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Cached> e) { return size() > 128; }
    });
    private final ConcurrentMap<String, CompletableFuture<Reply>> pending = new ConcurrentHashMap<>();
    private static <T> Map<String,T> bounded() {
        return Collections.synchronizedMap(new LinkedHashMap<>(128,.75f,true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String,T> entry) { return size()>128; }
        });
    }
    private final Map<String, Long> retryAt = bounded();
    private final Map<String, Integer> failures = bounded();
    public CatalogClient(Transport transport) { this(transport, System::currentTimeMillis); }
    public CatalogClient(Transport transport, LongSupplier clock) { this.transport = transport; this.clock = clock; }

    public Reply request(String base, String method, String path, String bearer, Object body) throws Exception {
        String url = base.replaceAll("/+$", "") + path;
        String key = url + "\n" + (bearer == null ? "" : bearer);
        boolean get = method.equals("GET");
        if (retryAt.getOrDefault(key, 0L) > clock.getAsLong()) throw new Failure(429, "retry");
        CompletableFuture<Reply> future = new CompletableFuture<>();
        if (get) {
            CompletableFuture<Reply> existing = pending.putIfAbsent(key, future);
            if (existing != null) {
                try { return existing.get(9, TimeUnit.SECONDS); }
                catch (ExecutionException e) { if (e.getCause() instanceof Exception cause) throw cause; throw e; }
            }
        }
        try {
            Cached saved = get ? cache.get(key) : null;
            Reply reply = transport.send(method, url, bearer, saved == null ? null : saved.etag,
                    body == null ? null : JsonValues.write(body).getBytes(StandardCharsets.UTF_8));
            if (reply.status == 304 && saved != null) reply = saved.reply;
            if (reply.status == 429 || reply.status >= 500) {
                int n = failures.merge(key, 1, Integer::sum);
                long delay = Math.min(60_000L, 1000L << Math.min(6, n));
                try { delay = Math.max(delay, Math.min(300_000L, Long.parseLong(reply.retryAfter) * 1000L)); }
                catch (Exception ignored) { }
                retryAt.put(key, clock.getAsLong() + delay);
            }
            if (reply.status / 100 != 2) throw new Failure(reply.status, errorKey(reply.status));
            if (reply.status != 204) ServiceBackoff.validateContract(url, reply.bytes);
            failures.remove(key); retryAt.remove(key);
            if (get && reply.etag != null && !reply.etag.isBlank()) cache.put(key, new Cached(reply.etag, reply));
            future.complete(reply);
            return reply;
        } catch (Exception e) {
            if (!(e instanceof Failure f && f.status > 0)) {
                int n = failures.merge(key, 1, Integer::sum);
                retryAt.put(key, clock.getAsLong() + Math.min(60_000L, 1000L << Math.min(6, n)));
            }
            future.completeExceptionally(e); throw e;
        }
        finally { if (get) pending.remove(key, future); }
    }
    public Page page(String base, Query query) throws Exception { return page(request(base, "GET", query.path(), null, null).text()); }
    public Item detail(String base, String source, String id) throws Exception {
        return item(JsonValues.parse(request(base, "GET", "/v1/catalog/" + encode(source) + "/" + encode(id), null, null).text()));
    }
    public static Page page(String json) {
        Map<String, Object> doc = JsonValues.object(JsonValues.parse(json));
        List<Item> items = new ArrayList<>();
        if (doc.get("items") instanceof List<?> list) for (Object value : list) { Item item = item(value); if (item != null) items.add(item); }
        Map<String, String> sources = new LinkedHashMap<>();
        if (doc.get("sources") instanceof Map<?, ?> map) map.forEach((k,v) -> { if (k instanceof String a && v instanceof String b) sources.put(a,b); });
        return new Page(List.copyOf(items), string(doc, "nextCursor", null), Map.copyOf(sources));
    }
    public static Item item(Object value) {
        if (!(value instanceof Map<?, ?>)) return null;
        Map<String, Object> map = JsonValues.object(value);
        String id = string(map, "id", ""), source = string(map, "source", "");
        if (id.isBlank() || !(source.equals("ours") || source.equals("labymod"))) return null;
        int count = number(map, "frameCount", 1, 1, 256);
        int[] delays = new int[count]; Arrays.fill(delays, 100);
        if (map.get("frameDelayMs") instanceof List<?> list) for (int i=0; i<Math.min(count,list.size()); i++)
            if (list.get(i) instanceof Number n) delays[i] = Math.clamp(n.intValue(), 50, 1000);
        return new Item(source, id, string(map,"kind","cape"), string(map,"title",id), string(map,"author",""),
                string(map,"style","flat"), Boolean.TRUE.equals(map.get("animateFrames")), count,
                number(map,"frameWidth",64,1,8192), number(map,"frameHeight",32,1,8192), delays,
                string(map,"textureUrl",""), string(map,"sha256",""), map.get("model") instanceof Map<?,?> ? JsonValues.write(map.get("model")) : null);
    }
    public static Item wearing(String json) { return item(JsonValues.object(JsonValues.parse(json)).get("wearing")); }
    public static Map<UUID, Item> players(String json) {
        Map<UUID, Item> out = new LinkedHashMap<>();
        Object values = JsonValues.object(JsonValues.parse(json)).get("players");
        if (values instanceof List<?> list) for (Object value : list) {
            Map<String,Object> map = JsonValues.object(value);
            try { out.put(UUID.fromString(string(map,"uuid","")), item(map.get("wearing"))); }
            catch (IllegalArgumentException ignored) { }
        }
        return out;
    }
    public static List<List<UUID>> chunks(Collection<UUID> ids) {
        List<UUID> unique = new ArrayList<>(new LinkedHashSet<>(ids)); List<List<UUID>> out = new ArrayList<>();
        for (int i=0; i<unique.size(); i+=50) out.add(List.copyOf(unique.subList(i,Math.min(i+50,unique.size()))));
        return List.copyOf(out);
    }
    public static String errorKey(int status) {
        return switch (status) { case 400 -> "invalid"; case 401 -> "signin"; case 403 -> "forbidden"; case 404 -> "missing";
            case 409 -> "conflict"; case 429 -> "retry"; default -> "error"; };
    }
    public static String encode(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String string(Map<String,Object> map,String key,String fallback) { return map.get(key) instanceof String s ? s : fallback; }
    private static int number(Map<String,Object> map,String key,int fallback,int min,int max) { return map.get(key) instanceof Number n ? Math.clamp(n.intValue(),min,max) : fallback; }
}
