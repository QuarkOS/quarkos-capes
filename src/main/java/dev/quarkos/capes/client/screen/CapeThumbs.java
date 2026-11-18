package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.render.CapeTextures;
import dev.quarkos.capes.client.screen.ui.CapeGridLayout.Rect;
import dev.quarkos.capes.sim.StripFrames;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Fits the outside face of one atlas frame into a card's texture well. */
final class CapeThumbs {
    static final List<String> SAMPLES = List.of("bands", "slide", "cloak");
    private static final int FACE_WIDTH = 10;
    private static final int FACE_HEIGHT = 16;

    private CapeThumbs() { }

    static OwnerCape sample(String id) {
        return switch (id) {
            case "slide" -> OwnerCape.sampleSlide();
            case "cloak" -> OwnerCape.sampleCloak();
            default -> OwnerCape.sampleBands();
        };
    }

    static void draw(GuiGraphics graphics, Rect area, CapeTextures.Image image, OwnerCape cape) {
        if (image == null || cape == null) return;
        int scale = Math.min(area.width() / FACE_WIDTH, area.height() / FACE_HEIGHT);
        if (scale < 1) return;
        int frames = Math.max(1, cape.frameCount);
        int frameWidth = image.width(), frameHeight = image.height();
        boolean vertical = frames > 1 && image.height() / frames == cape.frameHeight
                && image.width() == cape.frameWidth && image.height() % frames == 0;
        if (vertical) {
            frameHeight /= frames;
        } else if (frames > 1 && image.width() % frames == 0) {
            frameWidth /= frames;
        } else {
            frames = 1;
        }

        int frame = cape.animateFrames && frames > 1
                ? Math.floorMod(StripFrames.index(System.currentTimeMillis() / 2, cape.frameDelayMs), frames) : 0;
        int width = FACE_WIDTH * scale;
        int height = FACE_HEIGHT * scale;
        McBridge.capeThumb(graphics, image.id(), area.centerX() - width / 2, area.centerY() - height / 2,
                width, height, image.width(), image.height(), frameWidth, frameHeight,
                vertical ? 0 : frame * frameWidth, vertical ? frame * frameHeight : 0);
    }
}
