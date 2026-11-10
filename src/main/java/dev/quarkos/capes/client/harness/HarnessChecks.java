package dev.quarkos.capes.client.harness;

//? if dev_catalog {
/*import dev.quarkos.capes.client.net.LabyCatalog;
*///?}
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Assertions against real widgets, including Cloth categories that are not currently selected. */
final class HarnessChecks {
    private HarnessChecks() { }

    static void textureReplacement(java.nio.file.Path directory) {
        int baseline = dynamicTextures();
        byte[] original = dev.quarkos.capes.client.local.Samples.bands();
        // Trailing PNG bytes leave every sample pixel unchanged but force a dynamic fixture.
        byte[] dynamic = java.util.Arrays.copyOf(original, original.length + 1);
        for (int i = 0; i < 8; i++) {
            dynamic[dynamic.length - 1] = (byte) i;
            if (dev.quarkos.capes.client.render.CapeTextures.upload("audit-replacement", dynamic, false, false) == null)
                throw new IllegalStateException("Could not upload dynamic texture fixture");
        }
        int retained = dynamicTextures() - baseline;
        dev.quarkos.capes.client.render.CapeTextures.clear("audit-replacement");
        int afterClear = dynamicTextures() - baseline;
        String evidence = "CAPES_TEXTURES replacements=8 retained=" + retained + " after_clear=" + afterClear + "\n";
        System.out.print(evidence);
        try {
            java.nio.file.Files.createDirectories(directory);
            java.nio.file.Files.writeString(directory.resolve("texture-replacement.txt"), evidence);
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    private static int dynamicTextures() {
        Object manager = net.minecraft.client.Minecraft.getInstance().getTextureManager();
        java.util.Set<String> identifiers = new java.util.HashSet<>();
        try {
            for (var field : manager.getClass().getDeclaredFields()) {
                if (!java.util.Map.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                if (field.get(manager) instanceof java.util.Map<?, ?> map) for (Object key : map.keySet()) {
                    if (String.valueOf(key).startsWith("quarkos_capes:cape/")) identifiers.add(key.toString());
                }
            }
        } catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
        return identifiers.size();
    }

    static void catalogHidden(Screen screen) {
        if (!Boolean.getBoolean("quarkos_capes.harness") || screen == null) return;
//? if dev_catalog {
        /*if (LabyCatalog.enabled()) return;
*///?}
        inspect(screen);
        try {
            Object categories = screen.getClass().getMethod("getCategorizedEntries").invoke(screen);
            if (categories instanceof java.util.Map<?, ?> map) for (Object entries : map.values()) {
                if (entries instanceof Iterable<?> rows) for (Object row : rows) {
                    var method = row.getClass().getMethod("getFieldName");
                    method.setAccessible(true);
                    if (method.invoke(row) instanceof Component label) check(label);
                    if (row instanceof GuiEventListener listener) inspect(listener);
                }
            }
        } catch (NoSuchMethodException ordinaryScreen) {
            // Vanilla and native Forge screens have no Cloth category model.
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not inspect catalog widgets", failure);
        }
    }

    private static void inspect(GuiEventListener listener) {
        if (listener instanceof AbstractWidget widget) check(widget.getMessage());
        if (listener instanceof ContainerEventHandler container) for (var child : container.children()) inspect(child);
    }

    private static void check(Component label) {
        String value = label.getString();
        if (value.toLowerCase(java.util.Locale.ROOT).contains("labymod catalog"))
            throw new IllegalStateException("Dev catalog control visible: " + value);
    }
}
