package dev.quarkos.capes.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.quarkos.capes.sim.CapeGeometry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
//? if <1.21.11 {
/*import net.minecraft.client.renderer.RenderType;
*///?} else {
import net.minecraft.client.renderer.rendertype.RenderType;
//?}
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
//? if >=1.21.9 {
import net.minecraft.client.input.InputWithModifiers;
//?}
//? if fabric && >=1.21.9 {
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
//?}
//? if fabric && <1.21.9 {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
*///?}
//? if >=1.21.6 {
import net.minecraft.client.renderer.RenderPipelines;
//?}
//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?}

import java.util.function.Supplier;

/**
 * Version splits for keys, preview, textures, and screenshots.
 * Active sources are 1.21.11. The 1.21.1 branch is the commented arm.
 */
public final class McBridge {
    private McBridge() {
    }

    public static Identifier id(String path) {
        //? if <1.21 {
        /*return new Identifier("quarkos_capes", path);
        *///?} else {
        return Identifier.fromNamespaceAndPath("quarkos_capes", path);
        //?}
    }

    /** One model-part write for the versions that renamed the options method. */
    public static void modelPart(Options options, net.minecraft.world.entity.player.PlayerModelPart part, boolean enabled) {
        //? if <1.21.2 {
        /*options.toggleModelPart(part, enabled);
        *///?} else {
        options.setModelPart(part, enabled);
        //?}
    }

    public static float partialTick() {
        //? if <1.21 {
        /*return Minecraft.getInstance().getFrameTime();
        *///?} else if <1.21.2 {
        /*return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        *///?} else {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        //?}
    }

    public static void allowCommands(net.minecraft.client.gui.screens.worldselection.WorldCreationUiState ui) {
        //? if <1.21 {
        /*ui.setAllowCheats(true);
        *///?} else {
        ui.setAllowCommands(true);
        //?}
    }

    public static RenderType cutout(Identifier texture) {
        //? if <1.21.11 {
        /*return RenderType.entityCutoutNoCull(texture);
        *///?} else {
        return RenderTypes.armorCutoutNoCull(texture);
        //?}
    }

    public static void thumb(GuiGraphics graphics, Identifier id, int x, int y, int width, int height, int textureWidth, int textureHeight) {
        blit(graphics, id, x, y, width, height, textureWidth, textureHeight);
    }

