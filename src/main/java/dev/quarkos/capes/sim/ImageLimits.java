package dev.quarkos.capes.sim;

import java.nio.ByteBuffer;

/** Check the PNG header before any native or ImageIO decoder allocates pixels. */
public final class ImageLimits {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private ImageLimits() { }

    public static boolean dimensions(int width, int height) {
        return width > 0 && height > 0 && width <= 8192 && height <= 8192
                && (long) width * height <= 16_777_216L;
    }

    public static boolean png(byte[] bytes) {
        if (bytes == null || bytes.length < 33 || bytes.length > MAX_BYTES) return false;
        ByteBuffer header = ByteBuffer.wrap(bytes);
        return header.getLong(0) == 0x89504e470d0a1a0aL && header.getInt(8) == 13
                && header.getInt(12) == 0x49484452 && dimensions(header.getInt(16), header.getInt(20));
    }
}
