package dev.quarkos.capes.client;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.net.SiteApi;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.render.CapeTextures;
import dev.quarkos.capes.client.render.PlayerFacts;
import dev.quarkos.capes.client.screen.BrowseScreen;
import dev.quarkos.capes.client.screen.CapeScreens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

/**
 * Store screenshots from a release jar. Idle unless {@code -Dquarkos_capes.store=true}.
 * Scenes never open Advanced, so the service URL field stays off screen.
 */
public final class StoreCapture {
    private static final String SCENE = System.getProperty("quarkos_capes.storeScene", "gallery");
    private static final Path DIR = Path.of(System.getProperty("quarkos_capes.storeDir", "docs/store/shots"));
    private static final String ROLE = System.getProperty("quarkos_capes.storeRole", "a");
    private static final Path SHARE = Path.of(System.getProperty("quarkos_capes.storeShare", "build/store-share"));
    private static int ticks;
    private static int phase;
    private static int step;
    private static int stepTicks;
    private static boolean armed;
    private static int shotAt = -1;
    private static int readyTicks;
    private static int poseTicks;
    private static float wantYaw = 180f;
    private static float wantPitch;
    private static double ground = Double.NaN;

    private StoreCapture() {
    }

    /**
     * Runs at the start of the client tick, before the movement packet. End-of-tick
     * yaw changes only the local camera; the other client kept drawing the spawn yaw.
     */
    public static void beforeTick(Minecraft client) {
        if (!"peer".equals(SCENE) || client.player == null) {
            return;
        }
        lockFacing(client.player, wantYaw, wantPitch);
    }

