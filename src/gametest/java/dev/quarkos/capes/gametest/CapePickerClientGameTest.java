package dev.quarkos.capes.gametest;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.net.CapeHttp;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.screen.CapeActions;
import dev.quarkos.capes.client.screen.CapeScreen;
import dev.quarkos.capes.client.screen.GalleryScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.nio.file.Path;

@SuppressWarnings("UnstableApiUsage")
public class CapePickerClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("quarkos_capes.skipShots")) {
            return;
        }
        context.runOnClient(client -> client.options.pauseOnLostFocus = false);
        context.getInput().resizeWindow(1280, 720);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.options.renderDistance().set(2);
            client.options.simulationDistance().set(5);
            client.options.framerateLimit().set(20);
            client.options.menuBackgroundBlurriness().set(0);
        });
        try (TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(settings -> {
                    settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                    settings.setAllowCommands(true);
                    settings.getNormalPresetList().stream()
                            .filter(entry -> entry.preset().is(WorldPresets.FLAT))
                            .findFirst()
                            .ifPresent(settings::setWorldType);
                })
                .create()) {
            world.getClientWorld().waitForChunksRender();
            world.getServer().runCommand("gamerule send_command_feedback false");
            world.getServer().runCommand("gamerule log_admin_commands false");
            world.getServer().runCommand("gamerule advance_time false");
            world.getServer().runCommand("time set day");
            context.runOnClient(McBridge::clearChat);
            context.waitTicks(20);
            int before = context.computeOnClient(client -> CapeBook.draws);
            context.runOnClient(client -> McBridge.setScreen(client, CapeScreen.create(null)));
            context.waitTicks(10);
            int opened = context.computeOnClient(client -> CapeBook.draws);
            if (opened <= before) {
                throw new AssertionError("preview did not render");
            }
            select(context, "text.quarkos_capes.sample.bands");
            select(context, "text.quarkos_capes.style.flat");
            context.runOnClient(client -> {
                CapeBook.previewBack = false;
                McBridge.setScreen(client, CapeScreen.create(null, Component.translatable("text.quarkos_capes.tab.style")));
            });
            context.waitTicks(8);
            shot(context, "cape-front");
            context.runOnClient(client -> {
                CapeBook.previewBack = true;
                McBridge.setScreen(client, CapeScreen.create(null, Component.translatable("text.quarkos_capes.tab.style")));
            });
            context.waitTicks(8);
            shot(context, "cape-back");
            context.waitTicks(5);
            shot(context, "flat-preview");
            select(context, "text.quarkos_capes.sample.slide");
            select(context, "text.quarkos_capes.style.frames");
            for (int i = 0; i < 6; i++) {
                context.waitTicks(6);
                shot(context, "frames-" + i);
            }
            select(context, "text.quarkos_capes.style.wave");
            for (int i = 0; i < 6; i++) {
                context.waitTicks(4);
                shot(context, "wave-preview-" + i);
            }
            select(context, "text.quarkos_capes.sample.cloak");
            select(context, "text.quarkos_capes.style.model");
            context.waitTicks(5);
            shot(context, "model-preview");
            context.runOnClient(client -> {
                client.options.fov().set(30);
                McBridge.setScreen(client, null);
                McBridge.thirdPerson(client.options);
            });
            world.getServer().runCommand("tp @p 0 200 0 180 12");
            context.runOnClient(McBridge::clearChat);
            context.waitTicks(10);
            shot(context, "model");
            select(context, "text.quarkos_capes.sample.bands");
            select(context, "text.quarkos_capes.style.flat");
            context.runOnClient(client -> {
                McBridge.setScreen(client, null);
                McBridge.thirdPerson(client.options);
            });
            context.waitTicks(8);
            shot(context, "flat", "cape-on-player");
            select(context, "text.quarkos_capes.sample.slide");
            select(context, "text.quarkos_capes.style.frames");
            context.runOnClient(client -> {
                McBridge.setScreen(client, null);
                McBridge.thirdPerson(client.options);
            });
            context.waitTicks(5);
            for (int i = 0; i < 6; i++) {
                context.waitTicks(5);
                shot(context, "frames-world-" + i);
            }
            select(context, "text.quarkos_capes.style.wave");
            context.runOnClient(client -> {
                McBridge.setScreen(client, null);
                McBridge.thirdPerson(client.options);
            });
            for (int i = 0; i < 6; i++) {
                context.waitTicks(4);
                shot(context, "wave-" + i);
            }
            int sent = context.computeOnClient(client -> CapeHttp.sent);
            context.runOnClient(client -> {
                McBridge.clearChat(client);
                McBridge.setScreen(client, GalleryScreen.create(null));
            });
            context.waitTicks(10);
            int after = context.computeOnClient(client -> CapeHttp.sent);
            if (after != sent) {
                throw new AssertionError("gallery sent a request while offline");
            }
            shot(context, "gallery", "gallery-quiet");
        }
    }

    private static void select(ClientGameTestContext context, String key) {
        int before = context.computeOnClient(client -> CapeBook.draws);
        context.runOnClient(client -> {
            CapeActions.applyKey(key);
            McBridge.setScreen(client, CapeScreen.create(null, Component.translatable(CapeActions.tabFor(key))));
        });
        context.waitTicks(4);
        int after = context.computeOnClient(client -> CapeBook.draws);
        if (after <= before) {
            throw new AssertionError("preview did not render after " + key);
        }
    }

    private static void shot(ClientGameTestContext context, String... names) {
        context.runOnClient(McBridge::clearChat);
        context.waitTicks(2);
        context.runOnClient(client -> {
            for (String name : names) {
                McBridge.shot(client, captures().resolve(name + ".png"));
            }
        });
        context.waitTicks(3);
    }

    private static Path captures() {
        String configured = System.getProperty("quarkos_capes.captures");
        if (configured == null || configured.isBlank()) {
            return Path.of("build/captures");
        }
        return Path.of(configured);
    }
}
