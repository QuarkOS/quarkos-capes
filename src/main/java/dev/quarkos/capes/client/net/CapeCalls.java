package dev.quarkos.capes.client.net;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Plans API v1 calls and the read-only provider GETs. Local-only mode returns
 * no request at all, including Mojang session join.
 */
public final class CapeCalls {
    public static final String USER_AGENT = "QuarkOS-Capes/1.0.0";
    public static final String DEFAULT_SERVICE_URL = "http://127.0.0.1:8787";
    public static final String DEFAULT_BASE = DEFAULT_SERVICE_URL;

    public enum Kind {
        HEALTH,
        CHALLENGE,
        MOJANG_JOIN,
        COMPLETE,
        DELETE_SESSION,
        GET_ONE,
        GET_BATCH,
        GET_ASSET,
        PUT,
        DELETE,
        PROVIDER,
        GALLERY_LIST,
        GALLERY_ONE,
        GALLERY_ASSET,
        GALLERY_PUT,
        GALLERY_DELETE,
        GALLERY_REPORT
    }

    public record Planned(Kind kind, String method, String url) {
    }

    private CapeCalls() {
    }

    public static final List<String> PROVIDERS = List.of(
            "optifine", "labymod", "minecraftcapes", "cosmetica", "cloaksplus");

    public static List<Planned> plan(boolean localOnly, Kind kind, String base, String name, UUID uuid, String provider) {
        return plan(localOnly, kind, base, name, uuid, provider, null, "", "", 12, "");
    }

    /**
     * Gallery ids are path segments. Search text, style, limit, and cursor are the list query.
     * Local-only returns nothing for every kind, including gallery and provider reads.
     */
    public static List<Planned> plan(
            boolean localOnly,
            Kind kind,
            String base,
            String name,
            UUID uuid,
            String provider,
            String galleryId,
            String query,
            String style,
            int limit,
            String cursor
    ) {
        if (localOnly) {
            return List.of();
        }
        String root = trim(base);
        String dashed = uuid == null ? "" : uuid.toString();
        String raw = dashed.replace("-", "");
        String url = switch (kind) {
            case HEALTH -> root + "/v1/health";
            case CHALLENGE -> root + "/v1/auth/challenge";
            case MOJANG_JOIN -> "https://sessionserver.mojang.com/session/minecraft/join";
            case COMPLETE -> root + "/v1/auth/complete";
            case DELETE_SESSION -> root + "/v1/auth/session";
            case GET_ONE, DELETE, PUT -> root + "/v1/capes/" + dashed;
            case GET_BATCH -> root + "/v1/capes?uuids=" + dashed;
            case GET_ASSET -> root + "/v1/capes/" + dashed + "/asset";
            case PROVIDER -> providerUrl(provider, name, uuid, raw, dashed);
            case GALLERY_LIST -> root + "/v1/gallery?q=" + enc(query) + "&style=" + enc(style)
                    + "&limit=" + Math.max(1, limit) + "&cursor=" + enc(cursor);
            case GALLERY_ONE, GALLERY_PUT, GALLERY_DELETE -> root + "/v1/gallery/" + enc(galleryId);
            case GALLERY_ASSET -> root + "/v1/gallery/" + enc(galleryId) + "/asset";
            case GALLERY_REPORT -> root + "/v1/gallery/" + enc(galleryId) + "/report";
        };
        String method = switch (kind) {
            case DELETE, DELETE_SESSION, GALLERY_DELETE -> "DELETE";
            case CHALLENGE, MOJANG_JOIN, COMPLETE, GALLERY_REPORT -> "POST";
            case PUT, GALLERY_PUT -> "PUT";
            default -> "GET";
        };
        return List.of(new Planned(kind, method, url));
    }

    /** Builds a request, or returns null when local-only is on. The token is never placed in the URL. */
    public static HttpRequest request(boolean localOnly, String method, URI uri) {
        if (localOnly) {
            return null;
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, image/png");
        if ("GET".equals(method)) {
            builder.GET();
        } else if ("DELETE".equals(method)) {
            builder.DELETE();
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return builder.build();
    }

    /**
     * Session header for a service call. A {@code dev1.} bearer is not a session and is not sent.
     * Null means the production caller must omit Authorization.
     */
    public static String sessionHeader(String token) {
        if (token == null || token.isBlank() || token.startsWith("dev1.")) {
            return null;
        }
        return "Bearer " + token;
    }

    public static String providerUrl(String provider, String name, UUID uuid) {
        String dashed = uuid.toString();
        return providerUrl(provider, name, uuid, dashed.replace("-", ""), dashed);
    }

    private static String providerUrl(String provider, String name, UUID uuid, String raw, String dashed) {
        String safeName = URLEncoder.encode(name == null ? "" : name, StandardCharsets.UTF_8);
        return switch (provider) {
            case "optifine" -> "http://s.optifine.net/capes/" + safeName + ".png";
            case "labymod" -> "https://dl.labymod.net/capes/" + dashed;
            case "minecraftcapes" -> "https://api.minecraftcapes.net/profile/" + raw;
            case "cosmetica" -> "https://api.cosmetica.cc/users/" + dashed + "/cape";
            case "cloaksplus" -> "http://161.35.130.99/capes/" + safeName + ".png";
            default -> throw new IllegalArgumentException("unknown provider");
        };
    }

    /** Equipping a gallery cape is a local copy. It plans no request and does not publish. */
    public static List<Planned> equip() {
        return List.of();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String trim(String base) {
        if (base == null || base.isBlank()) {
            return DEFAULT_SERVICE_URL;
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
