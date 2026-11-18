package dev.quarkos.capes.client.screen.ui;

/** The stored render mode and independent texture playback flag. */
public record CapeStyles(String mode, boolean animated) {
    public enum Chip { FLAT, ANIMATED, WAVY, MODEL }

    public boolean on(Chip chip) {
        return switch (chip) {
            case FLAT -> !"wave".equals(mode) && !"model".equals(mode);
            case ANIMATED -> animated;
            case WAVY -> "wave".equals(mode);
            case MODEL -> "model".equals(mode);
        };
    }

    public CapeStyles toggle(Chip chip) {
        String flat = animated ? "frames" : "flat";
        return switch (chip) {
            case FLAT -> on(Chip.FLAT) ? this : new CapeStyles(flat, animated);
            case ANIMATED -> new CapeStyles(on(Chip.FLAT) ? (animated ? "flat" : "frames") : mode, !animated);
            case WAVY -> new CapeStyles(on(chip) ? flat : "wave", animated);
            case MODEL -> new CapeStyles(on(chip) ? flat : "model", animated);
        };
    }
}
