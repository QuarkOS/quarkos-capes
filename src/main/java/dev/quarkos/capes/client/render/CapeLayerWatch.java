package dev.quarkos.capes.client.render;

import net.minecraft.resources.Identifier;

/**
 * What the cape layer last did for the local player.
 * The client gametest reads this. Screenshot pixel counts do not.
 */
public final class CapeLayerWatch {
    public static int localCalls;
    public static Identifier localTexture;
    /** Non-null only while 1.21.1 is inside {@code CapeLayer.render} and should use our texture. */
    public static Identifier textureOverride;

    private CapeLayerWatch() {
    }

    public static void reset() {
        localCalls = 0;
        localTexture = null;
        textureOverride = null;
    }

    public static void local(Identifier texture) {
        localCalls++;
        localTexture = texture;
    }
}
