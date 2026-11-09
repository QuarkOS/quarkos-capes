package dev.quarkos.capes.client.harness;

import com.mojang.authlib.GameProfile;
import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.render.CapeTextures;
import dev.quarkos.capes.client.screen.CapeScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Isolated, offline audit scenes. No entry point is active without the harness flag. */
public final class AuditHarness {
    private static final String MODE = System.getProperty("quarkos_capes.only", "");
    private static final String[] TABS = {"mycape", "style", "physics", "sources", "gallery", "advanced"};
    private static final List<String> EXPECTED = new ArrayList<>();
    private static final List<RemotePlayer> PLAYERS = new ArrayList<>();
    private static int tick;
    private static int shot;
    private static int doneAt;
    private static boolean started;
    private static boolean measuring;
    private static int window;
    private static long began;
    private static long previous;
    private static long allocated;
    private static long allocationStart;
    private static int frames;
    private static long[] frameTimes;
    private static com.sun.management.ThreadMXBean bean;

    private AuditHarness() { }

    public static boolean selected() {
        return Boolean.getBoolean("quarkos_capes.harness") && !MODE.isEmpty();
    }

    public static void tick(Minecraft client) {
        if (!selected()) return;
        if (!List.of("screen", "inventory", "world", "bench").contains(MODE)) {
            throw new IllegalArgumentException("Unknown audit mode: " + MODE);
        }
        if (!started) {
            started = true;
            ClientSettings.get().localOnly = true;
            ClientSettings.get().enableLabyMod = false;
            client.options.framerateLimit().set("bench".equals(MODE) ? 260 : 60);
            client.options.enableVsync().set(false);
            client.options.hideGui = false;
            McBridge.showCape(client.options);
            client.player.connection.sendCommand("tp @s 0.5 -60 0.5 0 0");
            McBridge.prepareWorld(client);
            equip(0);
            if ("bench".equals(MODE)) armBench(client);
        }
        tick++;
        if ("bench".equals(MODE)) {
            if (tick == 40) capture(client, "bench-scene");
            walkPlayers(client);
            return;
        }
        if (doneAt != 0) {
            if (tick - doneAt == 1 && "inventory".equals(MODE)) HarnessChecks.textureReplacement(directory());
            if (tick - doneAt < 30) return;
            boolean ready = EXPECTED.stream().allMatch(AuditHarness::written);
            if (ready) {
                System.out.println("quarkos_capes audit " + MODE + " ok");
                System.exit(0);
            }
            if (tick - doneAt > 600) throw new IllegalStateException("Missing audit screenshots: " + EXPECTED);
            return;
        }
        if (tick < 40 || tick % 30 != 0) return;
        if ("screen".equals(MODE)) {
            if ((shot & 1) == 0) openTab(client, shot / 2);
            else capture(client, "screen-" + TABS[shot / 2]);
            if (++shot == 12) doneAt = tick;
        } else if ("inventory".equals(MODE)) {
            switch (shot++) {
                case 0 -> { equip(1); client.setScreen(new InventoryScreen(client.player)); }
                case 1 -> capture(client, "inventory");
                case 2 -> openTab(client, 0);
                case 3 -> { capture(client, "screen-preview"); doneAt = tick; }
                default -> { }
            }
        } else {
            world(client);
        }
    }

    private static void openTab(Minecraft client, int tab) {
        // Fabric's public category selector is already present on integ-base.
        //? if fabric {
        client.setScreen(dev.quarkos.capes.client.screen.CapeScreen.create(null,
                Component.translatable("text.quarkos_capes.tab." + (tab == 0 ? "my_cape" : TABS[tab]))));
        //?} else {
        /*Screen screen = CapeScreens.open(null);
        client.setScreen(screen);
        try {
            if (screen.getClass().getSimpleName().equals("ForgeCapeScreen")) {
                var field = screen.getClass().getDeclaredField("tab");
                field.setAccessible(true);
                field.setInt(screen, tab);
                var rebuild = screen.getClass().getDeclaredMethod("rebuild");
                rebuild.setAccessible(true);
                rebuild.invoke(screen);
            } else {
                var field = screen.getClass().getField("selectedCategoryIndex");
                field.setInt(screen, tab);
                client.setScreen(screen);
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot select settings tab " + tab, failure);
        }
        *///?}
    }

