package dev.quarkos.capes.client.net;

import java.util.UUID;

/**
 * LabyMod's empty cape is the image whose first four pixels form this UUID.
 * The read is the same 4-int layout Minecraft uses for an int array UUID.
 */
public final class LabyMark {
    public static final String PLACEHOLDER = "ff305f81-ff30-5f90-ff30-5f90ff305f90";

    private LabyMark() {
    }

    public static boolean placeholder(int[] pixels) {
        if (pixels == null || pixels.length < 4) {
            return false;
        }
        long most = ((long) pixels[0] << 32) | (pixels[1] & 0xFFFFFFFFL);
        long least = ((long) pixels[2] << 32) | (pixels[3] & 0xFFFFFFFFL);
        return PLACEHOLDER.equals(new UUID(most, least).toString());
    }
}
