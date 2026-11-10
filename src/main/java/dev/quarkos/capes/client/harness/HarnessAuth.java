package dev.quarkos.capes.client.harness;

import dev.quarkos.capes.sim.JsonValues;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Dev complete for the capture harness. Production login does not call this class.
 * The predicate is false unless the flag is exactly {@code true} and the secret is at least 16 characters.
 */
public final class HarnessAuth {
    public record Session(String token, String uuid, String name) {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private HarnessAuth() {
    }

    public static boolean enabled(String flag, String secret) {
        return "true".equals(flag) && secret != null && secret.length() >= 16;
    }

    public static boolean active() {
        return enabled(System.getenv("CAPES_DEV_AUTH"), System.getenv("CAPES_DEV_AUTH_SECRET"));
    }

    public static UUID offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    public static String bearer(UUID uuid, String secret) {
        String id = uuid.toString();
        return "dev1." + id + "." + hex(sign(id, secret));
    }

    public static Session complete(String base, String username) throws Exception {
        if (!active()) {
            throw new IllegalStateException("dev auth is off");
        }
        String secret = System.getenv("CAPES_DEV_AUTH_SECRET");
        UUID uuid = offlineUuid(username);
        String body = "{\"username\":\"" + username + "\",\"serverId\":\"dev-harness\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(trim(base) + "/v1/auth/complete"))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + bearer(uuid, secret))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("dev complete status " + response.statusCode());
        }
        Map<String, Object> doc = JsonValues.object(JsonValues.parse(response.body()));
        String token = doc.get("token") instanceof String value ? value : "";
        if (token.isBlank() || token.startsWith("dev1.")) {
            throw new IllegalStateException("dev complete did not return a session");
        }
        String id = doc.get("uuid") instanceof String value ? value : "";
        if (!uuid.toString().equals(id)) {
            throw new IllegalStateException("dev complete uuid did not match");
        }
        return new Session(token, id, username);
    }

    public static int putGallery(String base, Session session, String id, byte[] png, String title) throws Exception {
        return put(base, session, "/v1/gallery/" + id, png, title);
    }

    public static int putWorn(String base, Session session, byte[] png) throws Exception {
        return put(base, session, "/v1/capes/" + session.uuid(), png, null);
    }

    /** Upload API fixtures are separate from legacy gallery entries on some servers. */
    public static void putCatalogUpload(String base, String bearer, String id, byte[] png) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(trim(base) + "/v1/uploads/" + id))
                .timeout(Duration.ofSeconds(8)).header("Authorization", "Bearer " + bearer)
                .header("Content-Type", "multipart/form-data; boundary=quarkos")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(multipart(png, "Flow catalog cape"))).build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode()/100 != 2) throw new dev.quarkos.capes.client.net.CatalogClient.Failure(response.statusCode(),"error");
    }

    private static int put(String base, Session session, String path, byte[] png, String title) throws Exception {
        if (session.token().startsWith("dev1.")) {
            throw new IllegalStateException("dev bearer is not a session");
        }
        byte[] body = multipart(png, title);
        HttpRequest request = HttpRequest.newBuilder(URI.create(trim(base) + path))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + session.token())
                .header("Content-Type", "multipart/form-data; boundary=quarkos")
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    private static byte[] multipart(byte[] png, String title) {
        String boundary = "quarkos";
        StringBuilder head = new StringBuilder();
        part(head, boundary, "style", "flat");
        part(head, boundary, "animateFrames", "false");
        if (title != null) {
            part(head, boundary, "title", title);
        }
        String fileHead = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"cape.png\"\r\nContent-Type: image/png\r\n\r\n";
        String tail = "\r\n--" + boundary + "--\r\n";
        byte[] prefix = (head + fileHead).getBytes(StandardCharsets.UTF_8);
        byte[] suffix = tail.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[prefix.length + png.length + suffix.length];
        System.arraycopy(prefix, 0, out, 0, prefix.length);
        System.arraycopy(png, 0, out, prefix.length, png.length);
        System.arraycopy(suffix, 0, out, prefix.length + png.length, suffix.length);
        return out;
    }

    private static void part(StringBuilder out, String boundary, String name, String value) {
        out.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n");
        out.append(value).append("\r\n");
    }

    private static byte[] sign(String uuid, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(uuid.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(Character.forDigit((value >> 4) & 0xF, 16));
            out.append(Character.forDigit(value & 0xF, 16));
        }
        return out.toString();
    }

    private static String trim(String base) {
        if (base == null || base.isBlank()) {
            return dev.quarkos.capes.client.net.CapeCalls.DEFAULT_SERVICE_URL;
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
