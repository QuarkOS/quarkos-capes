package dev.quarkos.capes.client.net;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.LocalFiles;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.render.CapeTextures;
import dev.quarkos.capes.client.render.PlayerFacts;
import dev.quarkos.capes.sim.JsonValues;
import net.minecraft.client.Minecraft;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Talks to our service and the public cape APIs. Local-only returns before any request is built. */
public final class CapeHttp {
    public static int sent;
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final MediaType FORM = MediaType.parse("multipart/form-data; boundary=quarkos");
    private static final RefreshGate REFRESH = new RefreshGate();
    private static final Map<UUID, Long> ASSET_ATTEMPTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<UUID, Long> PROVIDER_ATTEMPTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<UUID> ASSETS_PENDING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<UUID> PROVIDERS_PENDING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static String sourceConfig = "";
    private static final java.util.concurrent.ThreadPoolExecutor EXEC = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(64), task -> {
        Thread thread = new Thread(task, "quarkos-capes");
        thread.setDaemon(true);
        return thread;
    }, new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    private static final java.util.concurrent.ThreadPoolExecutor DOWNLOADS = new java.util.concurrent.ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(128), task -> {
        Thread thread = new Thread(task, "quarkos-capes-download"); thread.setDaemon(true); return thread;
    }, new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();
    private static int tick;
    /** Session token for our service only. Never logged and never sent to Mojang. */
    private static String bearer;
    /** Why the last login stopped. No token and no response body. */
    public static String authNote = "";

    /** Production login never treats dev auth as on, including when a harness flag is present. */
    public static boolean productionDevAuth() {
        return false;
    }

    /** A Mojang access token. A blank, placeholder, or dev bearer cannot start hasJoined. */
    public static boolean mojangSession(String token) {
        return token != null && !token.isBlank() && !"0".equals(token) && !token.startsWith("dev1.");
    }

    private CapeHttp() {
    }

    private static boolean execute(Runnable task) {
        try { EXEC.execute(task); return true; }
        catch (java.util.concurrent.RejectedExecutionException busy) { return false; }
    }

    private static boolean download(Runnable task) {
        try { DOWNLOADS.execute(task); return true; }
        catch (java.util.concurrent.RejectedExecutionException busy) { return false; }
    }

    public static void tick(Minecraft client) {
        ClientSettings settings = ClientSettings.get();
        if (client.level == null) {
            ASSET_ATTEMPTS.clear(); PROVIDER_ATTEMPTS.clear();
            return;
        }
        if (settings.localOnly || !settings.showOthers) {
            return;
        }
        tick++;
        if (tick % 40 != 0) {
            return;
        }
        ASSET_ATTEMPTS.keySet().removeIf(id -> PlayerFacts.byUuid(id) == null);
        PROVIDER_ATTEMPTS.keySet().removeIf(id -> PlayerFacts.byUuid(id) == null);
        String config = settings.baseUrl + ":" + settings.enableOptifine + settings.enableLabyMod
                + settings.enableMinecraftCapesMod + settings.enableCosmetica + settings.enableCloaksPlus;
        if (!config.equals(sourceConfig)) {
            sourceConfig = config;
            ASSET_ATTEMPTS.clear(); PROVIDER_ATTEMPTS.clear();
            CapeBook.clearPlayers();
        }
        if (!REFRESH.begin()) return;
        List<PlayerFacts.Fact> facts = new ArrayList<>();
        for (var player : client.level.players()) {
            PlayerFacts.Fact fact = PlayerFacts.byUuid(player.getUUID());
            if (fact != null && client.player != player) {
                facts.add(fact);
            }
        }
        if (facts.isEmpty()) { REFRESH.finish(); return; }
        if (!execute(() -> {
            try { refresh(settings, facts); }
            finally { REFRESH.finish(); }
        })) REFRESH.finish();
    }

