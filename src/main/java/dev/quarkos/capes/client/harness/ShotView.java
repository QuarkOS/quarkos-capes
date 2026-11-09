package dev.quarkos.capes.client.harness;

import com.mojang.blaze3d.platform.NativeImage;
import dev.quarkos.capes.client.McBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelPart;
//? if >=1.21.11 {
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
//?}

/**
 * Harness-only camera orbit and a short-haired skin.
 * Inactive unless a compare shot asks for a view, so a normal client is unchanged.
 */
public final class ShotView {
    private static float yaw;
    private static float pitch;
    private static float distance;
    //? if >=1.21.11 {
    private static PlayerSkin skin;
    //?}

    private ShotView() {
    }

    public static void frame(float yawDegrees, float pitchDegrees, float blocks) {
        yaw = yawDegrees;
        pitch = pitchDegrees;
        distance = blocks;
    }

    public static void clear() {
        distance = 0f;
    }

    public static boolean active() {
        AuditHarness.frame();
        return Boolean.getBoolean("quarkos_capes.harness") && distance > 0.01f;
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    public static float distance() {
        return distance;
    }

    /** Look at the cape, a little below the eyes, so the hem stays in frame. */
    public static float lift() {
        return -0.72f;
    }

    public static void install(Minecraft client) {
        hideHat(client);
        //? if >=1.21.11 {
        skin = shortHair(client);
        //?}
        client.options.broadcastOptions();
    }

    private static void hideHat(Minecraft client) {
        //? if <1.21.11 {
        /*client.options.toggleModelPart(PlayerModelPart.HAT, false);
        *///?} else {
        client.options.setModelPart(PlayerModelPart.HAT, false);
        //?}
    }

    //? if >=1.21.11 {
    public static PlayerSkin skin() {
        return skin;
    }

    /** Steve's base head, with the hat layer cleared so the hair does not cover the cape. */
    private static PlayerSkin shortHair(Minecraft client) {
        Identifier steve = Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");
        try (var stream = client.getResourceManager().getResourceOrThrow(steve).open()) {
            NativeImage image = NativeImage.read(stream);
            int hatWidth = Math.min(image.getWidth(), 64);
            int hatHeight = Math.min(image.getHeight(), 16);
            for (int y = 0; y < hatHeight; y++) {
                for (int x = 32; x < hatWidth; x++) {
                    McBridge.setPixel(image, x, y, 0);
                }
            }
            Identifier id = McBridge.id("textures/shot/short_hair.png");
            client.getTextureManager().register(id, McBridge.dynamic(image, "short-hair"));
            return new PlayerSkin(new ClientAsset.ResourceTexture(id, id), null, null, PlayerModelType.WIDE, false);
        } catch (Exception e) {
            throw new IllegalStateException("short-haired skin was not ready", e);
        }
    }
    //?}
}