    private static void world(Minecraft client) {
        // Two camera angles, standing and walking, for each of four styles.
        if (shot < 32) {
            int scene = shot / 2;
            if ((shot & 1) == 0) {
                equip(scene / 4);
                CapeBook.preview = false;
                CapeBook.draft = null;
                client.setScreen(null);
                client.options.hideGui = true;
                McBridge.thirdPerson(client.options);
                client.options.keyUp.setDown((scene & 2) != 0);
                ShotView.frame((scene & 1) == 0 ? 0 : 40, 8, 3.5f);
            } else {
                String[] styles = {"flat", "wavy", "cube", "animated"};
                capture(client, "world-" + styles[scene / 4] + "-" + ((scene & 2) == 0 ? "standing-" : "walking-")
                        + ((scene & 1) == 0 ? "back" : "three-quarter"));
                client.options.keyUp.setDown(false);
            }
            shot++;
        } else {
            switch (shot++) {
                case 32 -> {
                    equip(2);
                    client.player.connection.sendCommand("time set midnight");
                    ShotView.frame(40, 8, 3.5f);
                }
                case 33 -> capture(client, "world-cube-night");
                case 34 -> {
                    client.player.connection.sendCommand("time set day");
                    equip(1); client.player.setInvisible(true);
                }
                case 35 -> capture(client, "world-wavy-invisible");
                case 36 -> {
                    client.player.setInvisible(false);
                    client.player.connection.sendCommand("item replace entity @s armor.chest with minecraft:elytra");
                }
                case 37 -> capture(client, "world-wavy-elytra");
                case 38 -> {
                    client.player.connection.sendCommand("item replace entity @s armor.chest with minecraft:diamond_chestplate");
                    client.options.keyShift.setDown(true);
                }
                case 39 -> capture(client, "world-wavy-crouch-armor");
                case 40 -> {
                    client.options.keyShift.setDown(false);
                    client.player.connection.sendCommand("gamemode spectator");
                }
                case 41 -> capture(client, "world-wavy-spectator");
                case 42 -> {
                    client.player.connection.sendCommand("gamemode creative");
                    client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    ShotView.clear();
                }
                case 43 -> { capture(client, "world-first-person"); doneAt = tick; }
                default -> { }
            }
        }
    }

    private static OwnerCape sample(int style) {
        OwnerCape cape = switch (style) {
            case 2 -> OwnerCape.sampleCloak();
            case 3 -> OwnerCape.sampleSlide();
            default -> OwnerCape.sampleBands();
        };
        if (style == 1) cape.style = "wave";
        return cape;
    }

    private static void equip(int style) {
        OwnerCape.set(sample(style));
        CapeBook.draft = OwnerCape.get();
        CapeBook.previewBack = true;
    }