    public static void gallery(String query, String style, int limit, String cursor, Consumer<GalleryPages.Page> ok, Consumer<String> fail) {
        if (ClientSettings.get().localOnly) {
            fail.accept(message("offline")); return;
        }
        if (!execute(() -> {
            try {
                Reply response = send(CapeCalls.Kind.GALLERY_LIST, null, query, style, limit, cursor, null);
                if (response == null || response.status / 100 != 2) {
                    Minecraft.getInstance().execute(() -> fail.accept(message("gallery_unreachable")));
                    return;
                }
                GalleryPages.Page page = GalleryPages.page(response.text());
                Minecraft.getInstance().execute(() -> ok.accept(page));
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> fail.accept(message("gallery_unreachable")));
            }
        })) fail.accept(message("busy"));
    }

    public static void open(String id, Consumer<Opened> ok, Consumer<String> fail) {
        if (ClientSettings.get().localOnly) {
            fail.accept(message("offline")); return;
        }
        if (!execute(() -> {
            try {
                Reply meta = send(CapeCalls.Kind.GALLERY_ONE, id, "", "", 12, "", null);
                if (meta == null || meta.status / 100 != 2) {
                    Minecraft.getInstance().execute(() -> fail.accept(message("cape_unavailable")));
                    return;
                }
                GalleryPages.Detail detail = GalleryPages.detail(meta.text());
                Reply asset = sendBytes(CapeCalls.Kind.GALLERY_ASSET, id);
                if (asset == null || asset.status / 100 != 2) {
                    Minecraft.getInstance().execute(() -> fail.accept(message("cape_unavailable")));
                    return;
                }
                Minecraft.getInstance().execute(() -> ok.accept(new Opened(detail, asset.bytes)));
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> fail.accept(message("cape_unavailable")));
            }
        })) fail.accept(message("busy"));
    }

    public static void equip(GalleryPages.Detail detail, byte[] png) {
        OwnerCape cape = new OwnerCape();
        cape.style = detail.style() == null ? "flat" : detail.style();
        cape.animateFrames = detail.animateFrames();
        cape.frameCount = Math.max(1, detail.frameCount());
        cape.frameWidth = detail.frameWidth();
        cape.frameHeight = detail.frameHeight();
        cape.frameDelayMs = detail.frameDelayMs();
        cape.modelJson = detail.modelJson();
        cape.png = png;
        OwnerCape.set(cape);
        ClientSettings.get().equippedGalleryId = detail.id();
    }

    public static void publish(Consumer<String> status) {
        if (ClientSettings.get().localOnly) { status.accept(message("offline")); return; }
        if (ClientSettings.get().equippedGalleryId != null) { status.accept(message("gallery_copy")); return; }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) { status.accept(message("no_player")); return; }
        UUID self = client.player.getUUID();
        String name = client.getUser().getName();
        String token = client.getUser().getAccessToken();
        OwnerCape cape = OwnerCape.get().copy();
        if (!execute(() -> {
            try {
                if (!login(name, self, token)) {
                    String why = authNote;
                    Minecraft.getInstance().execute(() -> status.accept(why));
                    return;
                }
                Reply response = send(CapeCalls.Kind.GALLERY_PUT, self.toString(), "", "", 12, "", multipart(cape));
                logout();
                String note = response != null && response.status / 100 == 2 ? message("published") : message("publish_failed");
                Minecraft.getInstance().execute(() -> status.accept(note));
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> status.accept(message("publish_failed")));
            }
        })) status.accept(message("busy"));
    }

    public static void unpublish(Consumer<String> status) {
        if (ClientSettings.get().localOnly) { status.accept(message("offline")); return; }
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) { status.accept(message("no_player")); return; }
        UUID self = client.player.getUUID();
        String name = client.getUser().getName();
        String token = client.getUser().getAccessToken();
        if (!execute(() -> {
            try {
                if (!login(name, self, token)) {
                    Minecraft.getInstance().execute(() -> status.accept(message("unpublish_signin")));
                    return;
                }
                Reply response = send(CapeCalls.Kind.GALLERY_DELETE, self.toString(), "", "", 12, "", null);
                logout();
                String note = response != null && (response.status == 204 || response.status / 100 == 2) ? message("unpublished") : message("unpublish_failed");
                Minecraft.getInstance().execute(() -> status.accept(note));
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> status.accept(message("unpublish_failed")));
            }
        })) status.accept(message("busy"));
    }

    public static void report(String id, Consumer<String> status) {
        if (ClientSettings.get().localOnly) { status.accept(message("offline")); return; }
        if (id == null || id.isBlank()) { status.accept(message("select_report")); return; }
        if (!execute(() -> {
            try {
                byte[] body = "{\"reason\":\"Copied without credit.\"}".getBytes(StandardCharsets.UTF_8);
                Reply response = send(CapeCalls.Kind.GALLERY_REPORT, id, "", "", 12, "", body);
                String note = response != null && response.status / 100 == 2
                        ? message("reported")
                        : message("report_failed");
                Minecraft.getInstance().execute(() -> status.accept(note));
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> status.accept(message("report_failed")));
            }
        })) status.accept(message("busy"));
    }

    public record Opened(GalleryPages.Detail detail, byte[] png) {
    }

    private record Reply(int status, byte[] bytes, String contentType) {
        String text() {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static void refresh(ClientSettings settings, List<PlayerFacts.Fact> facts) {
        if (settings.localOnly) {
            return;
        }
        StringBuilder ids = new StringBuilder();
        int count = 0;
        for (PlayerFacts.Fact fact : facts) {
            if (count == 50) {
                break;
            }
            if (count > 0) {
                ids.append(',');
            }
            ids.append(fact.id());
            count++;
        }
        try {
            List<CapeCalls.Planned> planned = CapeCalls.plan(false, CapeCalls.Kind.GET_BATCH, settings.baseUrl, "", facts.get(0).id(), null);
            if (planned.isEmpty()) {
                return;
            }
            String url = settings.baseUrl.replaceAll("/$", "") + "/v1/capes?uuids=" + ids;
            Reply response = raw("GET", URI.create(url), null, false);
            if (response != null && response.status / 100 == 2) {
                Map<String, Object> document = JsonValues.object(JsonValues.parse(response.text()));
                if (document.get("capes") instanceof Map<?, ?> capes) {
                    for (PlayerFacts.Fact fact : facts) {
                        Object value = capes.get(fact.id().toString());
                        if (!(value instanceof Map<?, ?> meta) || !"accepted".equals(meta.get("status"))) CapeBook.remote(fact.id(), null);
                    }
                }
                readBatch(response.text());
            }
        } catch (Exception ignored) {
            // The service is optional. Providers still run.
        }
        for (PlayerFacts.Fact fact : facts) {
            if (PlayerFacts.byUuid(fact.id()) == null) continue;
            if (CapeBook.remote(fact.id()) != null && CapeTextures.get(CapeBook.textureKey(fact.id())) != null) continue;
            if (PROVIDERS_PENDING.add(fact.id()) && !download(() -> {
                try { fetchProvider(settings, fact); } finally { PROVIDERS_PENDING.remove(fact.id()); }
            })) PROVIDERS_PENDING.remove(fact.id());
        }
    }

    private static void readBatch(String body) {
        Map<String, Object> root = JsonValues.object(JsonValues.parse(body));
        if (!(root.get("api") instanceof Long api) || api != 1L) {
            return;
        }
        if (!(root.get("capes") instanceof Map<?, ?> capes)) {
            return;
        }
        for (Map.Entry<?, ?> entry : capes.entrySet()) {
            if (!(entry.getKey() instanceof String id) || !(entry.getValue() instanceof Map<?, ?> raw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) raw;
            String status = map.get("status") instanceof String s ? s : "";
            if (!"accepted".equals(status)) {
                continue;
            }
            UUID uuid;
            try { uuid = UUID.fromString(id); } catch (IllegalArgumentException invalid) { continue; }
            if (PlayerFacts.byUuid(uuid) == null) continue;
            int count = map.get("frameCount") instanceof Long n ? Math.clamp(n.intValue(), 1, 256) : 1;
            CapeBook.remote(uuid, new CapeBook.Remote(
                    map.get("style") instanceof String s ? s : "flat",
                    map.get("animateFrames") instanceof Boolean b && b,
                    count,
                    map.get("frameWidth") instanceof Long n ? n.intValue() : 64,
                    map.get("frameHeight") instanceof Long n ? n.intValue() : 32,
                    delays(map.get("frameDelayMs"), count),
                    map.get("model") instanceof Map<?, ?> ? JsonValues.write(map.get("model")) : null,
                    status
            ));
            long now = System.currentTimeMillis();
            Long last = ASSET_ATTEMPTS.get(uuid);
            if ((last == null || now - last >= 60_000L) && ASSETS_PENDING.add(uuid)) {
                ASSET_ATTEMPTS.put(uuid, now);
                if (!download(() -> {
                    try { downloadAsset(uuid); } finally { ASSETS_PENDING.remove(uuid); }
                })) ASSETS_PENDING.remove(uuid);
            }
        }
    }

    private static void downloadAsset(UUID uuid) {
        if (ClientSettings.get().localOnly) {
            return;
        }
        try {
            Reply response = sendBytes(CapeCalls.Kind.GET_ASSET, uuid.toString());
            if (response == null || response.status / 100 != 2) {
                return;
            }
            byte[] png = response.bytes;
            Minecraft.getInstance().execute(() -> {
                if (!ClientSettings.get().localOnly && PlayerFacts.byUuid(uuid) != null)
                    CapeTextures.upload(CapeBook.textureKey(uuid), png, false, false);
            });
        } catch (Exception ignored) {
            // Missing assets stay on the vanilla cape.
        }
    }

    private static void fetchProvider(ClientSettings settings, PlayerFacts.Fact fact) {
        if (settings.localOnly || !settings.showOthers || PlayerFacts.byUuid(fact.id()) == null) return;
        long now = System.currentTimeMillis();
        Long last = PROVIDER_ATTEMPTS.putIfAbsent(fact.id(), now);
        if (last != null && now - last < 60_000L) return;
        PROVIDER_ATTEMPTS.put(fact.id(), now);
        for (String provider : CapeCalls.PROVIDERS) {
            if (!settings.providerEnabled(provider)) {
                continue;
            }
            try {
                if ("labymod".equals(provider)) {
                    byte[] png = LabySource.worn(true, fact.id(), LocalFiles.folder(), CapeHttp::labyHit);
                    if (png != null) {
                        Minecraft.getInstance().execute(() -> {
                            if (!providerAllowed(fact.id(), provider)) return;
                            CapeTextures.Image stored = CapeTextures.upload("provider/" + fact.id(), png, true, false, true);
                            if (stored != null) {
                                CapeBook.provider(fact.id(), "provider/" + fact.id(), provider);
                            }
                        });
                        return;
                    }
                    continue;
                }
                List<CapeCalls.Planned> planned = CapeCalls.plan(false, CapeCalls.Kind.PROVIDER, settings.baseUrl, fact.name(), fact.id(), provider);
                if (planned.isEmpty()) {
                    return;
                }
                Reply response = raw("GET", URI.create(planned.get(0).url()), null, false);
                if (response == null || response.status / 100 != 2) {
                    continue;
                }
                byte[] body = response.bytes;
                String type = response.contentType == null ? "" : response.contentType;
                if (type.contains("json") || looksJson(body)) {
                    ProviderDocs.Link link = ProviderDocs.read(provider, new String(body, StandardCharsets.UTF_8));
                    if (link == null) {
                        continue;
                    }
                    Reply image = raw("GET", URI.create(link.url()), null, false);
                    if (image == null || image.status / 100 != 2) {
                        continue;
                    }
                    byte[] png = image.bytes;
                    boolean animated = link.animated();
                    Minecraft.getInstance().execute(() -> {
                        if (!providerAllowed(fact.id(), provider)) return;
                        CapeTextures.Image stored = CapeTextures.upload("provider/" + fact.id(), png, animated, false);
                        if (stored != null) {
                            CapeBook.provider(fact.id(), "provider/" + fact.id(), provider);
                        }
                    });
                    return;
                }
                Minecraft.getInstance().execute(() -> {
                    if (!providerAllowed(fact.id(), provider)) return;
                    CapeTextures.Image stored = CapeTextures.upload("provider/" + fact.id(), body, false, false);
                    if (stored != null) {
                        CapeBook.provider(fact.id(), "provider/" + fact.id(), provider);
                    }
                });
                return;
            } catch (Exception ignored) {
                // Try the next provider.
            }
        }
    }

    private static boolean providerAllowed(UUID id, String provider) {
        ClientSettings settings = ClientSettings.get();
        return !settings.localOnly && settings.showOthers && settings.providerEnabled(provider) && PlayerFacts.byUuid(id) != null;
    }

    private static String message(String key) {
        return net.minecraft.network.chat.Component.translatable("text.quarkos_capes.status." + key).getString();
    }

    private static boolean login(String name, UUID uuid, String token) throws Exception {
        authNote = message("publish_signin");
        if (ClientSettings.get().localOnly) {
            authNote = message("publish_offline");
            return false;
        }
        if (!mojangSession(token)) {
            authNote = message("no_token");
            return false;
        }
        Reply challenge = send(CapeCalls.Kind.CHALLENGE, null, "", "", 12, "", new byte[0]);
        if (challenge == null || challenge.status / 100 != 2) {
            authNote = message("no_challenge");
            return false;
        }
        Map<String, Object> doc = JsonValues.object(JsonValues.parse(challenge.text()));
        if (!(doc.get("serverId") instanceof String serverId)) {
            authNote = message("no_challenge");
            return false;
        }
        String rawId = uuid.toString().replace("-", "");
        String join = "{\"accessToken\":\"" + jsonEscape(token) + "\",\"selectedProfile\":\"" + rawId + "\",\"serverId\":\"" + jsonEscape(serverId) + "\"}";
        Reply joined = raw("POST", URI.create("https://sessionserver.mojang.com/session/minecraft/join"), join.getBytes(StandardCharsets.UTF_8), false);
        if (joined == null || joined.status / 100 != 2) {
            authNote = message("join_failed");
            return false;
        }
        String complete = "{\"username\":\"" + jsonEscape(name) + "\",\"serverId\":\"" + jsonEscape(serverId) + "\"}";
        Reply done = send(CapeCalls.Kind.COMPLETE, null, "", "", 12, "", complete.getBytes(StandardCharsets.UTF_8));
        if (done == null || done.status / 100 != 2) {
            authNote = message("session_failed");
            return false;
        }
        Map<String, Object> session = JsonValues.object(JsonValues.parse(done.text()));
        bearer = session.get("token") instanceof String value ? value : null;
        if (bearer == null) {
            authNote = message("no_session");
        }
        return bearer != null;
    }

    private static void logout() {
        try {
            send(CapeCalls.Kind.DELETE_SESSION, null, "", "", 12, "", null);
        } catch (Exception ignored) {
            // The session expires on its own.
        }
        bearer = null;
    }

    private static Reply send(CapeCalls.Kind kind, String galleryId, String query, String style, int limit, String cursor, byte[] body) throws Exception {
        if (ClientSettings.get().localOnly) {
            return null;
        }
        List<CapeCalls.Planned> planned = CapeCalls.plan(false, kind, ClientSettings.get().baseUrl, "", null, null, galleryId, query, style, limit, cursor);
        if (planned.isEmpty()) {
            return null;
        }
        boolean auth = kind == CapeCalls.Kind.GALLERY_PUT || kind == CapeCalls.Kind.GALLERY_DELETE || kind == CapeCalls.Kind.DELETE_SESSION
                || kind == CapeCalls.Kind.PUT || kind == CapeCalls.Kind.DELETE;
        return raw(planned.get(0).method(), URI.create(planned.get(0).url()), body, auth);
    }

    private static Reply sendBytes(CapeCalls.Kind kind, String id) throws Exception {
        if (ClientSettings.get().localOnly) {
            return null;
        }
        List<CapeCalls.Planned> planned;
        if (kind == CapeCalls.Kind.GALLERY_ASSET) {
            planned = CapeCalls.plan(false, kind, ClientSettings.get().baseUrl, "", null, null, id, "", "", 12, "");
        } else {
            UUID uuid = UUID.fromString(id);
            planned = CapeCalls.plan(false, kind, ClientSettings.get().baseUrl, "", uuid, null);
        }
        if (planned.isEmpty()) {
            return null;
        }
        return raw(planned.get(0).method(), URI.create(planned.get(0).url()), null, false);
    }

    public static LabySource.Hit labyExchange(String url) throws IOException {
        return labyHit(url);
    }

    private static LabySource.Hit labyHit(String url) throws IOException {
        if (ClientSettings.get().localOnly) {
            return new LabySource.Hit(0, new byte[0], "", -1, -1, 0);
        }
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", CapeCalls.USER_AGENT)
                .header("Accept", "image/png, application/json")
                .get()
                .build();
        sent++;
        try (Response response = HTTP.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            byte[] bytes = responseBody == null ? new byte[0] : responseBody.bytes();
            return new LabySource.Hit(
                    response.code(),
                    bytes,
                    response.header("Content-Type", ""),
                    headerInt(response.header("X-RateLimit-Limit")),
                    headerInt(response.header("X-RateLimit-Remaining")),
                    headerReset(response.header("X-RateLimit-Reset"))
            );
        }
    }

    private static int headerInt(String value) {
        if (value == null || value.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long headerReset(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 && parsed < 10_000_000_000L ? parsed * 1000L : parsed;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Reply raw(String method, URI uri, byte[] body, boolean auth) throws Exception {
        if (ClientSettings.get().localOnly) {
            return null;
        }
        Request.Builder builder = new Request.Builder()
                .url(uri.toString())
                .header("User-Agent", CapeCalls.USER_AGENT)
                .header("Accept", "application/json, image/png");
        String header = CapeCalls.sessionHeader(auth ? bearer : null);
        if (header != null && uri.getHost() != null && !uri.getHost().contains("mojang")) {
            builder.header("Authorization", header);
        }
        if ("DELETE".equals(method)) {
            builder.delete();
        } else if (body == null || "GET".equals(method)) {
            builder.get();
        } else {
            MediaType type = "PUT".equals(method) ? FORM : JSON;
            builder.method(method, RequestBody.create(type, body));
        }
        sent++;
        try (Response response = HTTP.newCall(builder.build()).execute()) {
            ResponseBody responseBody = response.body();
            byte[] bytes = responseBody == null ? new byte[0] : responseBody.bytes();
            return new Reply(response.code(), bytes, response.header("Content-Type", ""));
        }
    }

    private static byte[] multipart(OwnerCape cape) {
        String boundary = "quarkos";
        StringBuilder head = new StringBuilder();
        part(head, boundary, "style", cape.style);
        part(head, boundary, "animateFrames", Boolean.toString(cape.animateFrames));
        if (cape.modelJson != null) {
            part(head, boundary, "model", cape.modelJson);
        }
        String fileHead = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"cape.png\"\r\nContent-Type: image/png\r\n\r\n";
        String tail = "\r\n--" + boundary + "--\r\n";
        byte[] prefix = (head + fileHead).getBytes(StandardCharsets.UTF_8);
        byte[] suffix = tail.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[prefix.length + cape.png.length + suffix.length];
        System.arraycopy(prefix, 0, out, 0, prefix.length);
        System.arraycopy(cape.png, 0, out, prefix.length, cape.png.length);
        System.arraycopy(suffix, 0, out, prefix.length + cape.png.length, suffix.length);
        return out;
    }

    private static void part(StringBuilder out, String boundary, String name, String value) {
        out.append("--").append(boundary).append("\r\nContent-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n");
        out.append(value).append("\r\n");
    }

    private static int[] delays(Object value, int count) {
        int n = Math.max(1, count);
        int[] delays = new int[n];
        java.util.Arrays.fill(delays, 100);
        if (value instanceof List<?> list) {
            for (int i = 0; i < Math.min(n, list.size()); i++) {
                if (list.get(i) instanceof Long number) {
                    delays[i] = number.intValue();
                }
            }
        }
        return delays;
    }

    private static boolean looksJson(byte[] body) {
        int i = 0;
        while (i < body.length && body[i] <= ' ') {
            i++;
        }
        return i < body.length && body[i] == '{';
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
