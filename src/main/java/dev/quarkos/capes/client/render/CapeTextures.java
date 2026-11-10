package dev.quarkos.capes.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.net.LabyMark;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import dev.quarkos.capes.client.local.Samples;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Memory-only textures. Nothing from a provider is written to disk. */
public final class CapeTextures {
    public record Image(Identifier id, int width, int height, boolean vertical, int frames, boolean elytra, boolean capeBox) {
    }

    private static final Map<String, Image> IMAGES = new ConcurrentHashMap<>();
    /** One vanilla-sized frame texture per strip frame, when {@code capeBox} is set. */
    private static final Map<String, Identifier[]> BOX_FRAMES = new ConcurrentHashMap<>();
    private static int salt;
    private static final Identifier BANDS = McBridge.id("textures/cape/bands.png");
    private static final Identifier SLIDE = McBridge.id("textures/cape/slide.png");
    private static final Identifier CLOAK = McBridge.id("textures/cape/cloak.png");

    private CapeTextures() {
    }

    public static Image get(String key) {
        return IMAGES.get(key);
    }

    public static Image upload(String key, byte[] png, boolean animated, boolean laby) {
        return upload(key, png, animated, laby, false);
    }

    /**
     * {@code capeBox} means the png is a vanilla cape atlas. Padding it to a power of two
     * would move the texels the cape box samples, so it is uploaded at its real size.
     * A vertical strip is split into one texture per frame so the box can show each frame.
     */
    public static Image upload(String key, byte[] png, boolean animated, boolean laby, boolean capeBox) {
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        NativeImage read = null;
        try {
            if (!dev.quarkos.capes.sim.ImageLimits.png(png)) return null;
            read = NativeImage.read(new ByteArrayInputStream(png));
            if (laby && placeholder(read)) {
                clear(key);
                return null;
            }
            boolean vertical = animated && read.getWidth() / 2 > 0 && read.getHeight() % (read.getWidth() / 2) == 0
                    && read.getHeight() / (read.getWidth() / 2) > 1;
            int frames = vertical ? read.getHeight() / (read.getWidth() / 2) : 1;
            if (frames > 256) return null;
            int width = read.getWidth();
            int height = read.getHeight();
            if (capeBox && frames > 1) {
                Identifier[] parts = splitFrames(key, read, frames);
                clear(key);
                BOX_FRAMES.put(key, parts);
                Image stored = new Image(parts[0], width, height / frames, true, frames, false, true);
                IMAGES.put(key, stored);
                return stored;
            }
            // Release the previous texture only after a replacement has decoded successfully.
            Identifier bundled = bundled(png);
            Identifier id;
            if (bundled != null) {
                id = bundled;
            } else {
                NativeImage image = read;
                read = null;
                id = McBridge.id("cape/" + Integer.toUnsignedString(key.hashCode()) + "_" + (salt++));
                register(id, image, key);
            }
            boolean elytra = frames == 1 && width == height * 2;
            Image stored = new Image(id, width, height, vertical, frames, elytra, capeBox);
            clear(key);
            IMAGES.put(key, stored);
            return stored;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (read != null) read.close();
        }
    }

    /** The frame the vanilla cape box should sample. A single image returns its own id. */
    public static Identifier frame(String key, int index) {
        Identifier[] frames = BOX_FRAMES.get(key);
        if (frames != null && frames.length > 0) {
            return frames[Math.floorMod(index, frames.length)];
        }
        Image image = IMAGES.get(key);
        return image == null ? null : image.id();
    }

    public static Identifier bandsTexture() {
        return BANDS;
    }

    public static Identifier slideTexture() {
        return SLIDE;
    }

    public static Identifier cloakTexture() {
        return CLOAK;
    }

    public static void clear(String key) {
        CapeBook.ELYTRA_SKINS.clear();
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        Image old = IMAGES.remove(key);
        Identifier[] frames = BOX_FRAMES.remove(key);
        if (frames != null) {
            for (Identifier id : frames) release(id);
        } else if (old != null) release(old.id());
    }

    private static void release(Identifier id) {
        // Bundled sample resources belong to Minecraft's resource manager.
        if (id != null && id.getPath().startsWith("cape/")) Minecraft.getInstance().getTextureManager().release(id);
    }

    private static Identifier[] splitFrames(String key, NativeImage strip, int frames) {
        int width = strip.getWidth();
        int frameHeight = strip.getHeight() / frames;
        Identifier[] ids = new Identifier[frames];
        try {
            for (int frame = 0; frame < frames; frame++) {
                NativeImage part = new NativeImage(width, frameHeight, true);
                try {
                    int origin = frame * frameHeight;
                    for (int y = 0; y < frameHeight; y++) {
                        for (int x = 0; x < width; x++) McBridge.setPixel(part, x, y, McBridge.pixel(strip, x, origin + y));
                    }
                } catch (RuntimeException failure) { part.close(); throw failure; }
                Identifier id = McBridge.id("cape/" + Integer.toUnsignedString(key.hashCode()) + "_" + (salt++) + "_f" + frame);
                register(id, part, key + "/" + frame);
                ids[frame] = id;
            }
            return ids;
        } catch (RuntimeException failure) {
            for (Identifier id : ids) release(id);
            throw failure;
        }
    }

    /** Takes ownership of the image even when texture creation or registration fails. */
    private static void register(Identifier id, NativeImage image, String name) {
        DynamicTexture texture = null;
        try {
            texture = McBridge.dynamic(image, name);
            Minecraft.getInstance().getTextureManager().register(id, texture);
        } catch (RuntimeException failure) {
            if (texture != null) texture.close(); else image.close();
            throw failure;
        }
    }

    private static Identifier bundled(byte[] png) {
        if (Arrays.equals(png, Samples.bands())) {
            return bandsTexture();
        }
        if (Arrays.equals(png, Samples.slide())) {
            return slideTexture();
        }
        if (Arrays.equals(png, Samples.cloakPng())) {
            return cloakTexture();
        }
        return null;
    }

    private static boolean placeholder(NativeImage image) {
        if (image.getWidth() < 1 || image.getHeight() < 1) {
            return false;
        }
        int[] pixels = new int[Math.min(4, image.getWidth() * image.getHeight())];
        int i = 0;
        for (int y = 0; y < image.getHeight() && i < 4; y++) {
            for (int x = 0; x < image.getWidth() && i < 4; x++) {
                pixels[i++] = McBridge.pixel(image, x, y);
            }
        }
        return LabyMark.placeholder(pixels);
    }

    private static NativeImage pot(NativeImage src) {
        int w = pot(src.getWidth());
        int h = pot(src.getHeight());
        if (w == src.getWidth() && h == src.getHeight()) {
            return src;
        }
        NativeImage out = new NativeImage(w, h, true);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                McBridge.setPixel(out, x, y, McBridge.pixel(src, x, y));
            }
        }
        src.close();
        return out;
    }

    private static int pot(int value) {
        int n = 1;
        while (n < value) {
            n <<= 1;
        }
        return n;
    }
}
