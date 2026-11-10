package dev.quarkos.capes.client.render;

import java.util.function.Predicate;

/** Decisions based on the installed layers, rather than another mod's presence. */
public final class CapeCompatibility {
    private CapeCompatibility() { }

    public static <T> boolean needsFallback(Iterable<T> layers, Predicate<T> capeLayer) {
        for (T layer : layers) if (capeLayer.test(layer)) return false;
        return true;
    }
}
