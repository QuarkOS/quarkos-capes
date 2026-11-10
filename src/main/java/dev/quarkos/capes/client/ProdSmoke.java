package dev.quarkos.capes.client;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.render.CapeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Production-client check used by {@code scripts/prod-smoke.sh}.
 * Stays idle unless {@code -Dquarkos_capes.prodSmoke=true}.
 */
public final class ProdSmoke {
    private static final String SHOT = System.getProperty("quarkos_capes.prodSmokeShot", "");
    private static int ticks;
    private static int played;
    private static int state;
    private static boolean armed;

    private ProdSmoke() {
    }

    public static void tick(Minecraft client) {
        if (!armed) {
            armed = true;
            if (SHOT.isBlank()) {
                fail("quarkos_capes.prodSmokeShot is not set");
            }
            ClientSettings.get().localOnly = true;
            ClientSettings.get().enableLabyMod = false;
            client.options.pauseOnLostFocus = false;
            client.options.onboardAccessibility = false;
            //? if >=1.21 {
            client.options.menuBackgroundBlurriness().set(0);
            //?}
            client.options.guiScale().set(Integer.getInteger("quarkos_capes.guiScale", 2));
            client.options.renderDistance().set(2);
            client.options.simulationDistance().set(5);
            client.options.framerateLimit().set(20);
            client.getWindow().setWindowed(
                    Integer.getInteger("quarkos_capes.width", 1280),
                    Integer.getInteger("quarkos_capes.height", 720)
            );
        }
        ticks++;
        try {
            switch (state) {
                case 0 -> title(client);
                case 1 -> open(client);
                case 2 -> play(client);
                case 3 -> finish();
                default -> { }
            }
        } catch (Throwable failure) {
            mark("threw " + failure);
            fail(failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private static void mark(String line) {
        if (SHOT.isBlank()) {
            return;
        }
        try {
            Files.writeString(
                    Path.of(SHOT + ".trace"),
                    line + "\n",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (java.io.IOException ignored) {
            // The smoke report still has the log line when the trace cannot be written.
        }
    }

    private static void title(Minecraft client) {
        Screen screen = McBridge.currentScreen(client);
        if (screen != null && !(screen instanceof TitleScreen)) {
            String name = screen.getClass().getName().toLowerCase();
            if (name.contains("accessibility")) {
                //? if <1.21 {
                /*client.options.onboardAccessibility = false;
                *///?} else {
                client.options.onboardingAccessibilityFinished();
                //?}
                McBridge.setScreen(client, new TitleScreen());
                return;
            }
            if (name.contains("notice") || name.contains("safety") || name.contains("confirm")) {
                press(screen, "continue", "proceed", "yes", "ok");
                return;
            }
        }
        if (!(McBridge.currentScreen(client) instanceof TitleScreen) || client.getOverlay() != null) {
            if (ticks == 300) {
                // 1.20.1 can sit on the loading overlay after resources are ready.
                client.setOverlay(null);
                if (!(McBridge.currentScreen(client) instanceof TitleScreen)) {
                    McBridge.setScreen(client, new TitleScreen());
                }
            }
            if (ticks > 20 * 120) {
                Screen stuck = McBridge.currentScreen(client);
                String overlay = client.getOverlay() == null ? "none" : client.getOverlay().getClass().getName();
                String name = stuck == null ? "none" : stuck.getClass().getName();
                fail("title screen did not open (" + name + ", overlay " + overlay + ")");
            }
            return;
        }
        state = 1;
        ticks = 0;
    }

    private static void open(Minecraft client) {
        if (!(McBridge.currentScreen(client) instanceof CreateWorldScreen)) {
            Screen screen = McBridge.currentScreen(client);
            if (screen != null && !(screen instanceof TitleScreen)) {
                press(screen, "create", "proceed", "yes", "continue");
            }
            //? if <1.21.9 {
            /*CreateWorldScreen.openFresh(client, McBridge.currentScreen(client));
            *///?} else {
            CreateWorldScreen.openFresh(client, () -> McBridge.setScreen(client, new TitleScreen()));
            //?}
            if (ticks > 20 * 60) {
                fail("create-world screen did not open");
            }
            return;
        }
        CreateWorldScreen create = (CreateWorldScreen) McBridge.currentScreen(client);
        var presets = create.getUiState().getNormalPresetList();
        if (presets.isEmpty()) {
            if (ticks > 20 * 60) {
                fail("world presets never loaded");
            }
            return;
        }
        WorldCreationUiState ui = create.getUiState();
        ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
        McBridge.allowCommands(ui);
        ui.setName("prod-smoke");
        presets.stream().filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(ui::setWorldType);
        create.onCreate();
        state = 2;
        ticks = 0;
        played = 0;
    }

    private static void play(Minecraft client) {
        if (client.player == null || client.level == null) {
            Screen screen = McBridge.currentScreen(client);
            if (screen != null && !(screen instanceof CreateWorldScreen)) {
                press(screen, "yes", "proceed", "continue", "ok");
            }
            if (ticks > 20 * 180) {
                fail("world did not load");
            }
            return;
        }
        // Count only ticks where the player exists. World creation can burn the earlier counter.
        played++;
        client.options.hideGui = true;
        client.options.fov().set(30);
        McBridge.showCape(client.options);
        McBridge.thirdPerson(client.options);
        if (played == 20) {
            mark("prepare");
            McBridge.prepareWorld(client);
            mark("cloak");
            OwnerCape.set(OwnerCape.sampleCloak());
            CapeBook.preview = false;
            CapeBook.draft = null;
            McBridge.setScreen(client, null);
            mark("tp");
            client.player.connection.sendCommand("tp @s 0.5 -60 0.5 0 0");
            McBridge.clearChat(client);
            McBridge.clearToasts(client);
            mark("ready");
        }
        if (played == 100) {
            mark("shot");
            McBridge.clearChat(client);
            McBridge.shot(client, Path.of(SHOT));
            mark("shot-done");
        }
        if (played > 100) {
            try {
                if (Files.size(Path.of(SHOT)) > 1000) {
                    state = 3;
                }
            } catch (java.io.IOException ignored) {
                // The 1.21.5+ screenshot is written on the render thread.
            }
            if (played > 100 + 20 * 30) {
                fail("screenshot was not written");
            }
        }
    }

    private static void finish() {
        mark("ok");
        // NeoForge does not copy stdout into latest.log, so the smoke script reads the game log.
        com.mojang.logging.LogUtils.getLogger().info("quarkos_capes prod smoke ok");
        System.out.println("quarkos_capes prod smoke ok");
        System.exit(0);
    }

    private static void press(Screen screen, String... words) {
        for (var child : screen.children()) {
            if (child instanceof Button button) {
                String label = button.getMessage().getString().toLowerCase();
                for (String word : words) {
                    if (label.contains(word)) {
                        McBridge.press(button);
                        return;
                    }
                }
            }
        }
    }

    private static void fail(String message) {
        mark("fail: " + message);
        com.mojang.logging.LogUtils.getLogger().error("quarkos_capes prod smoke fail: " + message);
        System.err.println("quarkos_capes prod smoke fail: " + message);
        System.exit(1);
    }
}