    /** Scale the visible cape panel, excluding the unused atlas padding and strip neighbors. */
    public static void capeThumb(GuiGraphics graphics, Identifier texture, int x, int y, int width, int height,
                                 int textureWidth, int textureHeight, int frameWidth, int frameHeight) {
        int sourceWidth = Math.max(1, frameWidth * 10 / 64);
        int sourceHeight = Math.max(1, frameHeight / 2);
        float u = frameWidth / 64f, v = frameHeight / 32f;
        //? if <1.21.2 {
        /*graphics.blit(texture, x, y, width, height, u, v, sourceWidth, sourceHeight, textureWidth, textureHeight);
        *///?} else if <1.21.6 {
        /*graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured, texture, x, y, u, v,
                width, height, sourceWidth, sourceHeight, textureWidth, textureHeight);
        *///?} else {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height,
                sourceWidth, sourceHeight, textureWidth, textureHeight);
        //?}
    }

    public static DynamicTexture dynamic(NativeImage image, String name) {
        //? if <1.21.5 {
        /*return new DynamicTexture(image);
        *///?} else {
        return new DynamicTexture(() -> name, image);
        //?}
    }

    public static void setPixel(NativeImage image, int x, int y, int color) {
        //? if <1.21.2 {
        /*image.setPixelRGBA(x, y, color);
        *///?} else {
        image.setPixel(x, y, color);
        //?}
    }

    public static int pixel(NativeImage image, int x, int y) {
        //? if <1.21.2 {
        /*return image.getPixelRGBA(x, y);
        *///?} else {
        return image.getPixel(x, y);
        //?}
    }

    public static KeyMapping registerKey() {
        KeyMapping mapping = new KeyMapping(
                "key.quarkos_capes.open",
                79,
                //? if <1.21.9 {
                /*"key.categories.quarkos_capes"
                *///?} else {
                KeyMapping.Category.MISC
                //?}
        );
        //? if forge || neoforge {
        /*return mapping;
        *///?} else {
        return KeyBindingHelper.registerKeyBinding(mapping);
        //?}
    }

    public static void setScreen(Minecraft client, Screen screen) {
        client.setScreen(screen);
    }

    public static Screen currentScreen(Minecraft client) {
        return client.screen;
    }

    public static void thirdPerson(Options options) {
        options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
    }

    public static void slide(net.minecraft.client.gui.components.AbstractSliderButton slider, double fraction) {
        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        double x = slider.getX() + 4.0 + clamped * Math.max(1, slider.getWidth() - 8);
        double y = slider.getY() + slider.getHeight() / 2.0;
        //? if <1.21.9 {
        /*slider.onClick(x, y);
        *///?} else {
        slider.onClick(new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
        //?}
    }

    public static void showCape(Options options) {
        modelPart(options, net.minecraft.world.entity.player.PlayerModelPart.CAPE, true);
    }

    public static void press(Button button) {
        //? if <1.21.9 {
        /*button.onPress();
        *///?} else {
        button.onPress(new InputWithModifiers() {
            @Override
            public int input() {
                return 0;
            }

            @Override
            public int modifiers() {
                return 0;
            }
        });
        //?}
    }

    /** Both settings implementations use the same bounded, three-quarter cape view. */
    public static void preview(GuiGraphics graphics, int x, int y, LivingEntity player) {
        preview(graphics, x - 50, y - 140, x + 50, y + 8, player);
    }

    public static void preview(GuiGraphics graphics, int x1, int y1, int x2, int y2, LivingEntity player) {
        float yaw = player.getYRot(), pitch = player.getXRot();
        float body = player.yBodyRot, head = player.yHeadRot;
        float oldBody = player.yBodyRotO, oldHead = player.yHeadRotO;
        float oldYaw = player.yRotO, oldPitch = player.xRotO;
        boolean preview = dev.quarkos.capes.client.render.CapeBook.preview;
        try {
            dev.quarkos.capes.client.render.CapeBook.preview = true;
            player.setYRot(0f); player.setXRot(0f);
            player.yRotO = player.xRotO = 0f;
            player.yBodyRot = player.yBodyRotO = 0f;
            player.yHeadRot = player.yHeadRotO = 0f;
            Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
            // Inventory's upright basis faces the camera. Turn it to expose the cape.
            //? if <1.21.11 {
            /*pose.rotateY((float) Math.toRadians(dev.quarkos.capes.client.render.CapeBook.previewBack ? 25 : 155));
            *///?} else {
            pose.rotateY((float) Math.toRadians(dev.quarkos.capes.client.render.CapeBook.previewBack ? 155 : 25));
            //?}
            Quaternionf camera = new Quaternionf();
            //? if <1.21 {
            /*int size = Math.max(16, Math.min((y2 - y1) / 2, x2 - x1));
            InventoryScreen.renderEntityInInventory(graphics, (x1 + x2) / 2, y2 - 6, size, pose, camera, player);
            *///?} else if <1.21.6 {
            /*int size = Math.max(16, Math.min((y2 - y1) / 2, x2 - x1));
            InventoryScreen.renderEntityInInventory(graphics, (x1 + x2) / 2, y2 - 6, size,
                    new Vector3f(0f, 0f, 0f), pose, camera, player);
            *///?} else if <1.21.11 {
            /*float scale = Math.max(16, Math.min((y2 - y1) / 2f, x2 - x1));
            InventoryScreen.renderEntityInInventory(graphics, x1, y1, x2, y2, scale,
                    new Vector3f(0f, 0f, 0f), pose, camera, player);
            *///?} else {
            var state = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(player, 1f);
            state.lightCoords = 15728880;
            if (state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living) {
                living.bodyRot = 180f; living.yRot = 0f; living.xRot = 0f;
                if (living.scale != 0f && living.scale != 1f) {
                    living.boundingBoxWidth /= living.scale;
                    living.boundingBoxHeight /= living.scale;
                    living.scale = 1f;
                }
            }
            float half = Math.max(0.9f, state.boundingBoxHeight / 2f);
            float scale = Math.max(12f, Math.min((y2 - y1) / 2.2f, (x2 - x1) / 1.2f));
            graphics.submitEntityRenderState(state, scale, new Vector3f(0f, half, 0f), pose, camera, x1, y1, x2, y2);
            //?}
        } finally {
            player.setYRot(yaw); player.setXRot(pitch);
            player.yRotO = oldYaw; player.xRotO = oldPitch;
            player.yBodyRot = body; player.yHeadRot = head;
            player.yBodyRotO = oldBody; player.yHeadRotO = oldHead;
            dev.quarkos.capes.client.render.CapeBook.preview = preview;
        }
    }

    public static String playerName(java.util.UUID id) {
        if (id == null) {
            return "";
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            for (var player : client.level.players()) {
                if (id.equals(player.getUUID())) {
                    return player.getName().getString();
                }
            }
        }
        if (client.getConnection() == null) {
            return "";
        }
        var info = client.getConnection().getPlayerInfo(id);
        if (info == null || info.getProfile() == null) {
            return "";
        }
        //? if <1.21.11 {
        /*return info.getProfile().getName();
        *///?} else {
        return info.getProfile().name();
        //?}
    }

    public static void quad(VertexConsumer consumer, PoseStack.Pose pose, int light, CapeGeometry.Quad quad) {
        vertex(consumer, pose, light, 255, quad.x0(), quad.y0(), quad.z0(), quad.u0(), quad.v0(), quad.nx(), quad.ny(), quad.nz());
        vertex(consumer, pose, light, 255, quad.x1(), quad.y1(), quad.z1(), quad.u0(), quad.v1(), quad.nx(), quad.ny(), quad.nz());
        vertex(consumer, pose, light, 255, quad.x2(), quad.y2(), quad.z2(), quad.u1(), quad.v1(), quad.nx(), quad.ny(), quad.nz());
        vertex(consumer, pose, light, 255, quad.x3(), quad.y3(), quad.z3(), quad.u1(), quad.v0(), quad.nx(), quad.ny(), quad.nz());
    }

    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, int light, int shade, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        //? if <1.21 {
        /*consumer.vertex(pose.pose(), x, y, z)
                .color(shade, shade, shade, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), nx, ny, nz)
                .endVertex();
        *///?} else {
        consumer.addVertex(pose, x, y, z)
                .setColor(shade, shade, shade, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
        //?}
    }

    public static void clearChat(Minecraft client) {
        if (client.gui != null) {
            client.gui.getChat().clearMessages(false);
        }
    }

    public static void clearToasts(Minecraft client) {
        //? if <1.21.2 {
        /*client.getToasts().clear();
        *///?} else {
        client.getToastManager().clear();
        //?}
    }

    public static void prepareWorld(Minecraft client) {
        if (client.player == null) {
            return;
        }
        //? if <1.21.2 {
        /*client.player.connection.sendCommand("gamerule doDaylightCycle false");
        client.player.connection.sendCommand("gamerule sendCommandFeedback false");
        client.player.connection.sendCommand("gamerule logAdminCommands false");
        *///?} else {
        client.player.connection.sendCommand("gamerule advance_time false");
        client.player.connection.sendCommand("gamerule send_command_feedback false");
        client.player.connection.sendCommand("gamerule log_admin_commands false");
        //?}
        client.player.connection.sendCommand("time set day");
    }

    public static void blit(GuiGraphics graphics, Identifier texture, int x, int y, int width, int height, int textureWidth, int textureHeight) {
        //? if <1.21.2 {
        /*graphics.blit(texture, x, y, 0f, 0f, width, height, textureWidth, textureHeight);
        *///?} else if <1.21.6 {
        /*graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured, texture, x, y, 0f, 0f, width, height, textureWidth, textureHeight);
        *///?} else {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, width, height, textureWidth, textureHeight);
        //?}
    }

    public static void shot(Minecraft client, java.nio.file.Path file) {
        clearChat(client);
        file.getParent().toFile().mkdirs();
        //? if <1.21.5 {
        /*try {
            NativeImage image = Screenshot.takeScreenshot(client.getMainRenderTarget());
            try {
                image.writeToFile(file);
            } finally {
                image.close();
            }
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        *///?} else {
        Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
            try {
                image.writeToFile(file);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            } finally {
                image.close();
            }
        });
        //?}
    }

    public static Supplier<String> name(String value) {
        return () -> value;
    }
}
