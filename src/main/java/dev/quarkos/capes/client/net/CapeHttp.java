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
    public static volatile int sent;
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
            .connectTimeout(3, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            // HTTP/1.1 only: an HTTP/2 connection to the HTTPS service keeps a non-daemon OkHttp writer thread
            // alive, which trips the client shutdown watchdog on quit (seen on 26.3 against api.emilioschwaiger.com).
            .protocols(List.of(okhttp3.Protocol.HTTP_1_1))
            .build();
    private static final OkHttpClient PRIVATE_HTTP = HTTP.newBuilder().followRedirects(false).followSslRedirects(false).build();
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("quarkos_capes");
    private static final java.util.Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.Set<String> WARNED_INSECURE = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static int tick;
    /** Session token for our service only. Never logged and never sent to Mojang. */
    private static String bearer;
    private static String bearerBase;
    private static UUID bearerPlayer;
    private static long bearerUntil;
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

    public static boolean execute(Runnable task) {
        try { EXEC.execute(task); return true; }
        catch (java.util.concurrent.RejectedExecutionException busy) { return false; }
    }

    public static boolean download(Runnable task) {
        try { DOWNLOADS.execute(task); return true; }
        catch (java.util.concurrent.RejectedExecutionException busy) { return false; }
    }

    /** Called on the bounded service pool, never on the render thread. */
    public static String siteBearer() throws Exception {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) throw new CatalogClient.Failure(401, "signin");
        if (bearer != null && ClientSettings.get().baseUrl.equals(bearerBase)
                && client.player.getUUID().equals(bearerPlayer) && System.currentTimeMillis() < bearerUntil) return bearer;
        if (!login(client.getUser().getName(), client.player.getUUID(), client.getUser().getAccessToken()))
            throw new CatalogClient.Failure(401, "signin");
        return bearer;
    }

    private static final ServiceBackoff HEALTH = new ServiceBackoff();
    private static final ServiceBackoff EXTERNAL = new ServiceBackoff();
    private static final Object PROBE_LOCK = new Object();
    private static final java.util.Set<String> PROBES_PENDING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static volatile String verifiedBase;
    public record ServiceIssue(String url, ServiceResponse.OfflineReason reason) { }

    public static ServiceIssue serviceIssue() {
        ClientSettings settings = ClientSettings.get();
        if (settings.localOnly) return null;
        String base = serviceBase(settings.baseUrl);
        var reason = HEALTH.reason(base);
        return reason == null ? null : new ServiceIssue(base, reason);
    }

    public static void retryService() {
        String base = serviceBase(ClientSettings.get().baseUrl);
        if (HEALTH.retry(base) && base.equals(verifiedBase)) verifiedBase = null;
    }

    public static void checkService() {
        ClientSettings settings = ClientSettings.get();
        if (settings.localOnly) return;
        String base = serviceBase(settings.baseUrl);
        if (base.equals(verifiedBase) || !PROBES_PENDING.add(base)) return;
        if (!execute(() -> {
            try { verifyService(base); }
            catch (CatalogClient.Failure ignored) { }
            finally { PROBES_PENDING.remove(base); }
        })) PROBES_PENDING.remove(base);
    }

    private static String serviceBase(String url) { return url.strip().replaceAll("/+$", ""); }

    public static CatalogClient.Reply siteExchange(String method, String url, String token, String etag, byte[] body) throws Exception {
        String base = serviceBase(ClientSettings.get().baseUrl);
        if (!url.startsWith(base + "/v1/")) throw new CatalogClient.Failure(0, "offline");
        if (token != null && (!ClientSettings.get().baseUrl.equals(bearerBase) || !ServiceUrl.sameOrigin(url, bearerBase)))
            throw new CatalogClient.Failure(401, "signin");
        Request.Builder request = new Request.Builder().url(url).header("User-Agent", CapeCalls.USER_AGENT)
                .header("Accept", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (etag != null) request.header("If-None-Match", etag);
        request.method(method, method.equals("GET") || method.equals("DELETE") ? null
                : RequestBody.create(JSON, body == null ? new byte[0] : body));
        Reply reply = serviceExchange(base, request.build(), token != null || !method.equals("GET"), false);
        if (reply.status == 401 && token != null && token.equals(bearer)) bearer = null;
        return new CatalogClient.Reply(reply.status, reply.bytes, reply.etag, reply.retryAfter);
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
            CapeBook.clearProviders();
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
                    Minecraft.getInstance().execute(() -> fail.accept(message("gallery_unreachable") + (response == null ? "" : " (HTTP " + response.status + ")")));
                    return;
                }
                GalleryPages.Page page = GalleryPages.page(response.text());
                Minecraft.getInstance().execute(() -> ok.accept(page));
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> fail.accept(serviceFailure(e, "gallery_unreachable")));
            }
        })) fail.accept(message("busy"));
    }

    public static void open(String id, Consumer<Opened> ok, Consumer<String> fail) {
        open(id, ok, fail, false);
    }

    public static void openThumbnail(String id, Consumer<Opened> ok, Consumer<String> fail) {
        open(id, ok, fail, true);
    }

    private static void open(String id, Consumer<Opened> ok, Consumer<String> fail, boolean thumbnail) {
        if (ClientSettings.get().localOnly) {
            fail.accept(message("offline")); return;
        }
        Runnable request = () -> {
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
                Minecraft.getInstance().execute(() -> fail.accept(serviceFailure(e, "cape_unavailable")));
            }
        };
        if (!(thumbnail ? download(request) : execute(request))) fail.accept(message("busy"));
    }

    public static void equip(GalleryPages.Detail detail, byte[] png) {
        OwnerCape.set(owner(detail, png));
        ClientSettings.get().equippedGalleryId = detail.id();
    }

    public static OwnerCape owner(GalleryPages.Detail detail, byte[] png) {
        OwnerCape cape = new OwnerCape();
        cape.style = detail.style() == null ? "flat" : detail.style();
        cape.animateFrames = detail.animateFrames();
        cape.frameCount = Math.max(1, detail.frameCount());
        cape.frameWidth = detail.frameWidth();
        cape.frameHeight = detail.frameHeight();
        cape.frameDelayMs = detail.frameDelayMs();
        cape.modelJson = detail.modelJson();
        cape.png = png;
        return cape;
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
                String note = response != null && response.status / 100 == 2 ? message("published") : message("publish_failed") + (response == null ? "" : " (HTTP " + response.status + ")");
                Minecraft.getInstance().execute(() -> status.accept(note));
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> status.accept(serviceFailure(e, "publish_failed")));
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
                String note = response != null && (response.status == 204 || response.status / 100 == 2) ? message("unpublished") : message("unpublish_failed") + (response == null ? "" : " (HTTP " + response.status + ")");
                Minecraft.getInstance().execute(() -> status.accept(note));
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> status.accept(serviceFailure(e, "unpublish_failed")));
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
                Minecraft.getInstance().execute(() -> status.accept(serviceFailure(e, "report_failed")));
            }
        })) status.accept(message("busy"));
    }

    public record Opened(GalleryPages.Detail detail, byte[] png) {
    }

    private record Reply(int status, byte[] bytes, String contentType, String etag, String retryAfter) {
        String text() {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static void refresh(ClientSettings settings, List<PlayerFacts.Fact> facts) {
        if (settings.localOnly) {
            return;
        }
        // Keep legacy worn uploads as a fallback. A resolved site reference takes priority.
        String base = settings.baseUrl;
        int generation = SiteApi.generation();
        List<UUID> legacy = facts.stream().map(PlayerFacts.Fact::id).filter(id -> !SiteApi.hasReference(id)).toList();
        for (List<UUID> chunk : CatalogClient.chunks(legacy)) {
            try {
                String ids = String.join(",", chunk.stream().map(UUID::toString).toList());
                Reply response = raw("GET", URI.create(serviceBase(base) + "/v1/capes?uuids=" + ids),
                        null, false, serviceBase(base), false);
                if (response != null && response.status / 100 == 2) {
                    String body = response.text();
                    Map<UUID, CapeBook.Remote> batch = batchMetadata(body);
                    Minecraft.getInstance().execute(() -> {
                        ClientSettings current = ClientSettings.get();
                        if (generation != SiteApi.generation() || current.localOnly || !current.showOthers || !base.equals(current.baseUrl)) return;
                        for (UUID id : chunk) {
                            if (!SiteApi.hasReference(id) && !batch.containsKey(id)) CapeBook.remote(id,null);
                        }
                        readBatch(batch);
                    });
                }
            } catch (Exception ignored) { /* Providers remain available when the service is down. */ }
        }
        for (PlayerFacts.Fact fact : facts) {
            if (PlayerFacts.byUuid(fact.id()) == null) continue;
            if (CapeBook.remote(fact.id()) != null && CapeTextures.get(CapeBook.textureKey(fact.id())) != null) continue;
            if (PROVIDERS_PENDING.add(fact.id()) && !download(() -> {
                try { fetchProvider(settings, fact); } finally { PROVIDERS_PENDING.remove(fact.id()); }
            })) PROVIDERS_PENDING.remove(fact.id());
        }
    }

    static Map<UUID, CapeBook.Remote> batchMetadata(String body) {
        Map<UUID, CapeBook.Remote> batch = new java.util.LinkedHashMap<>();
        Map<String, Object> root = JsonValues.object(JsonValues.parse(body));
        if (!(root.get("api") instanceof Long api) || api != 1L) {
            throw new JsonValues.Bad("api must be 1");
        }
        if (!(root.get("capes") instanceof Map<?, ?> capes)) {
            throw new JsonValues.Bad("capes must be an object");
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
            int count = map.get("frameCount") instanceof Long n ? Math.clamp(n, 1, 256) : 1;
            batch.put(uuid, new CapeBook.Remote(
                    map.get("style") instanceof String s ? s : "flat",
                    map.get("animateFrames") instanceof Boolean b && b,
                    count,
                    map.get("frameWidth") instanceof Long n ? Math.clamp(n, 1, 8192) : 64,
                    map.get("frameHeight") instanceof Long n ? Math.clamp(n, 1, 8192) : 32,
                    delays(map.get("frameDelayMs"), count),
                    map.get("model") instanceof Map<?, ?> ? JsonValues.write(map.get("model")) : null,
                    status
            ));
        }
        return batch;
    }

    private static void readBatch(Map<UUID, CapeBook.Remote> batch) {
        for (var entry : batch.entrySet()) {
            UUID uuid = entry.getKey();
            if (PlayerFacts.byUuid(uuid) == null || SiteApi.hasReference(uuid)) continue;
            CapeBook.remote(uuid, entry.getValue());
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
        int generation = SiteApi.generation();
        if (ClientSettings.get().localOnly) {
            return;
        }
        try {
            String base = ClientSettings.get().baseUrl;
            Reply response = sendBytes(CapeCalls.Kind.GET_ASSET, uuid.toString());
            if (response == null || response.status / 100 != 2) {
                return;
            }
            byte[] png = response.bytes;
            Minecraft.getInstance().execute(() -> {
                if (generation == SiteApi.generation() && !ClientSettings.get().localOnly && ClientSettings.get().showOthers && base.equals(ClientSettings.get().baseUrl)
                        && PlayerFacts.byUuid(uuid) != null && !SiteApi.hasReference(uuid))
                    CapeTextures.upload(CapeBook.textureKey(uuid), png, false, false);
            });
        } catch (Exception ignored) {
            // Missing assets stay on the vanilla cape.
        }
    }

    private static void fetchProvider(ClientSettings settings, PlayerFacts.Fact fact) {
        int generation = SiteApi.generation();
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
                            if (generation != SiteApi.generation() || !providerAllowed(fact.id(), provider)) return;
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
                        if (generation != SiteApi.generation() || !providerAllowed(fact.id(), provider)) return;
                        CapeTextures.Image stored = CapeTextures.upload("provider/" + fact.id(), png, animated, false);
                        if (stored != null) {
                            CapeBook.provider(fact.id(), "provider/" + fact.id(), provider);
                        }
                    });
                    return;
                }
                Minecraft.getInstance().execute(() -> {
                    if (generation != SiteApi.generation() || !providerAllowed(fact.id(), provider)) return;
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

    private static String serviceFailure(Exception error, String fallback) {
        return error instanceof CatalogClient.Failure failure && failure.key.equals("offline")
                ? SiteApi.message("unreachable") : message(fallback);
    }

    private static boolean login(String name, UUID uuid, String token) throws Exception {
        authNote = message("publish_signin");
        String base = ClientSettings.get().baseUrl;
        if (ClientSettings.get().localOnly) {
            authNote = message("publish_offline");
            return false;
        }
        if (Boolean.getBoolean("quarkos_capes.harness") && dev.quarkos.capes.client.harness.HarnessAuth.active()) {
            var session = dev.quarkos.capes.client.harness.HarnessAuth.complete(base, name);
            bearer = session.token();
            rememberBearer(uuid, base);
            return true;
        }
        if (!mojangSession(token)) {
            authNote = message("no_token");
            return false;
        }
        CatalogClient.Reply challenge = siteExchange("POST", base.replaceAll("/+$", "") + "/v1/auth/challenge", null, null, new byte[0]);
        if (challenge.status() / 100 != 2) {
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
        CatalogClient.Reply done = siteExchange("POST", base.replaceAll("/+$", "") + "/v1/auth/complete", null, null, complete.getBytes(StandardCharsets.UTF_8));
        if (done.status() / 100 != 2 || !base.equals(ClientSettings.get().baseUrl)) {
            authNote = message("session_failed");
            return false;
        }
        Map<String, Object> session = JsonValues.object(JsonValues.parse(done.text()));
        bearer = session.get("token") instanceof String value ? value : null;
        if (bearer == null) {
            authNote = message("no_session");
        }
        if (bearer != null) rememberBearer(uuid, base);
        return bearer != null;
    }

    private static void rememberBearer(UUID uuid, String base) {
        bearerBase = base;
        bearerPlayer = uuid;
        bearerUntil = System.currentTimeMillis() + 11 * 60 * 60 * 1000L;
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
        String base = serviceBase(ClientSettings.get().baseUrl);
        List<CapeCalls.Planned> planned = CapeCalls.plan(false, kind, base, "", null, null, galleryId, query, style, limit, cursor);
        if (planned.isEmpty()) {
            return null;
        }
        boolean auth = kind == CapeCalls.Kind.GALLERY_PUT || kind == CapeCalls.Kind.GALLERY_DELETE || kind == CapeCalls.Kind.DELETE_SESSION
                || kind == CapeCalls.Kind.PUT || kind == CapeCalls.Kind.DELETE;
        return raw(planned.get(0).method(), URI.create(planned.get(0).url()), body, auth, base, false);
    }

    private static Reply sendBytes(CapeCalls.Kind kind, String id) throws Exception {
        if (ClientSettings.get().localOnly) {
            return null;
        }
        String base = serviceBase(ClientSettings.get().baseUrl);
        List<CapeCalls.Planned> planned;
        if (kind == CapeCalls.Kind.GALLERY_ASSET) {
            planned = CapeCalls.plan(false, kind, base, "", null, null, id, "", "", 12, "");
        } else {
            UUID uuid = UUID.fromString(id);
            planned = CapeCalls.plan(false, kind, base, "", uuid, null);
        }
        if (planned.isEmpty()) {
            return null;
        }
        return raw(planned.get(0).method(), URI.create(planned.get(0).url()), null, false, base, true);
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
        countRequest();
        try (Response response = HTTP.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            byte[] bytes = responseBody == null ? new byte[0] : responseBody.byteStream().readNBytes(8 * 1024 * 1024 + 1);
            if (bytes.length > 8 * 1024 * 1024) throw new IOException("response too large");
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
        return raw(method, uri, body, auth, null, false);
    }

    private static Reply raw(String method, URI uri, byte[] body, boolean auth, String base, boolean image) throws Exception {
        if (ClientSettings.get().localOnly) {
            return null;
        }
        Request.Builder builder = new Request.Builder()
                .url(uri.toString())
                .header("User-Agent", CapeCalls.USER_AGENT)
                .header("Accept", "application/json, image/png");
        String header = CapeCalls.sessionHeader(auth ? bearer : null);
        if (header != null) {
            if (!ClientSettings.get().baseUrl.equals(bearerBase) || !ServiceUrl.sameOrigin(uri.toString(), bearerBase))
                throw new CatalogClient.Failure(401, "signin");
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
        boolean noRedirects = auth || !method.equals("GET");
        Request request = builder.build();
        return base == null ? externalExchange(request, noRedirects) : serviceExchange(base, request, noRedirects, image);
    }

    private static synchronized void countRequest() { sent++; }

    private static Reply exchange(Request request, boolean noRedirects) throws IOException {
        countRequest();
        try (Response response = (noRedirects ? PRIVATE_HTTP : HTTP).newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            if (responseBody != null && responseBody.contentLength() > 8 * 1024 * 1024) throw new IOException("response too large");
            byte[] bytes = responseBody == null ? new byte[0] : responseBody.byteStream().readNBytes(8 * 1024 * 1024 + 1);
            if (bytes.length > 8 * 1024 * 1024) throw new IOException("response too large");
            return new Reply(response.code(), bytes, response.header("Content-Type", ""), response.header("ETag"), response.header("Retry-After"));
        }
    }

    private static Reply externalExchange(Request request, boolean noRedirects) throws IOException {
        String origin = request.url().resolve("/").toString();
        var before = EXTERNAL.before(origin);
        try {
            Reply reply = exchange(request, noRedirects);
            if (reply.status == 429 || reply.status >= 500) {
                EXTERNAL.failed(origin, ServiceResponse.OfflineReason.SERVER_ERROR, reply.retryAfter, false);
            } else if (reply.status / 100 == 2) {
                EXTERNAL.success(origin, before);
            }
            return reply;
        } catch (IOException error) {
            EXTERNAL.failed(origin, ServiceResponse.transportFailure(error), null, false);
            throw error;
        }
    }

    private static Reply serviceExchange(String base, Request request, boolean noRedirects, boolean image) throws CatalogClient.Failure {
        verifyService(base);
        var before = HEALTH.before(base);
        Reply reply = checkedExchange(base, request, noRedirects, image);
        HEALTH.success(base, before);
        return reply;
    }

    private static void verifyService(String base) throws CatalogClient.Failure {
        ensureCurrentService(base);
        if (!base.equals(verifiedBase) && ServiceUrl.insecure(base) && WARNED_INSECURE.add(base)) {
            LOGGER.info("The configured cape service uses unencrypted HTTP outside loopback. Requests and session credentials can be observed in transit.");
        }
        HEALTH.before(base);
        if (!base.equals(verifiedBase)) {
            synchronized (PROBE_LOCK) {
                var before = HEALTH.before(base);
                ensureCurrentService(base);
                if (!base.equals(verifiedBase)) {
                    String url = CapeCalls.plan(false, CapeCalls.Kind.HEALTH, base, "", null, null).getFirst().url();
                    Request probe = new Request.Builder().url(url).header("User-Agent", CapeCalls.USER_AGENT)
                            .header("Accept", "application/json").build();
                    checkedExchange(base, probe, true, false);
                    verifiedBase = base;
                    if (before != null && before.healthProbe()) HEALTH.success(base, before);
                }
            }
        }
        ensureCurrentService(base);
    }

    private static void ensureCurrentService(String base) throws CatalogClient.Failure {
        ClientSettings settings = ClientSettings.get();
        if (settings.localOnly || !base.equals(serviceBase(settings.baseUrl))) throw new CatalogClient.Failure(0, "offline");
    }

    private static Reply checkedExchange(String base, Request request, boolean noRedirects, boolean image) throws CatalogClient.Failure {
        String path = request.url().encodedPath();
        Reply reply;
        try {
            reply = exchange(request, noRedirects);
        } catch (IOException error) {
            throw unavailable(base, request.method(), path, ServiceResponse.transportFailure(error), 0, null);
        }
        var reason = ServiceResponse.classify(request.method(), path, reply.status, reply.contentType,
                reply.bytes, image, request.header("If-None-Match") != null);
        if (reason == null && image && reply.status / 100 == 2) {
            try {
                validateImage(reply.bytes);
            } catch (CatalogClient.Failure invalid) {
                reason = ServiceResponse.OfflineReason.INVALID_RESPONSE;
            }
        }
        if (reason != null) throw unavailable(base, request.method(), path, reason, reply.status, reply.retryAfter);
        return reply;
    }

    private static CatalogClient.Failure unavailable(String base, String method, String path,
                                                      ServiceResponse.OfflineReason reason, int status, String retryAfter) {
        HEALTH.failed(base, reason, retryAfter, path.endsWith("/v1/health"));
        if (base.equals(verifiedBase)) verifiedBase = null;
        if (WARNED.add(base)) {
            LOGGER.warn("Cape service not available at {} ({} {}{}) because {}", base, method, path,
                    status > 0 ? ", HTTP " + status : "", reason);
        }
        return new CatalogClient.Failure(status, "offline");
    }

    private static void validateImage(byte[] bytes) throws CatalogClient.Failure {
        if (!dev.quarkos.capes.sim.ImageLimits.png(bytes)) throw new CatalogClient.Failure(0, "image_error");
        var header = java.nio.ByteBuffer.wrap(bytes);
        int width = header.getInt(16), height = header.getInt(20);
        try (var image = com.mojang.blaze3d.platform.NativeImage.read(new java.io.ByteArrayInputStream(bytes))) {
            if (image.getWidth() != width || image.getHeight() != height) throw new CatalogClient.Failure(0, "image_error");
        } catch (Exception invalid) { throw new CatalogClient.Failure(0, "image_error"); }
    }

    public static byte[] siteImage(String configuredBase, String url) throws Exception {
        String base = serviceBase(configuredBase);
        ensureCurrentService(base);
        URI uri = URI.create(url);
        boolean serviceAsset = ServiceUrl.sameOrigin(url, base);
        Reply response = raw("GET", uri, null, false, serviceAsset ? base : null, true);
        if (response == null || response.status / 100 != 2) {
            throw new CatalogClient.Failure(response == null ? 0 : response.status, "image_error");
        }
        if (!serviceAsset) validateImage(response.bytes);
        return response.bytes;
    }

    private static byte[] multipart(OwnerCape cape) {
        String boundary = "quarkos";
        StringBuilder head = new StringBuilder();
        part(head, boundary, "title", net.minecraft.network.chat.Component.translatable("text.quarkos_capes.site.publish_title", Minecraft.getInstance().getUser().getName()).getString());
        part(head, boundary, "frameCount", Integer.toString(cape.frameCount));
        part(head, boundary, "frameWidth", Integer.toString(cape.frameWidth));
        part(head, boundary, "frameHeight", Integer.toString(cape.frameHeight));
        part(head, boundary, "frameDelayMs", JsonValues.write(cape.frameDelayMs));
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
                    delays[i] = Math.clamp(number, 50, 1000);
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
