package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.LocalFiles;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.net.CapeHttp;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.sim.WaveCloth;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Shared settings behavior for the Fabric screen, the Cloth screen, and the
 * 1.21.11 Forge screen. The widgets differ. The values they write do not.
 */
public final class CapeBoard {
    public static final String[] TABS = {
            "text.quarkos_capes.tab.mine",
            "text.quarkos_capes.tab.style",
            "text.quarkos_capes.tab.physics",
            "text.quarkos_capes.tab.sources",
            "text.quarkos_capes.tab.gallery",
            "text.quarkos_capes.tab.advanced"
    };

    private static String sample = "bands";

    private CapeBoard() {
    }

    public static Component state(String name, boolean on) {
        return Component.literal(name + ": ").append(Component.translatable(on ? "options.on" : "options.off"));
    }

    public static void show() {
        CapeBook.preview = true;
        CapeBook.draft = OwnerCape.get();
    }

    public static void equipSample(String id) {
        sample = id;
        OwnerCape.set(switch (id) {
            case "slide" -> OwnerCape.sampleSlide();
            case "cloak" -> OwnerCape.sampleCloak();
            default -> OwnerCape.sampleBands();
        });
        ClientSettings.get().equippedGalleryId = null;
        show();
    }

    public static boolean sampleOn(String id) {
        OwnerCape cape = OwnerCape.get();
        if (ClientSettings.get().equippedGalleryId != null) return false;
        byte[] expected = switch (id) {
            case "bands" -> dev.quarkos.capes.client.local.Samples.bands();
            case "slide" -> dev.quarkos.capes.client.local.Samples.slide();
            case "cloak" -> dev.quarkos.capes.client.local.Samples.cloakPng();
            default -> null;
        };
        return expected != null && java.util.Arrays.equals(cape.png, expected);
    }

    public static void style(String style) {
        OwnerCape cape = OwnerCape.get();
        cape.style = style;
        if ("model".equals(style)) {
            if (cape.modelJson == null) {
                cape.modelJson = OwnerCape.sampleCloak().modelJson;
            }
        } else {
            cape.modelJson = null;
        }
        if ("frames".equals(style)) {
            cape.animateFrames = true;
        }
        show();
    }

    public static boolean styleOn(String style) {
        return style.equals(OwnerCape.get().style);
    }

    public static void animation(boolean on) {
        OwnerCape.get().animateFrames = on;
        show();
    }

    public static void movement(WaveCloth.Movement movement) {
        WaveCloth.Settings cloth = settings().cloth;
        cloth(movement, cloth.mesh(), cloth.wind(), cloth.gravity(), cloth.heightMultiplier(), cloth.straveMultiplier(), cloth.computeGravityVector());
    }

    public static boolean movementOn(WaveCloth.Movement movement) {
        return settings().cloth.movement() == movement;
    }

    public static void mesh(WaveCloth.Mesh mesh) {
        WaveCloth.Settings cloth = settings().cloth;
        cloth(cloth.movement(), mesh, cloth.wind(), cloth.gravity(), cloth.heightMultiplier(), cloth.straveMultiplier(), cloth.computeGravityVector());
    }

    public static boolean meshOn(WaveCloth.Mesh mesh) {
        return settings().cloth.mesh() == mesh;
    }

    public static void wind(WaveCloth.Wind wind) {
        WaveCloth.Settings cloth = settings().cloth;
        cloth(cloth.movement(), cloth.mesh(), wind, cloth.gravity(), cloth.heightMultiplier(), cloth.straveMultiplier(), cloth.computeGravityVector());
    }

    public static boolean windOn(WaveCloth.Wind wind) {
        return settings().cloth.wind() == wind;
    }

    public static void gravity(int value) {
        WaveCloth.Settings cloth = settings().cloth;
        cloth(cloth.movement(), cloth.mesh(), cloth.wind(), value, cloth.heightMultiplier(), cloth.straveMultiplier(), cloth.computeGravityVector());
    }

    public static void height(int value) {
        WaveCloth.Settings cloth = settings().cloth;
        cloth(cloth.movement(), cloth.mesh(), cloth.wind(), cloth.gravity(), value, cloth.straveMultiplier(), cloth.computeGravityVector());
    }

    public static void strafe(int value) {
        WaveCloth.Settings cloth = settings().cloth;
        cloth(cloth.movement(), cloth.mesh(), cloth.wind(), cloth.gravity(), cloth.heightMultiplier(), value, cloth.computeGravityVector());
    }

    public static void gravityVector(boolean on) {
        WaveCloth.Settings cloth = settings().cloth;
        cloth(cloth.movement(), cloth.mesh(), cloth.wind(), cloth.gravity(), cloth.heightMultiplier(), cloth.straveMultiplier(), on);
    }

    public static void showOthers(boolean on) {
        settings().showOthers = on;
    }

    public static void localOnly(boolean on) {
        settings().localOnly = on;
    }

    public static void elytra(boolean on) {
        settings().enableElytraTexture = on;
    }

    public static void provider(String provider, boolean on) {
        ClientSettings settings = settings();
        switch (provider) {
            case "optifine" -> settings.enableOptifine = on;
            case "labymod" -> settings.enableLabyMod = on;
            case "minecraftcapes" -> settings.enableMinecraftCapesMod = on;
            case "cosmetica" -> settings.enableCosmetica = on;
            case "cloaksplus" -> settings.enableCloaksPlus = on;
            default -> {
            }
        }
    }

    public static void publish(Consumer<String> note) {
        if (settings().localOnly) {
            note.accept(message("offline"));
            return;
        }
        note.accept(message("publishing"));
        CapeHttp.publish(note);
    }

    public static void unpublish(Consumer<String> note) {
        if (settings().localOnly) {
            note.accept(message("offline"));
            return;
        }
        note.accept(message("unpublishing"));
        CapeHttp.unpublish(note);
    }

    public static void report(String id, Consumer<String> note) {
        if (settings().localOnly) {
            note.accept(message("offline"));
            return;
        }
        if (id == null || id.isBlank()) {
            note.accept(message("select_report"));
            return;
        }
        note.accept(message("reporting"));
        CapeHttp.report(id, note);
    }

    public static void save(Consumer<String> note) {
        note.accept(message("saving"));
        LocalFiles.saveAsync(success -> note.accept(message(success ? "saved" : "save_failed")));
    }

    private static String message(String key) {
        return Component.translatable("text.quarkos_capes.status." + key).getString();
    }

    public static void discard() {
        LocalFiles.load();
        show();
    }

    public static void baseUrl(String url) {
        if (url != null && !url.isBlank()) {
            settings().baseUrl = url.trim();
        }
    }

    private static void cloth(WaveCloth.Movement movement, WaveCloth.Mesh mesh, WaveCloth.Wind wind, int gravity, int height, int strafe, boolean vector) {
        settings().cloth = new WaveCloth.Settings(movement, mesh, wind, gravity, height, strafe, vector).clamped();
    }

    private static ClientSettings settings() {
        return ClientSettings.get();
    }
}