    private static void capture(Minecraft client, String name) {
        HarnessChecks.catalogHidden(client.screen);
        if (dev.quarkos.capes.client.net.CapeHttp.sent != 0) throw new IllegalStateException("Offline audit sent a request");
        EXPECTED.add(name);
        try { Files.deleteIfExists(directory().resolve(name + ".png")); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        McBridge.shot(client, directory().resolve(name + ".png"));
    }

    private static boolean written(String name) {
        try { return Files.size(directory().resolve(name + ".png")) > 1000; }
        catch (java.io.IOException e) { return false; }
    }

    private static Path directory() {
        return Path.of(System.getProperty("quarkos_capes.captures", "build/captures"));
    }

    private static int number(String key, int fallback) {
        String value = System.getProperty(key, System.getenv(key));
        return value == null ? fallback : Integer.parseInt(value);
    }

    private static void armBench(Minecraft client) {
        frameTimes = new long[1_000_000];
        int count = Math.clamp(number("QUARKOS_CAPES_BENCH_PLAYERS", 16), 1, 512);
        ClientSettings.get().localOnly = false;
        ClientSettings.get().showOthers = true;
        // No network: all entities have complete accepted metadata and bundled textures;
        // the tick entry skips CapeHttp while the isolated audit is active.
        OwnerCape bare = new OwnerCape();
        OwnerCape.set(bare);
        McBridge.thirdPerson(client.options);
        client.options.hideGui = true;
        client.options.fov().set(70);
        ShotView.frame(0, 12, 10);
        for (int i = 0; i < count; i++) {
            UUID id = UUID.nameUUIDFromBytes(("capes-bench-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            RemotePlayer player = new RemotePlayer(client.level, new GameProfile(id, "CapeBench" + i)) {
                @Override public boolean isModelPartShown(PlayerModelPart part) { return true; }
            };
            player.setId(1_000_000 + i);
            client.level.addEntity(player);
            PLAYERS.add(player);
            OwnerCape cape = sample(i % 4);
            CapeTextures.upload(CapeBook.textureKey(id), cape.png, false, false);
            CapeBook.remote(id, new CapeBook.Remote(cape.style, cape.animateFrames, cape.frameCount,
                    cape.frameWidth, cape.frameHeight, cape.frameDelayMs, cape.modelJson, "accepted"));
        }
        var management = ManagementFactory.getThreadMXBean();
        if (management instanceof com.sun.management.ThreadMXBean supported && supported.isThreadAllocatedMemorySupported()) {
            bean = supported;
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        began = System.nanoTime();
    }

    private static void walkPlayers(Minecraft client) {
        for (int i = 0; i < PLAYERS.size(); i++) {
            RemotePlayer player = PLAYERS.get(i);
            double angle = i * Math.PI * 2 / PLAYERS.size();
            double sway = Math.sin(tick * 0.04 + i) * 0.4;
            player.setPos(0.5 + Math.cos(angle) * 3.0 + sway, client.player.getY(), 2.0 + Math.sin(angle) * 1.5);
            float yaw = 15f * (float) Math.sin(tick * 0.03 + i);
            player.setYRot(yaw);
            player.setYBodyRot(yaw);
            player.setYHeadRot(yaw);
            //? if <1.21.11 {
            /*player.walkAnimation.update(0.3f, 0.4f);
            *///?} else {
            player.walkAnimation.update(0.3f, 0.4f, 1f);
            //?}
        }
    }

    /** Called once per world frame by the existing harness camera hook, on the render thread. */
    public static void frame() {
        try { measureFrame(); }
        catch (RuntimeException failure) { ClientHarness.fail(failure.getMessage()); }
    }

    private static void measureFrame() {
        if (!selected() || !"bench".equals(MODE) || !started) return;
        long now = System.nanoTime();
        if (!measuring) {
            if (now - began < 5_000_000_000L) return;
            measuring = true;
            previous = began = now;
            frames = 0;
            allocationStart = bean == null ? 0 : bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
            return;
        }
        if (frames == frameTimes.length) throw new IllegalStateException("Benchmark frame capacity exceeded");
        frameTimes[frames++] = now - previous;
        previous = now;
        allocated = bean == null ? 0 : bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocationStart;
        if (now - began < Math.max(1, number("QUARKOS_CAPES_BENCH", 20)) * 1_000_000_000L) return;
        double seconds = (now - began) / 1e9;
        Arrays.sort(frameTimes, 0, frames);
        String line = String.format(Locale.ROOT,
                "%s players=%d frames=%d seconds=%.3f fps_avg=%.2f frame_ms_p50=%.3f frame_ms_p95=%.3f alloc_bytes_per_frame=%.2f%n",
                window == 0 ? "CAPES_BENCH" : "CAPES_BENCH_OFF", PLAYERS.size(), frames, seconds, frames / seconds,
                frameTimes[(frames - 1) / 2] / 1e6, frameTimes[(int) ((frames - 1) * 0.95)] / 1e6,
                bean == null ? -1.0 : allocated / (double) frames);
        System.out.print(line);
        try {
            Files.createDirectories(directory());
            Files.writeString(directory().resolve("bench.txt"), line,
                    window == 0 ? java.nio.file.StandardOpenOption.CREATE : java.nio.file.StandardOpenOption.APPEND,
                    window == 0 ? java.nio.file.StandardOpenOption.TRUNCATE_EXISTING : java.nio.file.StandardOpenOption.WRITE);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
        if (window++ == 1) System.exit(0);
        ClientSettings.get().showOthers = false;
        measuring = false;
        began = now;
    }
}
