package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.local.OwnerCape;
import net.minecraft.network.chat.Component;

/** Display choices preserve the existing persisted ids and physics values. */
public final class CapeChoices {
    public enum Style { FLAT, FRAMES, WAVE, MODEL }
    private CapeChoices() { }
    public static Style style() {
        return switch (OwnerCape.get().style) {
            case "frames" -> Style.FRAMES;
            case "wave" -> Style.WAVE;
            case "model" -> Style.MODEL;
            default -> Style.FLAT;
        };
    }
    public static String id(Enum<?> value) {
        return value.name().toLowerCase(java.util.Locale.ROOT);
    }
    public static Component name(Enum<?> value) {
        return Component.translatable("text.quarkos_capes.choice." + id(value));
    }
    public static Component help(Enum<?> value) {
        return Component.translatable("text.quarkos_capes.tooltip." + id(value));
    }
    public static <T extends Enum<T>> T next(T value) {
        T[] choices = value.getDeclaringClass().getEnumConstants();
        return choices[(value.ordinal() + 1) % choices.length];
    }
}
