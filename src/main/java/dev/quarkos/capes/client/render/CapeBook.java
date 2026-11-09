package dev.quarkos.capes.client.render;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.sim.CapeModelJson;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Picks what to draw. Our accepted cape wins over a provider image. */
public final class CapeBook {
    public enum Kind {
        NONE, QUAD, CLOTH, MODEL
    }

    public record Choice(
            Kind kind,
            Identifier texture,
            boolean vertical,
            int frames,
            int frameWidth,
            int frameHeight,
            int[] delays,
            boolean animate,
            String modelJson
    ) {
    }

    public record Remote(String style, boolean animateFrames, int frameCount, int frameWidth, int frameHeight, int[] delays, String modelJson, String status) {
    }

    private static final Map<UUID, String> REMOTE_SOURCES = new ConcurrentHashMap<>();
    public static void remoteSource(UUID id, String source) { REMOTE_SOURCES.put(id, source); }
    private static final Map<UUID, Remote> REMOTE = new ConcurrentHashMap<>();
    private static final Map<UUID, String> PROVIDER = new ConcurrentHashMap<>();
    private static final Map<UUID, String> PROVIDER_SOURCES = new ConcurrentHashMap<>();
    private static final Map<Identifier, Choice> PROVIDER_CHOICES = new java.util.LinkedHashMap<>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Identifier, Choice> entry) { return size() > 128; }
    };
    private static final int[] STILL = {100};
    private static final Choice NONE = new Choice(Kind.NONE, null, false, 1, 64, 32, STILL, false, null);
    private static final Map<UUID, String> TEXTURE_KEYS = new ConcurrentHashMap<>();
    public static boolean preview;
    /** When the picker is open, true shows the cape from behind. */
    public static boolean previewBack = true;
    public static OwnerCape draft;
    public static int draws;
    private static final boolean WATCH_DRAWS = Boolean.getBoolean("quarkos_capes.harness");
    private static final Map<UUID,Integer> DRAWN = new ConcurrentHashMap<>();
    public static void drawn(UUID id) { draws++; if (WATCH_DRAWS) DRAWN.merge(id,1,Integer::sum); }
    public static int draws(UUID id) { return DRAWN.getOrDefault(id,0); }
    public static void resetDraws() { draws=0; DRAWN.clear(); }

    private CapeBook() {
    }

    public static void remote(UUID id, Remote meta) {
        if (meta == null) {
            REMOTE.remove(id);
            REMOTE_SOURCES.remove(id);
        } else {
            REMOTE.put(id, meta);
        }
    }

    public static Remote remote(UUID id) {
        return REMOTE.get(id);
    }

    public static void provider(UUID id, String key) {
        if (key == null) {
            PROVIDER.remove(id);
        } else {
            PROVIDER.put(id, key);
        }
    }

    public static void provider(UUID id, String key, String source) {
        provider(id, key);
        PROVIDER_SOURCES.put(id, source);
    }

    private static CapeTextures.Image providerImage(UUID id) {
        String source = PROVIDER_SOURCES.get(id);
        if (source != null && !ClientSettings.get().providerEnabled(source)) return null;
        String key = PROVIDER.get(id);
        return key == null ? null : CapeTextures.get(key);
    }

    public static Choice local(OwnerCape cape, CapeTextures.Image image) {
        if (cape == null || image == null) {
            return none();
        }
        return fromOwner(cape, image);
    }

    public static Choice other(UUID id, Identifier vanillaCape, boolean showVanilla) {
        ClientSettings settings = ClientSettings.get();
        if (settings.localOnly || !settings.showOthers) {
            return none();
        }
        Remote remote = REMOTE.get(id);
        String remoteKey = TEXTURE_KEYS.get(id);
        CapeTextures.Image ours = remoteKey == null ? null : CapeTextures.get(remoteKey);
        if (remote != null && "accepted".equals(remote.status) && ours != null
                && (!"labymod".equals(REMOTE_SOURCES.get(id)) || settings.enableLabyMod)) {
            return fromRemote(remote, ours);
        }
        String providerKey = PROVIDER.get(id);
        CapeTextures.Image provider = providerImage(id);
        if (provider != null) {
            if (provider.capeBox()) {
                return vanillaBox(providerKey, provider);
            }
            synchronized (PROVIDER_CHOICES) {
                Choice found = PROVIDER_CHOICES.get(provider.id());
                if (found != null) return found;
                int frames = Math.max(1, provider.frames());
                int[] delays = new int[frames];
                java.util.Arrays.fill(delays, 100);
                int frameHeight = provider.vertical() ? provider.width() / 2 : provider.height();
                Choice choice = new Choice(Kind.CLOTH, provider.id(), provider.vertical(), frames, provider.width(), frameHeight, delays, provider.vertical(), null);
                PROVIDER_CHOICES.put(provider.id(), choice);
                return choice;
            }
        }
        if (showVanilla && vanillaCape != null) {
            return new Choice(Kind.QUAD, vanillaCape, false, 1, 64, 32, STILL, false, null);
        }
        return none();
    }

    public static Identifier elytraFor(UUID id, boolean local) {
        if (!ClientSettings.get().enableElytraTexture) {
            return null;
        }
        if (local) {
            CapeTextures.Image image = CapeTextures.get("local");
            return image != null && image.elytra() ? image.id() : null;
        }
        if (ClientSettings.get().localOnly || !ClientSettings.get().showOthers) return null;
        String remoteKey = TEXTURE_KEYS.get(id);
        CapeTextures.Image ours = remoteKey == null ? null : CapeTextures.get(remoteKey);
        if (ours != null && ours.elytra() && (!"labymod".equals(REMOTE_SOURCES.get(id)) || ClientSettings.get().enableLabyMod)) {
            return ours.id();
        }
        String providerKey = PROVIDER.get(id);
        CapeTextures.Image provider = providerImage(id);
        return provider != null && provider.elytra() ? provider.id() : null;
    }

    public static String textureKey(UUID id) {
        return TEXTURE_KEYS.computeIfAbsent(id, value -> "remote/" + value);
    }

    /**
     * One frame of a vanilla cape atlas. The cape layer draws it, so the hang,
     * the swing, and the front, back, and edges stay the vanilla ones.
     */
    public static Choice vanillaBox(String key, CapeTextures.Image image) {
        int count = Math.max(1, image.frames());
        int frame = count > 1 ? (int) Math.floorMod(System.currentTimeMillis() / 100L, count) : 0;
        Identifier texture = CapeTextures.frame(key, frame);
        int frameHeight = image.vertical() ? image.width() / 2 : image.height();
        return new Choice(Kind.QUAD, texture, false, 1, image.width(), frameHeight, STILL, false, null);
    }

    private static Choice fromOwner(OwnerCape cape, CapeTextures.Image image) {
        Kind kind = switch (cape.style) {
            case "wave" -> Kind.CLOTH;
            case "model" -> Kind.MODEL;
            default -> Kind.QUAD;
        };
        return new Choice(kind, image.id(), false, Math.max(1, cape.frameCount), cape.frameWidth, cape.frameHeight, cape.frameDelayMs, cape.animateFrames, cape.modelJson);
    }

    private static Choice fromRemote(Remote remote, CapeTextures.Image image) {
        Kind kind = switch (remote.style) {
            case "wave" -> Kind.CLOTH;
            case "model" -> Kind.MODEL;
            default -> Kind.QUAD;
        };
        return new Choice(kind, image.id(), image.vertical(), Math.max(1, remote.frameCount), remote.frameWidth, remote.frameHeight, remote.delays, remote.animateFrames, remote.modelJson);
    }

    public static List<CapeModelJson.Cube> cubes(Choice choice) {
        if (choice.modelJson == null) {
            return List.of();
        }
        try {
            return CapeModelJson.parse(choice.modelJson, Math.max(1, choice.frameWidth), Math.max(1, choice.frameHeight));
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    public static Choice none() { return NONE; }

    public static void forget(UUID id) {
        DRAWN.remove(id);
        REMOTE.remove(id);
        REMOTE_SOURCES.remove(id);
        PROVIDER_SOURCES.remove(id);
        String remoteKey = TEXTURE_KEYS.remove(id);
        if (remoteKey != null) CapeTextures.clear(remoteKey);
        String providerKey = PROVIDER.remove(id);
        if (providerKey != null) CapeTextures.clear(providerKey);
    }

    public static void clearProviders() {
        for (String key : PROVIDER.values()) CapeTextures.clear(key);
        PROVIDER.clear(); PROVIDER_SOURCES.clear();
        synchronized (PROVIDER_CHOICES) { PROVIDER_CHOICES.clear(); }
    }

    public static void clearPlayers() {
        for (UUID id : TEXTURE_KEYS.keySet()) forget(id);
        for (UUID id : PROVIDER.keySet()) forget(id);
        REMOTE.clear();
        REMOTE_SOURCES.clear();
        synchronized (PROVIDER_CHOICES) { PROVIDER_CHOICES.clear(); }
    }
}
