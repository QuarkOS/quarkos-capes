package dev.quarkos.capes.sim;

/**
 * Picks a frame from a strip. Equal delays use {@code (ageMs / delay) % count}.
 * Unequal delays walk the timeline, which matches that formula when every delay is the same.
 */
public final class StripFrames {
    private StripFrames() {
    }

    public static int index(long ageMs, int[] frameDelayMs) {
        if (frameDelayMs == null || frameDelayMs.length <= 1) {
            return 0;
        }
        int count = frameDelayMs.length;
        int first = frameDelayMs[0];
        boolean uniform = first > 0;
        long total = 0;
        for (int delay : frameDelayMs) {
            if (delay != first) {
                uniform = false;
            }
            total += delay;
        }
        if (total <= 0) {
            return 0;
        }
        if (uniform) {
            return (int) Math.floorMod(ageMs / first, count);
        }
        long cursor = Math.floorMod(ageMs, total);
        for (int i = 0; i < count; i++) {
            if (cursor < frameDelayMs[i]) {
                return i;
            }
            cursor -= frameDelayMs[i];
        }
        return 0;
    }
}
