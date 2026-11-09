package dev.quarkos.capes.client.local;

import dev.quarkos.capes.client.net.CapeCalls;
import dev.quarkos.capes.sim.JsonValues;
import dev.quarkos.capes.sim.WaveCloth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Viewer settings in {@code config/quarkos_capes.json}. Defaults keep the network idle. */
public final class ClientSettings {
    private static ClientSettings current = new ClientSettings();

    public boolean showOthers = true;
    public boolean localOnly = true;
    public String baseUrl = CapeCalls.DEFAULT_SERVICE_URL;
    public boolean enableOptifine;
    public boolean enableLabyMod;
    public boolean enableMinecraftCapesMod;
    public boolean enableCosmetica;
    public boolean enableCloaksPlus;
    public boolean enableElytraTexture = true;
    public WaveCloth.Settings cloth = WaveCloth.Settings.defaults();
    public String equippedGalleryId;

    public static ClientSettings get() {
        return current;
    }

    public static void load(Path file) {
        ClientSettings settings = new ClientSettings();
        if (Files.isRegularFile(file)) {
            try {
                Map<String, Object> root = JsonValues.object(JsonValues.parse(Files.readString(file)));
                settings.showOthers = bool(root, "showOthers", true);
                settings.localOnly = bool(root, "localOnly", true);
                if (root.get("baseUrl") instanceof String url && !url.isBlank()) {
                    settings.baseUrl = url;
                }
                settings.enableOptifine = bool(root, "enableOptifine", false);
                settings.enableLabyMod = bool(root, "enableLabyMod", false);
                settings.enableMinecraftCapesMod = bool(root, "enableMinecraftCapesMod", false);
                settings.enableCosmetica = bool(root, "enableCosmetica", false);
                settings.enableCloaksPlus = bool(root, "enableCloaksPlus", false);
                settings.enableElytraTexture = bool(root, "enableElytraTexture", true);
                settings.equippedGalleryId = root.get("equippedGalleryId") instanceof String id ? id : null;
                settings.cloth = new WaveCloth.Settings(
                        movement(text(root, "capeMovement", "simulation_v2")),
                        mesh(text(root, "capeStyle", "smooth")),
                        wind(text(root, "windMode", "none")),
                        integer(root, "gravity", 25),
                        integer(root, "heightMultiplier", 6),
                        integer(root, "straveMultiplier", 2),
                        bool(root, "computeGravityVector", false)
                ).clamped();
            } catch (RuntimeException | IOException ignored) {
                settings = new ClientSettings();
            }
        }
        current = settings;
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, json());
    }

    public String json() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("showOthers", showOthers);
        root.put("localOnly", localOnly);
        root.put("baseUrl", baseUrl);
        root.put("enableOptifine", enableOptifine);
        root.put("enableLabyMod", enableLabyMod);
        root.put("enableMinecraftCapesMod", enableMinecraftCapesMod);
        root.put("enableCosmetica", enableCosmetica);
        root.put("enableCloaksPlus", enableCloaksPlus);
        root.put("enableElytraTexture", enableElytraTexture);
        root.put("windMode", cloth.wind().name().toLowerCase());
        root.put("capeStyle", cloth.mesh().name().toLowerCase());
        root.put("capeMovement", movementName(cloth.movement()));
        root.put("gravity", cloth.gravity());
        root.put("heightMultiplier", cloth.heightMultiplier());
        root.put("computeGravityVector", cloth.computeGravityVector());
        root.put("straveMultiplier", cloth.straveMultiplier());
        root.put("equippedGalleryId", equippedGalleryId);
        return JsonValues.write(root);
    }

    public boolean providerEnabled(String provider) {
        return switch (provider) {
            case "optifine" -> enableOptifine;
            case "labymod" -> enableLabyMod;
            case "minecraftcapes" -> enableMinecraftCapesMod;
            case "cosmetica" -> enableCosmetica;
            case "cloaksplus" -> enableCloaksPlus;
            default -> false;
        };
    }

    public static String movementName(WaveCloth.Movement movement) {
        return switch (movement) {
            case CLASSIC -> "classic";
            case SIMULATION -> "simulation";
            case SIMULATION_V2 -> "simulation_v2";
            case DUNGEONS -> "dungeons";
        };
    }

    public static WaveCloth.Movement movement(String name) {
        return switch (name) {
            case "classic" -> WaveCloth.Movement.CLASSIC;
            case "simulation" -> WaveCloth.Movement.SIMULATION;
            case "dungeons" -> WaveCloth.Movement.DUNGEONS;
            default -> WaveCloth.Movement.SIMULATION_V2;
        };
    }

    public static WaveCloth.Mesh mesh(String name) {
        return "blocky".equals(name) ? WaveCloth.Mesh.BLOCKY : WaveCloth.Mesh.SMOOTH;
    }

    public static WaveCloth.Wind wind(String name) {
        return "waves".equals(name) ? WaveCloth.Wind.WAVES : WaveCloth.Wind.NONE;
    }

    private static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map.get(key);
        return value instanceof Boolean b ? b : fallback;
    }

    private static int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Long n ? n.intValue() : fallback;
    }

    private static String text(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value instanceof String s ? s : fallback;
    }
}
