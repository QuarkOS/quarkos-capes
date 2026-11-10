package dev.quarkos.capes.client.net;

import dev.quarkos.capes.sim.JsonValues;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Bounded origin-wide circuit delay, shared by metadata, authentication and image requests. */
public final class ServiceBackoff {
    private record State(int failures, long retryAt) { }
    private final Map<String, State> states = new LinkedHashMap<>();
    private final LongSupplier clock;
    public ServiceBackoff() { this(System::currentTimeMillis); }
    public ServiceBackoff(LongSupplier clock) { this.clock = clock; }
    private static String origin(String url) {
        URI uri = URI.create(url);
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }
    public synchronized void before(String url) throws CatalogClient.Failure {
        State state = states.get(origin(url));
        if (state != null && state.retryAt > clock.getAsLong()) throw new CatalogClient.Failure(0, "error");
    }
    public synchronized void failed(String url, String retryAfter) {
        String key = origin(url);
        State old = states.get(key);
        int failures = old == null ? 1 : Math.min(7, old.failures + 1);
        long delay = Math.min(60_000L, 1000L << failures);
        try { delay = Math.max(delay, Math.clamp(Long.parseLong(retryAfter), 0L, 300L) * 1000L); }
        catch (RuntimeException ignored) { }
        if (states.size() >= 128 && !states.containsKey(key)) states.remove(states.keySet().iterator().next());
        states.put(key, new State(failures, clock.getAsLong() + delay));
    }
    public synchronized void success(String url) { states.remove(origin(url)); }
    public static void validateJson(byte[] bytes) throws CatalogClient.Failure {
        try {
            Object value = JsonValues.parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) { throw new CatalogClient.Failure(0, "error"); }
    }
    public static void validateContract(String url, byte[] bytes) throws CatalogClient.Failure {
        validateJson(bytes);
        Map<String, Object> doc = JsonValues.object(JsonValues.parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)));
        String path = URI.create(url).getPath();
        boolean valid = true;
        if (path.endsWith("/v1/catalog")) valid = doc.get("items") instanceof java.util.List<?>;
        else if (path.contains("/v1/catalog/")) valid = CatalogClient.item(doc) != null;
        else if (path.endsWith("/v1/me/wearing")) valid = doc.containsKey("wearing") && (doc.get("wearing") == null || CatalogClient.item(doc.get("wearing")) != null);
        else if (path.endsWith("/v1/players/wearing")) valid = doc.get("players") instanceof java.util.List<?>;
        else if (path.endsWith("/v1/auth/challenge")) valid = doc.get("serverId") instanceof String;
        else if (path.endsWith("/v1/auth/complete")) valid = doc.get("token") instanceof String;
        else if (path.endsWith("/v1/link/claim")) valid = doc.get("linked") instanceof Boolean;
        else if (path.endsWith("/v1/gallery")) valid = doc.get("items") instanceof java.util.List<?> || doc.get("entries") instanceof java.util.List<?>;
        else if (path.endsWith("/v1/capes")) valid = doc.get("capes") instanceof Map<?, ?>;
        if (!valid) throw new CatalogClient.Failure(0, "error");
    }
    public static boolean png(byte[] bytes) {
        return bytes.length >= 8 && bytes[0] == (byte)137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71
                && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
    }
}