    public static void tick(Minecraft client) {
        if (!armed) {
            armed = true;
            ClientSettings.get().enableLabyMod = false;
            ClientSettings.get().localOnly = !"peer".equals(SCENE);
            ClientSettings.get().showOthers = true;
            client.options.pauseOnLostFocus = false;
            client.options.onboardAccessibility = false;
            //? if >=1.21 {
            client.options.menuBackgroundBlurriness().set(0);
            //?}
            client.options.guiScale().set(Integer.getInteger("quarkos_capes.guiScale", 2));
            client.options.renderDistance().set("shader".equals(SCENE) ? 12 : 8);
            client.options.simulationDistance().set(6);
            client.options.framerateLimit().set(60);
            client.options.fov().set(70);
            client.getWindow().setWindowed(
                    Integer.getInteger("quarkos_capes.width", 1920),
                    Integer.getInteger("quarkos_capes.height", 1080)
            );
            try {
                Files.createDirectories(DIR);
                Files.createDirectories(SHARE);
            } catch (java.io.IOException e) {
                fail(e.getMessage());
            }
        }
        ticks++;
        try {
            if ("peer".equals(SCENE)) {
                peer(client);
            } else {
                switch (phase) {
                    case 0 -> title(client);
                    case 1 -> open(client);
                    case 2 -> scenes(client);
                    default -> { }
                }
            }
        } catch (Throwable failure) {
            fail(failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private static void scenes(Minecraft client) {
        if (client.player == null || client.level == null) {
            if (ticks > 20 * 180) {
                fail("world did not load");
            }
            return;
        }
        McBridge.clearToasts(client);
        if ("shader".equals(SCENE)) {
            wearWorld(client, "shader");
            return;
        }
        switch (step) {
            case 0 -> wardrobe(client);
            case 1 -> browse(client);
            case 2 -> wearWorld(client, "wearing");
            case 3 -> elytra(client);
            default -> finish("gallery");
        }
    }

    private static void wardrobe(Minecraft client) {
        stepTicks++;
        if (stepTicks == 1) {
            client.options.hideGui = false;
            McBridge.showCape(client.options);
            OwnerCape.set(OwnerCape.sampleCloak());
            CapeBook.previewBack = true;
            CapeBook.draft = null;
            McBridge.setScreen(client, CapeScreens.openTab(null, 0));
            CapeBook.preview = true;
        }
        if (!(client.screen != null && CapeScreens.isSettings(client.screen))) {
            if (stepTicks > 20 * 20) {
                fail("wardrobe did not open");
            }
            return;
        }
        CapeBook.preview = true;
        CapeBook.previewBack = true;
        if (stepTicks == 40) {
            shoot(client, "wardrobe");
        }
        if (stepTicks > 40 && shotDone("wardrobe")) {
            next();
        }
    }

    private static void browse(Minecraft client) {
        stepTicks++;
        ClientSettings.get().localOnly = false;
        ClientSettings.get().showOthers = true;
        client.options.hideGui = false;
        if (stepTicks == 1) {
            readyTicks = 0;
            McBridge.setScreen(client, new BrowseScreen(null));
        }
        if (!(client.screen instanceof BrowseScreen browse)) {
            if (stepTicks > 20 * 15) {
                fail("browse did not open");
            }
            return;
        }
        if (stepTicks == 20) {
            browse.search("Verdant", "ours");
        }
        String status = browse.status() == null ? "" : browse.status();
        String title = browse.selected() == null ? "" : browse.selected().title();
        boolean ready = stepTicks > 20 && !browse.isLoading() && !browse.items().isEmpty()
                && browse.previewReady() && title.toLowerCase().contains("verdant") && status.isBlank();
        if (!ready) {
            readyTicks = 0;
            if (!browse.items().isEmpty() && stepTicks % 20 == 0) {
                browse.selectFirst();
            }
            if (stepTicks > 20 * 45) {
                fail("browse did not load (" + status + ", " + title + ", items=" + browse.items().size() + ")");
            }
            return;
        }
        if (showsAddress(status) || showsAddress(title)) {
            fail("browse shows an address");
        }
        // Tick runs before the frame is drawn, so wait until the preview has been on screen.
        readyTicks++;
        if (shotAt < 0 && readyTicks >= 30) {
            shoot(client, "browse");
        }
        if (shotAt >= 0 && shotDone("browse")) {
            McBridge.setScreen(client, null);
            CapeBook.draft = null;
            CapeBook.preview = false;
            next();
        }
    }

    private static void wearWorld(Minecraft client, String name) {
        stepTicks++;
        if (stepTicks == 1) {
            McBridge.prepareWorld(client);
            McBridge.showCape(client.options);
            OwnerCape.set(OwnerCape.sampleCloak());
            CapeBook.preview = false;
            CapeBook.draft = null;
            McBridge.setScreen(client, null);
            client.options.hideGui = true;
            McBridge.thirdPerson(client.options);
            client.options.fov().set(30);
            CapeBook.resetDraws();
            ground = client.player.getY();
            client.player.connection.sendCommand("tp @s 0.5 " + trim(ground) + " 0.5 0 8");
            client.player.connection.sendCommand("weather clear");
        }
        client.options.hideGui = true;
        McBridge.thirdPerson(client.options);
        McBridge.clearChat(client);
        McBridge.clearToasts(client);
        if (client.player.getY() < ground - 2 && stepTicks % 20 == 0) {
            client.player.connection.sendCommand("tp @s 0.5 " + trim(ground) + " 0.5 0 8");
        }
        if (client.player.getY() < ground - 2) {
            if (stepTicks > 20 * 20) {
                fail(name + " player stayed at y=" + client.player.getY());
            }
            return;
        }
        if (CapeBook.draws(client.player.getUUID()) < 1) {
            if (stepTicks > 20 * 20) {
                fail(name + " cape was not drawn");
            }
            return;
        }
        if (shotAt < 0 && stepTicks >= 30) {
            shoot(client, name);
        }
        if (shotAt >= 0 && shotDone(name)) {
            if ("shader".equals(SCENE)) {
                finish("shader");
            } else {
                next();
            }
        }
    }

    private static void elytra(Minecraft client) {
        stepTicks++;
        if (stepTicks == 1) {
            // Bands is a single 2:1 frame, so the elytra skin uses that texture.
            OwnerCape.set(OwnerCape.sampleBands());
            readyTicks = 0;
            client.options.hideGui = true;
            McBridge.thirdPerson(client.options);
            McBridge.setScreen(client, null);
            client.player.getAbilities().flying = false;
            client.player.connection.sendCommand("gamerule spawn_mobs false");
            client.player.connection.sendCommand("kill @e[type=!player]");
            client.player.connection.sendCommand("item replace entity @s armor.chest with minecraft:elytra");
        }
        client.options.hideGui = true;
        client.options.fov().set(28);
        McBridge.thirdPerson(client.options);
        McBridge.clearChat(client);
        McBridge.clearToasts(client);
        if (stepTicks % 8 == 0) {
            client.player.connection.sendCommand("kill @e[type=!player]");
            client.player.connection.sendCommand("tp @s 0.5 " + trim(ground + 7) + " 0.5 0 8");
        }
        boolean elytra = client.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
        if (elytra) {
            requestGlide(client);
        }
        if (!elytra || !client.player.isFallFlying()) {
            readyTicks = 0;
            if (stepTicks > 20 * 25) {
                fail("elytra did not open");
            }
            return;
        }
        readyTicks++;
        if (shotAt < 0 && readyTicks >= 40) {
            shoot(client, "elytra");
        }
        if (shotAt >= 0 && shotDone("elytra")) {
            next();
        }
    }

    private static void requestGlide(Minecraft client) {
        var player = client.player;
        player.startFallFlying();
        player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(
                player, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
    }

    private static void peer(Minecraft client) {
        switch (phase) {
            case 0 -> title(client);
            case 1 -> join(client);
            case 2 -> peerPlay(client);
            default -> { }
        }
    }

    private static void join(Minecraft client) {
        if (!(McBridge.currentScreen(client) instanceof TitleScreen)) {
            return;
        }
        String address = System.getProperty("quarkos_capes.server", "127.0.0.1:25599");
        //? if <1.21 {
        /*ServerData data = new ServerData("capes", address, false);
        ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address), data, false);
        *///?} else {
        ServerData data = new ServerData("capes", address, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address), data, false, null);
        //?}
        phase = 2;
        ticks = 0;
    }

    private static void peerPlay(Minecraft client) {
        if (client.player == null || client.level == null) {
            Screen screen = McBridge.currentScreen(client);
            if (screen != null && !(screen instanceof TitleScreen)) {
                press(screen, "yes", "proceed", "continue", "ok");
            }
            if (ticks > 20 * 120) {
                fail("did not join the dedicated server");
            }
            return;
        }
        ClientSettings.get().localOnly = false;
        ClientSettings.get().showOthers = true;
        McBridge.showCape(client.options);
        if (ticks % 20 == 0) {
            client.options.broadcastOptions();
        }
        McBridge.clearToasts(client);
        if (ticks == 5) {
            client.player.getAbilities().flying = false;
            client.player.connection.sendCommand("gamemode creative @s");
            McBridge.prepareWorld(client);
        }
        if (ticks < 25) {
            return;
        }
        if (Double.isNaN(ground)) {
            if (!client.player.onGround() && ticks < 20 * 20) {
                return;
            }
            ground = client.player.getY();
            mark("ground " + ground);
            client.player.connection.sendCommand("gamerule spawn_mobs false");
            client.player.connection.sendCommand("kill @e[type=!player]");
            if ("a".equals(ROLE)) {
                try {
                    Files.writeString(SHARE.resolve("a-ground"), trim(ground) + "\n");
                } catch (java.io.IOException e) {
                    fail(e.getMessage());
                }
            }
        }
        if ("b".equals(ROLE) && Files.isRegularFile(SHARE.resolve("a-ground"))) {
            try {
                ground = Double.parseDouble(Files.readString(SHARE.resolve("a-ground")).trim());
            } catch (java.io.IOException | NumberFormatException e) {
                fail("ground");
            }
        }
        if ("b".equals(ROLE)) {
            peerB(client);
        } else {
            peerA(client);
        }
    }

    private static void peerA(Minecraft client) {
        if (step == 0) {
            // North of B, facing north, so B's camera (south, also facing north) sees this back.
            hold(client, 0, ground, 0, 180, 0, false);
            marker("a-ready");
            if (markerExists("b-shot") && markerExists("b-turned")) {
                step = 1;
                stepTicks = 0;
                CapeBook.resetDraws();
            } else if (ticks > 20 * 180) {
                fail("client B did not photograph A");
            }
            return;
        }
        // Stand south of B and keep facing north. Do not also command the north pose:
        // that teleport was winning the race and left this camera looking at B's face.
        stepTicks++;
        hold(client, 0, ground, 7.2, 180, 8, stepTicks % 10 == 0);
        if (stepTicks < 40) {
            return;
        }
        watch(client, peerId(), "multiplayer-a", "a-shot", "B");
    }

    private static void peerB(Minecraft client) {
        if (!markerExists("a-ready")) {
            if (ticks > 20 * 120) {
                fail("client A was not ready");
            }
            return;
        }
        if (step == 0) {
            hold(client, 0, ground, 3.6, 180, 10, false);
            watch(client, peerId(), "multiplayer-b", "b-shot", "A");
            return;
        }
        hold(client, 0, ground, 3.6, 180, 0, false);
        marker("b-turned");
        if (markerExists("a-shot")) {
            finish("multiplayer");
        } else if (ticks > 20 * 240) {
            fail("client A did not photograph B");
        }
    }

    private static void watch(Minecraft client, UUID other, String shot, String marker, String who) {
        stepTicks++;
        client.options.hideGui = true;
        client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        client.options.fov().set(55);
        McBridge.clearChat(client);
        if (stepTicks % 10 == 0) {
            SiteApi.poll(List.of(other));
        }
        boolean present = false;
        for (var player : client.level.players()) {
            if (player.getUUID().equals(other)) {
                present = true;
            }
        }
        PlayerFacts.Fact fact = PlayerFacts.byUuid(other);
        boolean ready = present && fact != null && CapeBook.remote(other) != null
                && CapeTextures.get(CapeBook.textureKey(other)) != null
                && CapeBook.draws(other) > 0;
        if (!ready) {
            poseTicks = 0;
            if (stepTicks > 20 * 60) {
                fail(who + " cape was not drawn (present=" + present + " remote=" + (CapeBook.remote(other) != null)
                        + " draws=" + CapeBook.draws(other) + ")");
            }
            return;
        }
        for (var player : client.level.players()) {
            if (!player.getUUID().equals(other)) {
                continue;
            }
            if (poseTicks == 0) {
                mark(String.format(java.util.Locale.US,
                        "raw cam %.2f,%.2f yaw %.1f sub %.2f,%.2f yaw %.1f body %.1f",
                        client.player.getX(), client.player.getZ(), client.player.getYRot(),
                        player.getX(), player.getZ(), player.getYRot(), player.yBodyRot));
            }
            // Same yaw as the camera. The camera stands to the south and looks north.
            lockFacing(player, wantYaw, 0f);
        }
        poseTicks++;
        if (shotAt < 0 && poseTicks >= 8 && stepTicks >= 25) {
            shoot(client, shot);
        }
        if (shotAt >= 0 && shotDone(shot)) {
            marker(marker);
            mark(shot + " draws=" + CapeBook.draws(other));
            step = 1;
            stepTicks = 0;
            shotAt = -1;
            if ("a".equals(ROLE)) {
                finish("multiplayer");
            }
        }
    }

    private static void hold(Minecraft client, double x, double y, double z, int yaw, int pitch, boolean now) {
        var player = client.player;
        wantYaw = yaw;
        wantPitch = pitch;
        lockFacing(player, yaw, pitch);
        if (now || ticks % 20 == 0) {
            player.connection.sendCommand("tp @s " + trim(x) + " " + trim(y) + " " + trim(z) + " " + yaw + " " + pitch);
        }
    }

    private static void lockFacing(net.minecraft.world.entity.player.Player player, float yaw, float pitch) {
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYBodyRot(yaw);
        player.yBodyRot = yaw;
        player.yBodyRotO = yaw;
        player.setYHeadRot(yaw);
        player.yHeadRot = yaw;
        player.yHeadRotO = yaw;
    }

    private static UUID peerId() {
        String name = "a".equals(ROLE) ? "CapeStoreB" : "CapeStoreA";
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
        phase = "peer".equals(SCENE) ? 1 : 1;
        if (!"peer".equals(SCENE)) {
            phase = 1;
        }
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
        ui.setName("store");
        presets.stream().filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(ui::setWorldType);
        create.onCreate();
        phase = 2;
        ticks = 0;
    }

    private static void shoot(Minecraft client, String name) {
        McBridge.clearChat(client);
        McBridge.clearToasts(client);
        shotAt = stepTicks;
        McBridge.shot(client, DIR.resolve(name + ".png"));
        mark("shot " + name);
    }

    private static boolean shotDone(String name) {
        if (stepTicks < shotAt + 15) {
            return false;
        }
        try {
            if (Files.size(DIR.resolve(name + ".png")) > 1000) {
                return true;
            }
        } catch (java.io.IOException ignored) {
            // 1.21.5+ writes the screenshot on the render thread.
        }
        if (stepTicks > shotAt + 20 * 30) {
            fail("screenshot was not written: " + name);
        }
        return false;
    }

    private static void next() {
        step++;
        stepTicks = 0;
        shotAt = -1;
    }

    private static void finish(String scene) {
        mark("ok " + scene);
        com.mojang.logging.LogUtils.getLogger().info("quarkos_capes store " + scene + " ok");
        System.out.println("quarkos_capes store " + scene + " ok");
        System.exit(0);
    }

    private static boolean showsAddress(String value) {
        String text = value.toLowerCase();
        return text.contains("localhost") || text.contains("127.0.0.1") || text.contains("http://") || text.contains("https://");
    }

    private static String trim(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static void marker(String name) {
        try {
            Files.writeString(SHARE.resolve(name), "ok\n", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (java.io.IOException e) {
            fail(e.getMessage());
        }
    }

    private static boolean markerExists(String name) {
        return Files.isRegularFile(SHARE.resolve(name));
    }

    private static void mark(String line) {
        try {
            Files.writeString(SHARE.resolve("capture.trace"), line + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (java.io.IOException ignored) {
        }
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
        com.mojang.logging.LogUtils.getLogger().error("quarkos_capes store fail: " + message);
        System.err.println("quarkos_capes store fail: " + message);
        System.exit(1);
    }
}
