package dev.quarkos.capes.client.render;

import java.util.function.BooleanSupplier;

/** GUI submission and feature execution share this scope, including deferred 1.21.11 previews. */
public final class CapeRenderPass {
    public static int guiDepth;
    private static final BooleanSupplier SHADOW = shadowProbe();
    private CapeRenderPass() { }

    public static boolean allowed(boolean gui, boolean cameraEntity, boolean firstPerson, boolean shadow) {
        return gui || !cameraEntity || !firstPerson || shadow;
    }

    public static boolean worldDraw(boolean gui, boolean shadow) { return !gui && !shadow; }
    public static boolean shadow() { return SHADOW.getAsBoolean(); }

    private static BooleanSupplier shadowProbe() {
        for (String name : new String[]{"net.irisshaders.iris.api.v0.IrisApi", "net.coderbot.iris.api.v0.IrisApi"}) {
            try {
                Class<?> api = Class.forName(name);
                Object instance = api.getMethod("getInstance").invoke(null);
                var method = api.getMethod("isRenderingShadowPass");
                return () -> { try { return Boolean.TRUE.equals(method.invoke(instance)); } catch (ReflectiveOperationException e) { return false; } };
            } catch (ReflectiveOperationException | LinkageError ignored) { }
        }
        try {
            var field = Class.forName("net.optifine.shaders.Shaders").getField("isShadowPass");
            return () -> { try { return field.getBoolean(null); } catch (IllegalAccessException e) { return false; } };
        } catch (ReflectiveOperationException | LinkageError ignored) { return () -> false; }
    }
}
