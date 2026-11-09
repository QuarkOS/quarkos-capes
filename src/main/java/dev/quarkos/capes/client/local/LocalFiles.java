package dev.quarkos.capes.client.local;

//? if forge {
/*import net.minecraftforge.fml.loading.FMLPaths;
*///?} else {
import net.fabricmc.loader.api.FabricLoader;
//?}

import java.io.IOException;
import java.nio.file.Path;

public final class LocalFiles {
    private static final java.util.concurrent.ExecutorService WRITER = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "quarkos-capes-save");
        thread.setDaemon(true); return thread;
    });
    private static volatile LocalSnapshot last;
    private static LocalSnapshot pending;
    private static boolean writing;
    private static boolean wasSettings;
    private static final java.util.List<java.util.function.Consumer<Boolean>> CALLBACKS = new java.util.ArrayList<>();

    private LocalFiles() {
    }

    public static Path configDir() {
        //? if forge {
        /*return FMLPaths.CONFIGDIR.get();
        *///?} else {
        return FabricLoader.getInstance().getConfigDir();
        //?}
    }

    public static Path config() {
        return configDir().resolve("quarkos_capes.json");
    }

    public static Path folder() {
        return configDir().resolve("quarkos_capes");
    }

    public static void load() {
        last = null;
        ClientSettings.load(config());
        OwnerCape.set(OwnerCape.load(folder().resolve("local.png"), folder().resolve("local.json")));
    }

    private static LocalSnapshot snapshot() {
        return new LocalSnapshot(ClientSettings.get().json(), dev.quarkos.capes.sim.JsonValues.write(OwnerCape.get().meta()), OwnerCape.get().png);
    }

    public static void autosave(boolean settings) {
        if (Boolean.getBoolean("quarkos_capes.harness")) return;
        if (!settings && !wasSettings) return;
        wasSettings = settings;
        LocalSnapshot captured = snapshot();
        if (!captured.equals(last)) { last = captured; enqueue(captured, null); }
    }

    public static void saveAsync(java.util.function.Consumer<Boolean> done) {
        LocalSnapshot captured = snapshot();
        last = captured;
        enqueue(captured, done);
    }

    private static synchronized void enqueue(LocalSnapshot captured, java.util.function.Consumer<Boolean> done) {
        pending = captured;
        if (done != null) {
            if (CALLBACKS.size() >= 64) { done.accept(false); return; }
            CALLBACKS.add(done);
        }
        if (writing) return;
        writing = true;
        Path config = config(), folder = folder();
        WRITER.execute(() -> drain(config, folder));
    }

    private static void drain(Path config, Path folder) {
        while (true) {
            LocalSnapshot captured;
            java.util.List<java.util.function.Consumer<Boolean>> callbacks;
            synchronized (LocalFiles.class) {
                captured = pending;
                if (captured == null) { writing = false; return; }
                pending = null;
                callbacks = java.util.List.copyOf(CALLBACKS); CALLBACKS.clear();
            }
            boolean ok;
            try { captured.write(config, folder); ok = true; }
            catch (IOException failure) { ok = false; }
            boolean success = ok;
            if (!success) last = null; // Retry a failed autosave on the next settings tick.
            if (!callbacks.isEmpty()) net.minecraft.client.Minecraft.getInstance().execute(() -> callbacks.forEach(done -> done.accept(success)));
        }
    }

    public static void save() throws IOException {
        ClientSettings.get().save(config());
        OwnerCape.get().save(folder().resolve("local.png"), folder().resolve("local.json"));
    }
}
