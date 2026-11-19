package dev.quarkos.capes.client.net;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.LocalFiles;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.render.CapeTextures;
import dev.quarkos.capes.client.render.PlayerFacts;
import dev.quarkos.capes.sim.JsonValues;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Minecraft adapter. Metadata uses the service pool; images use the bounded download pool. */
public final class SiteApi {
    public static final CatalogClient CLIENT = new CatalogClient(CapeHttp::siteExchange);
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, String> REMOTE = new ConcurrentHashMap<>();
    private static final Map<UUID, String> PENDING_IMAGES = new ConcurrentHashMap<>();
    private static volatile int remoteGeneration;
    public static int generation() { return remoteGeneration; }
    private static int tick;
    private static String config = "";
    private static volatile String lastError = "No HTTP error recorded; waiting for UI/render observation";
    public static String lastError() { return lastError; }
    private static String failure(Exception error) {
        if (error instanceof CatalogClient.Failure f && "offline".equals(f.key) && CapeHttp.serviceIssue() != null) {
            lastError = message("unreachable");
            return lastError;
        }
        String message = error instanceof CatalogClient.Failure f
                ? message(f.key) + (f.status > 0 ? " (HTTP " + f.status + ")" : "")
                : message("error") + " (" + error.getClass().getSimpleName() + ")";
        lastError = message;
        return error instanceof CatalogClient.Failure f ? message(f.key) : message("error");
    }
    private static Object world;
    private SiteApi() { }
    public static boolean hasReference(UUID id) { return REMOTE.containsKey(id); }
    public static String message(String key) { return Component.translatable("text.quarkos_capes.site." + key).getString(); }
    @FunctionalInterface public interface Work<T> { T run() throws Exception; }
    public static <T> void work(Work<T> work, Consumer<T> ok, Consumer<String> fail) {
        if (ClientSettings.get().localOnly) { fail.accept(message("offline")); return; }
        String base = ClientSettings.get().baseUrl;
        if (!CapeHttp.execute(() -> {
            try {
                if (ClientSettings.get().localOnly || !base.equals(ClientSettings.get().baseUrl)) throw new CatalogClient.Failure(0,"offline");
                T result = work.run();
                Minecraft.getInstance().execute(() -> {
                    if (!ClientSettings.get().localOnly && base.equals(ClientSettings.get().baseUrl)) ok.accept(result);
                    else fail.accept(message("offline"));
                });
            } catch (Exception e) { Minecraft.getInstance().execute(() -> fail.accept(failure(e))); }
        })) fail.accept(message("busy"));
    }
    public static void page(CatalogClient.Query query, Consumer<CatalogClient.Page> ok, Consumer<String> fail) {
        String base = ClientSettings.get().baseUrl;
        work(() -> CLIENT.page(base,query),ok,fail);
    }
    public static void detail(CatalogClient.Item item, Consumer<CatalogClient.Item> ok, Consumer<String> fail) {
        String base = ClientSettings.get().baseUrl;
        work(() -> CLIENT.detail(base,item.source(),item.id()),ok,fail);
    }
    private static CatalogClient.Reply me(String method, Object body) throws Exception {
        return CLIENT.request(ClientSettings.get().baseUrl, method, "/v1/me/wearing", CapeHttp.siteBearer(), body);
    }
    public static void wearing(Consumer<CatalogClient.Item> ok, Consumer<String> fail) {
        work(() -> CatalogClient.wearing(me("GET",null).text()),ok,fail);
    }
    public static void wear(CatalogClient.Item item, Consumer<CatalogClient.Item> ok, Consumer<String> fail) {
        work(() -> CatalogClient.wearing(me("PUT",Map.of("source",item.source(),"id",item.id())).text()), worn -> {
            if (worn == null) { fail.accept(message("missing")); return; }
            image(worn, png -> {
                OwnerCape.set(owner(worn,png));
                ClientSettings.get().equippedGalleryId = worn.id();
                LocalFiles.saveAsync(ignored -> { });
                ok.accept(worn);
            },fail);
        },fail);
    }
    public static void unwear(Consumer<String> ok, Consumer<String> fail) {
        work(() -> { me("DELETE",null); return true; }, result -> {
            OwnerCape.set(new OwnerCape()); ClientSettings.get().equippedGalleryId = null;
            LocalFiles.saveAsync(ignored -> { }); ok.accept(message("unworn"));
        },fail);
    }
    public static void claim(String code, Consumer<String> ok, Consumer<String> fail) {
        work(() -> {
            var response = CLIENT.request(ClientSettings.get().baseUrl,"POST","/v1/link/claim",CapeHttp.siteBearer(),Map.of("code",code.trim()));
            var doc = JsonValues.object(JsonValues.parse(response.text()));
            if (!Boolean.TRUE.equals(doc.get("linked"))) throw new CatalogClient.Failure(400,"invalid");
            return doc.get("name") instanceof String name ? name : "";
        }, name -> ok.accept(Component.translatable("text.quarkos_capes.site.linked",name).getString()),fail);
    }
    public static OwnerCape owner(CatalogClient.Item item, byte[] png) {
        OwnerCape cape = new OwnerCape(); cape.png = png; cape.style = item.style(); cape.animateFrames = item.animateFrames();
        cape.frameCount = item.frameCount(); cape.frameWidth = item.frameWidth(); cape.frameHeight = item.frameHeight();
        cape.frameDelayMs = item.delays(); cape.modelJson = item.modelJson();
        // LabyMod descriptors are nominal. Decode and use the actual atlas dimensions.
        if (item.source().equals("labymod")) {
            if (dev.quarkos.capes.sim.ImageLimits.png(png)) {
                var header = java.nio.ByteBuffer.wrap(png);
                cape.frameWidth = header.getInt(16); cape.frameHeight = header.getInt(20);
            }
        }
        return cape;
    }
    public static void image(CatalogClient.Item item, Consumer<byte[]> ok, Consumer<String> fail) {
        if (ClientSettings.get().localOnly) { fail.accept(message("offline")); return; }
        String base = ClientSettings.get().baseUrl;
        if (!CapeHttp.download(() -> {
            try {
                byte[] bytes = CapeHttp.siteImage(base, item.textureUrl());
                if (!item.sha256().isBlank()) {
                    String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
                    if (!hash.equalsIgnoreCase(item.sha256())) throw new java.io.IOException("hash");
                }
                byte[] png = CatalogTexture.localPng(item, bytes);
                Minecraft.getInstance().execute(() -> {
                    if (!ClientSettings.get().localOnly && base.equals(ClientSettings.get().baseUrl)) ok.accept(png);
                    else fail.accept(message("offline"));
                });
            } catch (Exception e) { Minecraft.getInstance().execute(() -> fail.accept(failure(e))); }
        })) fail.accept(message("busy"));
    }
    public static void tick(Minecraft client) {
        if (world != client.level) { world = client.level; REMOTE.clear(); PENDING_IMAGES.clear(); remoteGeneration++; tick = 0; }
        REMOTE.keySet().removeIf(id -> PlayerFacts.byUuid(id) == null);
        PENDING_IMAGES.keySet().removeIf(id -> PlayerFacts.byUuid(id) == null);
        ClientSettings s = ClientSettings.get();
        String now = s.baseUrl + s.localOnly + s.showOthers + s.enableLabyMod;
        if (!now.equals(config)) { config = now; tick = 0; REMOTE.clear(); PENDING_IMAGES.clear(); remoteGeneration++; CapeBook.clearPlayers(); }
        if (client.level == null || s.localOnly || !s.showOthers || tick++ % 600 != 0) return;
        List<UUID> ids = new ArrayList<>();
        for (var player : client.level.players()) if (player != client.player) ids.add(player.getUUID());
        for (List<UUID> chunk : CatalogClient.chunks(ids)) poll(chunk);
    }
    public static void poll(List<UUID> ids) {
        int request = remoteGeneration;
        String base = ClientSettings.get().baseUrl;
        String path = "/v1/players/wearing?uuids=" + String.join(",",ids.stream().map(UUID::toString).toList());
        if (!PENDING.add(path)) return;
        work(() -> {
            try { return CatalogClient.players(CLIENT.request(base,"GET",path,null,null).text()); }
            finally { PENDING.remove(path); }
        }, players -> {
            if (request == remoteGeneration) for (UUID id : ids) if (players.containsKey(id)) remote(id, players.get(id));
        }, ignored -> PENDING.remove(path));
    }
    private static void remote(UUID id, CatalogClient.Item item) {
        ClientSettings s = ClientSettings.get();
        if (s.localOnly || !s.showOthers || PlayerFacts.byUuid(id) == null) return;
        if (item == null || (item.source().equals("labymod") && !s.enableLabyMod)) {
            PENDING_IMAGES.remove(id);
            if (REMOTE.remove(id) != null) CapeBook.remote(id,null);
            return;
        }
        String identity = item.source() + "/" + item.id() + "/" + item.sha256() + "/" + item.textureUrl()
                + "/" + item.style() + item.animateFrames() + "/" + item.frameCount() + "/" + item.frameWidth() + "/" + item.frameHeight()
                + "/" + Arrays.toString(item.delays()) + "/" + item.modelJson() + "/" + item.verticalStrip();
        if (identity.equals(REMOTE.get(id)) && CapeBook.remote(id)!=null && CapeTextures.get(CapeBook.textureKey(id))!=null) return;
        if (identity.equals(PENDING_IMAGES.put(id, identity))) return;
        int request = remoteGeneration;
        image(item, png -> {
            if (request != remoteGeneration || !PENDING_IMAGES.remove(id, identity)) return;
            ClientSettings latest = ClientSettings.get();
            if (PlayerFacts.byUuid(id) == null || !latest.showOthers || latest.localOnly || (item.source().equals("labymod") && !latest.enableLabyMod)) return;
            OwnerCape cape = owner(item,png);
            var texture = CapeTextures.upload(CapeBook.textureKey(id),png,false,false);
            if (texture == null) return;
            CapeBook.remote(id,new CapeBook.Remote(cape.style,cape.animateFrames,cape.frameCount,cape.frameWidth,cape.frameHeight,cape.frameDelayMs,cape.modelJson,"accepted"));
            CapeBook.remoteSource(id,item.source()); REMOTE.put(id,identity);
        }, ignored -> { if (request == remoteGeneration) PENDING_IMAGES.remove(id, identity); });
    }
}
