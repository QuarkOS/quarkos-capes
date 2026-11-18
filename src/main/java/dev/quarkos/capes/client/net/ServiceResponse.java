package dev.quarkos.capes.client.net;

import dev.quarkos.capes.sim.ImageLimits;
import dev.quarkos.capes.sim.JsonValues;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Classifies replies from the configured Cape service before callers interpret their data. */
public final class ServiceResponse {
    public enum OfflineReason {
        WRONG_SERVICE("the health endpoint did not identify a Cape service"),
        NOT_FOUND("HTTP 404"),
        SERVER_ERROR("server error"),
        RATE_LIMITED("rate limited"),
        CONTENT_TYPE("unexpected content type"),
        INVALID_JSON("invalid JSON"),
        INVALID_RESPONSE("unexpected response"),
        TIMEOUT("request timed out"),
        CONNECTION("connection failed");

        private final String description;

        OfflineReason(String description) { this.description = description; }

        @Override public String toString() { return description; }
    }

    private ServiceResponse() { }

    /** A null reason means the response can be used, including recognized API errors. */
    public static OfflineReason classify(String method, String path, int status, String contentType,
                                         byte[] bytes, boolean image, boolean conditional) {
        boolean health = path.endsWith("/v1/health");
        if (status == 404) return health ? OfflineReason.WRONG_SERVICE : OfflineReason.NOT_FOUND;
        if (status == 429) return OfflineReason.RATE_LIMITED;
        if (status >= 500) return OfflineReason.SERVER_ERROR;
        OfflineReason unexpected = health ? OfflineReason.WRONG_SERVICE : OfflineReason.INVALID_RESPONSE;
        if (status == 304) return conditional ? null : unexpected;
        if (status == 204) return method.equals("DELETE") ? null : unexpected;
        if (status < 200 || status >= 300 && status < 400) return unexpected;

        String type = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (image && status < 300) {
            if (!type.equals("image/png")) return OfflineReason.CONTENT_TYPE;
            return ImageLimits.png(bytes) ? null : OfflineReason.INVALID_RESPONSE;
        }
        if (!type.equals("application/json") && !(type.startsWith("application/") && type.endsWith("+json"))) {
            return health ? OfflineReason.WRONG_SERVICE : OfflineReason.CONTENT_TYPE;
        }
        Map<String, Object> doc;
        try {
            doc = parse(bytes);
        } catch (RuntimeException invalid) {
            return health ? OfflineReason.WRONG_SERVICE : OfflineReason.INVALID_JSON;
        }
        if (!Long.valueOf(1).equals(doc.get("api"))) return unexpected;
        if (health) return status == 200 && Boolean.TRUE.equals(doc.get("ok")) ? null : unexpected;
        if (status >= 400) return doc.get("error") instanceof String ? null : unexpected;
        return validFields(path, doc) ? null : unexpected;
    }

    static OfflineReason transportFailure(IOException error) {
        return error instanceof InterruptedIOException || error instanceof HttpTimeoutException
                ? OfflineReason.TIMEOUT : OfflineReason.CONNECTION;
    }

    /** The catalog transport is injectable, so also check its payloads before caching them. */
    static void validateContract(String url, byte[] bytes) throws CatalogClient.Failure {
        try {
            if (validFields(URI.create(url).getPath(), parse(bytes))) return;
        } catch (RuntimeException invalid) { }
        throw new CatalogClient.Failure(0, "error");
    }

    private static Map<String, Object> parse(byte[] bytes) {
        return JsonValues.object(JsonValues.parse(new String(bytes, StandardCharsets.UTF_8)));
    }

    private static boolean validFields(String path, Map<String, Object> doc) {
        if (path.endsWith("/v1/catalog")) return doc.get("items") instanceof List<?> items && items.stream().allMatch(ServiceResponse::item);
        if (path.contains("/v1/catalog/")) return item(doc) && CatalogClient.item(doc) != null;
        if (path.endsWith("/v1/me/wearing")) return wearing(doc);
        if (path.endsWith("/v1/players/wearing")) return doc.get("players") instanceof List<?> players
                && players.stream().allMatch(row -> row instanceof Map<?, ?> player && uuid(player.get("uuid")) && wearing(player));
        if (path.endsWith("/v1/auth/challenge")) return nonBlank(doc.get("serverId"));
        if (path.endsWith("/v1/auth/complete")) return nonBlank(doc.get("token"));
        if (path.endsWith("/v1/link/claim")) return doc.get("linked") instanceof Boolean;
        if (path.endsWith("/v1/gallery")) {
            Object rows = doc.containsKey("items") ? doc.get("items") : doc.get("entries");
            return rows instanceof List<?> list
                    && list.stream().allMatch(row -> row instanceof Map<?, ?> item && nonBlank(item.get("id")));
        }
        if (path.contains("/v1/gallery/") && !path.endsWith("/report")) return nonBlank(doc.get("id"));
        if (path.endsWith("/v1/capes")) return doc.get("capes") instanceof Map<?, ?>;
        return true;
    }

    private static boolean nonBlank(Object value) { return value instanceof String string && !string.isBlank(); }

    private static boolean item(Object value) {
        return value instanceof Map<?, ?> row && nonBlank(row.get("id")) && nonBlank(row.get("source"))
                && nonBlank(row.get("textureUrl"));
    }

    private static boolean wearing(Map<?, ?> doc) {
        return doc.containsKey("wearing") && (doc.get("wearing") == null || item(doc.get("wearing")));
    }

    private static boolean uuid(Object value) {
        if (!(value instanceof String string)) return false;
        try { return UUID.fromString(string).toString().equalsIgnoreCase(string); }
        catch (IllegalArgumentException invalid) { return false; }
    }
}
