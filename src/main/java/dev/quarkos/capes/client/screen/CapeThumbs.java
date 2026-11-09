package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.Samples;
import dev.quarkos.capes.client.render.CapeTextures;
import net.minecraft.client.gui.GuiGraphics;

/** Draws the three bundled sample capes. */
public final class CapeThumbs {
    private CapeThumbs() {
    }

    public static void draw(GuiGraphics graphics, int x, int y) {
        drawOne(graphics, "bands", x, y);
        drawOne(graphics, "slide", x, y + 20);
        drawOne(graphics, "cloak", x, y + 40);
    }

    public static void drawOne(GuiGraphics graphics, String key, int x, int y) {
        byte[] png = switch (key) {
            case "slide" -> Samples.slide();
            case "cloak" -> Samples.cloakPng();
            default -> Samples.bands();
        };
        blit(graphics, key, png, x, y);
    }

    private static void blit(GuiGraphics graphics, String key, byte[] png, int x, int y) {
        CapeTextures.Image image = CapeTextures.get("sample:" + key);
        if (image == null) {
            image = CapeTextures.upload("sample:" + key, png, false, false);
        }
        if (image == null) {
            return;
        }
        McBridge.capeThumb(graphics, image.id(), x, y, 10, 16, image.width(), image.height(), 64, 32);
    }
}
