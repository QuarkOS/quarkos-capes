package dev.quarkos.capes.client.harness;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.LocalFiles;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.net.CapeHttp;
//? if dev_catalog {
/*import dev.quarkos.capes.client.net.LabyCatalog;
*///?}
import dev.quarkos.capes.client.net.LabySource;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.render.CapeLayerWatch;
import dev.quarkos.capes.client.render.CapeTextures;
//? if dev_catalog {
/*import dev.quarkos.capes.client.render.LabyWear;
*///?}
import dev.quarkos.capes.client.screen.CapeActions;
//? if fabric {
import dev.quarkos.capes.client.screen.CapeGalleryScreen;
import dev.quarkos.capes.client.screen.CapeScreen;
import dev.quarkos.capes.client.screen.CapeScreens;
import dev.quarkos.capes.client.screen.GalleryScreen;
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Drives a real client. Forge captures are F5 back views, and only after the cape texture is set.
public final class ClientHarness {
    private static final int SCALE = Integer.getInteger("quarkos_capes.timeoutScale", 1);
    private static final Deque<String> SHOTS = new ArrayDeque<>();
    private static final String[] BACKS = {"flat", "frames", "wave", "model"};
    private static boolean armed;
    private static State state = State.WAIT_TITLE;
    private static int ticks;
    private static int sentAtGallery;
    private static int shotCool;
    private static boolean placed;
    private static int styleIndex;
    private static int phase;
    private static int labyPhase;
    private static int labyIndex;
//? if dev_catalog {
    /*private static List<LabyFrame> labyFrames;
    private static String labyError;
    private static final List<String> LABY_EXPECTED = new ArrayList<>();
*///?}


    private static boolean playerSeen;

    private ClientHarness() {
    }

    public static void start(Minecraft client) {
        if (!Boolean.getBoolean("quarkos_capes.harness")
                && !Boolean.getBoolean("quarkos_capes.labyShots")
                && !Boolean.getBoolean("quarkos_capes.labyCompare") && !UiHarness.selected()) {
            return;
        }
        boolean ui = UiHarness.selected();
        if (ui && UiHarness.finished()) return;
        if ("boot".equals(System.getProperty("quarkos_capes.only"))) {
            BootHarness.tick(client);
            return;
        }
        //? if fabric {
        String role = System.getProperty("quarkos_capes.role", "");
        if (!ui && ("a".equals(role) || "b".equals(role))) {
            ShareHarness.start(client, role);
            return;
        }
        //?}
        if (!armed) {
            armed = true;
            if (ui) {
                System.setProperty("quarkos_capes.harness", "true");
                UiHarness.prepare(client);
            }
            client.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
            client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            client.options.pauseOnLostFocus = false;
            client.options.onboardAccessibility = false;
            //? if >=1.21 {
            client.options.menuBackgroundBlurriness().set(0);
            //?}
            client.options.guiScale().set(Integer.getInteger("quarkos_capes.guiScale", 2));
            client.options.renderDistance().set(2);
            client.options.simulationDistance().set(5);
            client.options.framerateLimit().set(20);
            client.getWindow().setWindowed(Integer.getInteger("quarkos_capes.width", ui ? 1920 : 1280),
                    Integer.getInteger("quarkos_capes.height", ui ? 1080 : 720));
        }
        ticks++;
        McBridge.clearChat(client);
        McBridge.clearToasts(client);
        flushShot(client);
        try {
            switch (state) {
                case WAIT_TITLE -> waitTitle(client);
                case OPEN_WORLD -> openWorld(client);
                case WAIT_PLAYER -> waitPlayer(client);
                //? if fabric {
                case CAPE -> cape(client);
                case WORLD -> world(client);
                case GALLERY -> gallery(client);
                //?}
                case BACK -> backs(client);
//? if dev_catalog {
                /*case LABY -> labyShots(client);
                case COMPARE -> {
                    if (LabyCompare.tick(client)) {
                        state = State.DONE;
                    }
                }

*///?}
                case CLOAK -> cloakShots(client);
                case AUDIT -> AuditHarness.tick(client);
                case FLOWS -> FlowHarness.tick(client);
                case UI -> UiHarness.tick(client);
                case DONE -> finish();
            }
        } catch (RuntimeException e) {
            fail(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static void waitTitle(Minecraft client) {
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
            if (name.contains("notice") || name.contains("safety")) {
                pressMatching(screen, "continue", "proceed");
                return;
            }
        }
        if (!(McBridge.currentScreen(client) instanceof TitleScreen) || client.getOverlay() != null) {
            if (ticks > timeout(20 * 120)) {
                fail("title screen did not open");
            }
            return;
        }
        state = State.OPEN_WORLD;
        ticks = 0;
    }

    private static void openWorld(Minecraft client) {
        if (!(McBridge.currentScreen(client) instanceof CreateWorldScreen)) {
            //? if <1.21.9 {
            /*CreateWorldScreen.openFresh(client, McBridge.currentScreen(client));
            *///?} else {
            CreateWorldScreen.openFresh(client, () -> McBridge.setScreen(client, new TitleScreen()));
            //?}
            return;
        }
        CreateWorldScreen create = (CreateWorldScreen) McBridge.currentScreen(client);
        var presets = create.getUiState().getNormalPresetList();
        if (presets.isEmpty()) {
            if (ticks > timeout(20 * 60)) {
                fail("world presets never loaded");
            }
            return;
        }
        WorldCreationUiState ui = create.getUiState();
        ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
        McBridge.allowCommands(ui);
        ui.setName(UiHarness.selected() ? "capes-ui" : "capes");
        presets.stream().filter(entry -> entry.preset().is(WorldPresets.FLAT)).findFirst().ifPresent(ui::setWorldType);
        create.onCreate();
        state = State.WAIT_PLAYER;
        ticks = 0;
    }

    private static void waitPlayer(Minecraft client) {
        if (client.player == null || client.level == null) {
            if (ticks > timeout(20 * 180)) {
                fail("world did not load");
            }
            return;
        }
        // Count setup ticks from the first tick with a player: slow world loads must not skip the setup ticks below.
        if (!playerSeen) {
            playerSeen = true;
            ticks = 1;
        }
        if (ticks == 40) {
            showCape(client);
            McBridge.prepareWorld(client);
        }
        if (ticks == 55) {
            McBridge.clearChat(client);
            showCape(client);
            if (UiHarness.selected()) { state = State.UI; ticks = 0; return; }
//? if dev_catalog {
            /*if (Boolean.getBoolean("quarkos_capes.labyCompare")) {
                if (!LabyCatalog.enabled()) {
                    fail("CAPES_DEV_CATALOG is not true");
                }
                LabyCompare.arm(client);
                state = State.COMPARE;
                ticks = 0;
                return;
            }
            if (Boolean.getBoolean("quarkos_capes.labyShots")) {
                if (!LabyCatalog.enabled()) {
                    fail("CAPES_DEV_CATALOG is not true");
                }
                armLaby(client);
                state = State.LABY;
                ticks = 0;
                return;
            }

*///?}
            if (Boolean.getBoolean("quarkos_capes.cloakOnly")) {
                phase = 0;
                state = State.CLOAK;
                ticks = 0;
                return;
            }
            if (FlowHarness.selected()) { state = State.FLOWS; ticks = 0; return; }
            if (AuditHarness.selected()) {
                state = State.AUDIT;
                ticks = 0;
                return;
            }
            //? if forge || neoforge {
            /*CapeActions.bands();
            CapeActions.style("flat", false);
            CapeBook.preview = false;
            CapeBook.draft = null;
            McBridge.setScreen(client, null);
            McBridge.thirdPerson(client.options);
            styleIndex = 0;
            phase = 0;
            state = State.BACK;
            *///?} else {
            McBridge.setScreen(client, CapeScreens.open(null));
            state = State.CAPE;
            //?}
            ticks = 0;
        }
    }

    //? if fabric {
    private static void cape(Minecraft client) {
        if (!(McBridge.currentScreen(client) instanceof CapeScreen)) {
            if (ticks > 10) {
                fail("cape screen did not open");
            }
            return;
        }
        if (ticks == 5) {
            apply(client, "text.quarkos_capes.sample.bands");
            apply(client, "text.quarkos_capes.style.flat");
            CapeBook.previewBack = false;
            McBridge.setScreen(client, CapeScreens.open(null));
        }
        if (ticks == 12) {
            if (CapeBook.draws <= 0) {
                fail("preview did not render the flat sample");
            }
            queueShot(client, "cape-front");
            queueShot(client, "flat-preview");
        }
        if (ticks == 16) {
            CapeBook.previewBack = true;
            McBridge.setScreen(client, CapeScreens.open(null));
            queueShot(client, "cape-back");
        }
        if (ticks == 20) {
            apply(client, "text.quarkos_capes.sample.slide");
            apply(client, "text.quarkos_capes.style.frames");
        }
        if (ticks >= 24 && ticks < 30) {
            queueShot(client, "frames-" + (ticks - 24));
        }
        if (ticks == 40) {
            apply(client, "text.quarkos_capes.style.wave");
        }
        if (ticks >= 44 && ticks < 50) {
            queueShot(client, "wave-preview-" + (ticks - 44));
        }
        if (ticks == 60) {
            apply(client, "text.quarkos_capes.sample.cloak");
            apply(client, "text.quarkos_capes.style.model");
        }
        if (ticks == 66) {
            queueShot(client, "model-preview");
        }
        if (ticks >= 72 && SHOTS.isEmpty() && shotCool == 0) {
            McBridge.setScreen(client, null);
            McBridge.thirdPerson(client.options);
            state = State.WORLD;
            ticks = 0;
            placed = false;
        }
    }

    private static void world(Minecraft client) {
        if (client.player == null) {
            fail("player missing for world shots");
            return;
        }
        client.options.fov().set(30);
        showCape(client);
        if (!placed && ticks == 2) {
            client.player.connection.sendCommand("tp @s 0 200 0 180 12");
            placed = true;
            McBridge.clearChat(client);
        }
        if (ticks == 12) {
            apply(client, "text.quarkos_capes.sample.cloak");
            apply(client, "text.quarkos_capes.style.model");
            McBridge.setScreen(client, null);
            McBridge.thirdPerson(client.options);
        }
        if (ticks == 20) {
            queueShot(client, "model");
        }
        if (ticks == 28) {
            apply(client, "text.quarkos_capes.sample.bands");
            apply(client, "text.quarkos_capes.style.flat");
            McBridge.setScreen(client, null);
            McBridge.thirdPerson(client.options);
        }
        if (ticks == 36) {
            queueShot(client, "flat");
            queueShot(client, "cape-on-player");
        }
        if (ticks == 48) {
            apply(client, "text.quarkos_capes.sample.slide");
            apply(client, "text.quarkos_capes.style.frames");
            McBridge.setScreen(client, null);
            McBridge.thirdPerson(client.options);
        }
        if (ticks >= 56 && ticks < 62) {
            queueShot(client, "frames-world-" + (ticks - 56));
        }
        if (ticks == 74) {
            apply(client, "text.quarkos_capes.sample.bands");
            apply(client, "text.quarkos_capes.style.wave");
            McBridge.setScreen(client, null);
            McBridge.thirdPerson(client.options);
        }
        if (ticks >= 82 && ticks < 88) {
            queueShot(client, "wave-" + (ticks - 82));
        }
        if (ticks >= 100 && SHOTS.isEmpty() && shotCool == 0) {
            sentAtGallery = CapeHttp.sent;
            McBridge.clearChat(client);
            McBridge.setScreen(client, GalleryScreen.create(null));
            state = State.GALLERY;
            ticks = 0;
        }
    }

    private static void gallery(Minecraft client) {
        if (!(McBridge.currentScreen(client) instanceof CapeGalleryScreen)) {
            if (ticks > 10) {
                fail("gallery screen did not open");
            }
            return;
        }
        if (ticks == 8) {
            if (CapeHttp.sent != sentAtGallery) {
                fail("gallery sent a request while offline");
            }
            queueShot(client, "gallery");
            queueShot(client, "gallery-quiet");
        }
        if (ticks > 20 && SHOTS.isEmpty() && shotCool == 0) {
            state = State.DONE;
        }
    }

    private static void apply(Minecraft client, String key) {
        CapeActions.applyKey(key);
        McBridge.setScreen(client, CapeScreens.open(null));
    }
    //?}

    private static void backs(Minecraft client) {
        if (client.player == null) {
            fail("player missing for back views");
            return;
        }
        client.options.hideGui = true;
        client.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        McBridge.thirdPerson(client.options);
        client.options.fov().set(46);
        if (styleIndex >= BACKS.length) {
            stopWalk(client);
            if (SHOTS.isEmpty() && shotCool == 0) {
                state = State.DONE;
            }
            return;
        }
        String style = BACKS[styleIndex];
        boolean wave = "wave".equals(style);
        client.player.setYRot(0f);
        client.player.setYBodyRot(0f);
        client.player.setYHeadRot(0f);
        client.player.setXRot(0f);
        client.player.getAbilities().flying = false;
        settleCloak(client);
        if (!wave) {
            stopWalk(client);
        }
        if (phase == 0) {
            showCape(client);
            equip(style);
            CapeBook.preview = false;
            CapeBook.draft = null;
            McBridge.setScreen(client, null);
            CapeLayerWatch.reset();
            if (styleIndex == 0) {
                client.player.connection.sendCommand("fill -3 140 -8 3 140 40 minecraft:barrier");
                client.player.connection.sendCommand("fill -4 141 -10 4 147 44 minecraft:air");
            }
            client.player.connection.sendCommand("tp @s 0.5 141 0.5 0 0");
            phase = 1;
            ticks = 0;
            return;
        }
        if (phase == 1) {
            if (ticks < 24) {
                return;
            }
            boolean third = client.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK;
            float y = (float) client.player.getY();
            boolean standing = client.player.onGround() && y > 140f && y < 143f;
            if (CapeLayerWatch.localTexture != null && CapeLayerWatch.localCalls > 0 && third && standing) {
                McBridge.clearChat(client);
                phase = wave ? 2 : 4;
                ticks = 0;
                return;
            }
            if (ticks > timeout(20 * 15)) {
                fail("no cape view for " + style
                        + " texture=" + CapeLayerWatch.localTexture
                        + " calls=" + CapeLayerWatch.localCalls
                        + " camera=" + client.options.getCameraType()
                        + " y=" + client.player.getY()
                        + " ground=" + client.player.onGround());
            }
            return;
        }
        if (wave && phase == 2) {
            boolean coast = ticks >= 12 && ticks < 22;
            client.options.keyUp.setDown(!coast && ticks < 22);
            client.options.keyLeft.setDown(ticks >= 22);
            if (ticks == 8) {
                queueShot(client, "wave-walk-1");
            }
            if (ticks == 16) {
                queueShot(client, "wave-back");
                queueShot(client, "wave-walk-2");
            }
            if (ticks == 30) {
                queueShot(client, "wave-walk-3");
                phase = 3;
            }
            return;
        }
        if (phase == 4 && ticks >= 2) {
            System.out.println("quarkos_capes back " + style
                    + " texture=" + CapeLayerWatch.localTexture
                    + " calls=" + CapeLayerWatch.localCalls
                    + " camera=" + client.options.getCameraType()
                    + " y=" + client.player.getY()
                    + " z=" + client.player.getZ()
                    + " hud=" + client.options.hideGui);
            queueShot(client, style + "-back");
            phase = 3;
            return;
        }
        if (phase == 3 && SHOTS.isEmpty() && shotCool == 0) {
            System.out.println("quarkos_capes back " + style
                    + " done z=" + client.player.getZ()
                    + " y=" + client.player.getY());
            stopWalk(client);
            styleIndex++;
            phase = 0;
            ticks = 0;
        }
    }

    private static void cloakShots(Minecraft client) {
        if (client.player == null) {
            fail("player missing for cloak shots");
            return;
        }
        client.options.hideGui = true;
        client.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        client.options.fov().set(30);
        McBridge.thirdPerson(client.options);
        showCape(client);
        client.player.getAbilities().flying = false;
        stopWalk(client);
        if (phase == 0) {
            CapeActions.cloak();
            CapeActions.style("model", false);
            CapeBook.preview = false;
            CapeBook.draft = null;
            McBridge.setScreen(client, null);
            CapeLayerWatch.reset();
            try {
                // Back plates are kept. This pass only replaces the three-quarter view.
                java.nio.file.Files.deleteIfExists(captures().resolve("cloak-three-quarter.png"));
            } catch (java.io.IOException ignored) {
            }
            client.player.connection.sendCommand("fill -6 140 -6 6 140 6 minecraft:grass_block");
            client.player.connection.sendCommand("fill -6 141 -6 6 146 6 minecraft:air");
            client.player.connection.sendCommand("tp @s 0.5 141 0.5 0 0");
            phase = 1;
            ticks = 0;
            return;
        }
        if (phase == 1) {
            if (ticks < 20) {
                return;
            }
            boolean third = client.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK;
            float y = (float) client.player.getY();
            boolean standing = client.player.onGround() && y > 140f && y < 143f;
            if (CapeLayerWatch.localTexture != null && CapeLayerWatch.localCalls > 0 && third && standing) {
                McBridge.clearChat(client);
                client.player.connection.sendCommand("tp @s 0.5 141 0.5 0 0");
                phase = 2;
                ticks = 0;
                return;
            }
            if (ticks > timeout(20 * 20)) {
                fail("no cloak view texture=" + CapeLayerWatch.localTexture
                        + " calls=" + CapeLayerWatch.localCalls
                        + " camera=" + client.options.getCameraType()
                        + " y=" + client.player.getY()
                        + " ground=" + client.player.onGround());
            }
            return;
        }
        if (phase == 2) {
            face(client, 0f);
            // An existing back plate stays. Turning the look yaw would turn the camera with it.
            if (written("cloak-back")) {
                phase = 3;
                ticks = 0;
                return;
            }
            if (ticks == 8) {
                McBridge.clearChat(client);
                queueShot(client, "cloak-back");
            }
            if (ticks > 8 && written("cloak-back")) {
                phase = 3;
                ticks = 0;
                return;
            }
            if (ticks > timeout(20 * 15)) {
                fail("cloak-back missing at " + captures().resolve("cloak-back.png"));
            }
            return;
        }
        if (phase == 3) {
            // Look stays at 0 so the third-person camera holds the back. The body turns 45°
            // so the cape's side edge comes into frame, matching the preview's right figure.
            turn(client, 0f, -45f);
            if (ticks == 12) {
                McBridge.clearChat(client);
                queueShot(client, "cloak-three-quarter");
            }
            if (ticks > 12 && written("cloak-three-quarter")) {
                phase = 4;
                ticks = 0;
                return;
            }
            if (ticks > timeout(20 * 15)) {
                fail("cloak-three-quarter missing at " + captures().resolve("cloak-three-quarter.png"));
            }
            return;
        }
        if (phase == 4 && ticks > 5) {
            state = State.DONE;
        }
    }

    private static boolean written(String name) {
        try {
            java.nio.file.Path file = captures().resolve(name + ".png");
            return java.nio.file.Files.isRegularFile(file) && java.nio.file.Files.size(file) > 1000L;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static void face(Minecraft client, float yaw) {
        turn(client, yaw, yaw);
    }

    /** Look yaw drives the third-person camera. Body yaw is the player, and the cape on their back. */
    private static void turn(Minecraft client, float lookYaw, float bodyYaw) {
        client.player.setYRot(lookYaw);
        client.player.yRotO = lookYaw;
        client.player.setXRot(0f);
        client.player.xRotO = 0f;
        client.player.setYBodyRot(bodyYaw);
        client.player.yBodyRotO = bodyYaw;
        client.player.setYHeadRot(bodyYaw);
        client.player.yHeadRotO = bodyYaw;
    }

    private static void stopWalk(Minecraft client) {
        client.options.keyUp.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false);
    }

    private static void equip(String style) {
        switch (style) {
            case "frames" -> {
                CapeActions.slide();
                CapeActions.style("frames", true);
            }
            case "wave" -> {
                CapeActions.bands();
                CapeActions.style("wave", true);
            }
            case "model" -> {
                CapeActions.cloak();
                CapeActions.style("model", false);
            }
            default -> {
                CapeActions.bands();
                CapeActions.style("flat", false);
            }
        }
    }

    private static void settleCloak(Minecraft client) {
        //? if <1.21.11 {
        /*client.player.xCloak = client.player.getX();
        client.player.yCloak = client.player.getY();
        client.player.zCloak = client.player.getZ();
        client.player.xCloakO = client.player.xCloak;
        client.player.yCloakO = client.player.yCloak;
        client.player.zCloakO = client.player.zCloak;
        *///?}
    }

    private static void showCape(Minecraft client) {
        McBridge.showCape(client.options);
        client.options.broadcastOptions();
    }

    private static void pressMatching(Screen screen, String... words) {
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

    private static void queueShot(Minecraft client, String name) {
        McBridge.clearChat(client);
        SHOTS.add(name);
        if (shotCool == 0) {
            shotCool = 2;
        }
    }

    private static void flushShot(Minecraft client) {
        if (SHOTS.isEmpty()) {
            return;
        }
        if (shotCool > 0) {
            shotCool--;
            return;
        }
        McBridge.shot(client, captures().resolve(SHOTS.poll() + ".png"));
        shotCool = SHOTS.isEmpty() ? 0 : 2;
    }

    private static Path captures() {
        String configured = System.getProperty("quarkos_capes.captures");
        if (configured == null || configured.isBlank()) {
            return Path.of("build/captures");
        }
        return Path.of(configured);
    }

    private static void finish() {
        System.out.println("quarkos_capes harness ok");
        System.exit(0);
    }

    private static int timeout(int value) {
        return Math.max(1, value) * Math.max(1, SCALE);
    }

    static void fail(String message) {
        if (UiHarness.selected()) {
            UiHarness.abort(Minecraft.getInstance(), message);
            return;
        }
        System.err.println("quarkos_capes harness fail: " + message);
        System.exit(1);
    }

//? if dev_catalog {
    /*private static void armLaby(Minecraft client) {
        hideChrome(client);
        McBridge.thirdPerson(client.options);
        client.options.fov().set(30);
        client.options.gamma().set(1.0);
        McBridge.setScreen(client, null);
        CapeBook.preview = false;
        CapeBook.draft = null;
        OwnerCape bare = new OwnerCape();
        bare.png = new byte[0];
        bare.style = "flat";
        OwnerCape.set(bare);
        CapeTextures.clear("local");
        LabyWear.clear();
        ClientSettings settings = ClientSettings.get();
        settings.localOnly = false;
        settings.enableLabyMod = true;
        settings.showOthers = true;
        labyPhase = 0;
        labyIndex = 0;
        labyFrames = null;
        labyError = null;
        LABY_EXPECTED.clear();
        Thread thread = new Thread(ClientHarness::loadLabyFrames, "quarkos-laby-shots");
        thread.setDaemon(true);
        thread.start();
    }

    private static void loadLabyFrames() {
        try {
            Path cache = LocalFiles.folder().resolve("labymod");
            List<LabyFrame> frames = new ArrayList<>();
            frames.addAll(pick("cape", 3, cache));
            frames.addAll(pick("cloak", 3, cache));
            if (frames.stream().noneMatch(frame -> "cape".equals(frame.kind))) {
                throw new IllegalStateException("catalog returned no capes");
            }
            if (frames.stream().noneMatch(frame -> "cloak".equals(frame.kind))) {
                throw new IllegalStateException("catalog returned no cloaks");
            }
            labyFrames = frames;
        } catch (Exception e) {
            labyError = e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    private static List<LabyFrame> pick(String kind, int count, Path cache) throws Exception {
        List<LabyCatalog.Item> page = LabyCatalog.page(LabyCatalog.allow(false), kind, 12, 0, CapeHttp::labyExchange);
        List<LabyFrame> frames = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (LabyCatalog.Item item : page) {
            if (frames.size() >= count) {
                break;
            }
            byte[] png = LabySource.texture(item.hash(), cache, CapeHttp::labyExchange);
            if (png == null) {
                continue;
            }
            String name = unique("labymod-" + kind + "-" + item.slug(), names);
            frames.add(new LabyFrame(kind, name, png));
        }
        return frames;
    }

    private static String unique(String name, Set<String> names) {
        String unique = name;
        int n = 2;
        while (!names.add(unique)) {
            unique = name + "-" + n++;
        }
        return unique;
    }

    private static void labyShots(Minecraft client) {
        if (client.player == null) {
            fail("player missing for labymod shots");
            return;
        }
        hideChrome(client);
        McBridge.thirdPerson(client.options);
        client.options.fov().set(30);
        stopWalk(client);
        client.player.setYRot(0f);
        client.player.setYBodyRot(0f);
        client.player.setYHeadRot(0f);
        client.player.setXRot(0f);
        client.player.getAbilities().flying = false;
        settleCloak(client);
        if (labyError != null) {
            fail(labyError);
            return;
        }
        if (labyPhase == 0) {
            client.player.connection.sendCommand("fill -3 140 -8 3 140 40 minecraft:barrier");
            client.player.connection.sendCommand("fill -4 141 -10 4 147 44 minecraft:air");
            client.player.connection.sendCommand("tp @s 0.5 141 0.5 0 0");
            labyPhase = 1;
            ticks = 0;
            return;
        }
        if (labyFrames == null) {
            if (ticks > timeout(20 * 90)) {
                fail("labymod catalog did not load");
            }
            return;
        }
        if (labyIndex >= labyFrames.size()) {
            labyOff(client);
            return;
        }
        LabyFrame frame = labyFrames.get(labyIndex);
        if (labyPhase == 1) {
            showCape(client);
            ClientSettings.get().enableLabyMod = true;
            ClientSettings.get().localOnly = false;
            LabyWear.wear(frame.png);
            CapeLayerWatch.reset();
            McBridge.clearChat(client);
            labyPhase = 2;
            ticks = 0;
            return;
        }
        if (labyPhase == 2) {
            if (ticks < 40) {
                return;
            }
            boolean third = client.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK;
            float y = (float) client.player.getY();
            boolean standing = client.player.onGround() && y > 140f && y < 143f;
            if (LabyWear.showing() && CapeLayerWatch.localTexture != null && CapeLayerWatch.localCalls > 0 && third && standing) {
                McBridge.clearChat(client);
                queueShot(client, frame.name);
                LABY_EXPECTED.add(frame.name);
                labyPhase = 3;
                return;
            }
            if (ticks > timeout(20 * 20)) {
                fail("no labymod view for " + frame.name
                        + " showing=" + LabyWear.showing()
                        + " texture=" + CapeLayerWatch.localTexture
                        + " calls=" + CapeLayerWatch.localCalls
                        + " camera=" + client.options.getCameraType()
                        + " y=" + client.player.getY()
                        + " ground=" + client.player.onGround());
            }
            return;
        }
        if (labyPhase == 3 && SHOTS.isEmpty() && shotCool == 0) {
            labyIndex++;
            labyPhase = 1;
            ticks = 0;
        }
    }

    private static void labyOff(Minecraft client) {
        if (labyPhase != 4) {
            LabyWear.clear();
            ClientSettings.get().enableLabyMod = false;
            OwnerCape bare = new OwnerCape();
            bare.png = new byte[0];
            bare.style = "flat";
            OwnerCape.set(bare);
            CapeTextures.clear("local");
            CapeLayerWatch.reset();
            McBridge.clearChat(client);
            labyPhase = 4;
            ticks = 0;
            return;
        }
        if (ticks < 12) {
            return;
        }
        if (!LABY_EXPECTED.contains("labymod-toggle-off")) {
            boolean bare = CapeLayerWatch.localHidden > 0 && CapeLayerWatch.localTexture == null && !LabyWear.showing();
            if (bare) {
                McBridge.clearChat(client);
                queueShot(client, "labymod-toggle-off");
                LABY_EXPECTED.add("labymod-toggle-off");
            } else if (ticks > timeout(20 * 15)) {
                fail("toggle-off still drew " + CapeLayerWatch.localTexture + " showing=" + LabyWear.showing());
            }
            return;
        }
        if (!SHOTS.isEmpty() || shotCool > 0) {
            return;
        }
        if (!labyFilesReady()) {
            if (ticks > timeout(20 * 30)) {
                fail("labymod shots were not written");
            }
            return;
        }
        state = State.DONE;
    }

    private static boolean labyFilesReady() {
        Path dir = captures();
        for (String name : LABY_EXPECTED) {
            Path file = dir.resolve(name + ".png");
            try {
                if (!Files.isRegularFile(file) || Files.size(file) < 5000) {
                    return false;
                }
            } catch (java.io.IOException e) {
                return false;
            }
        }
        return LABY_EXPECTED.size() >= 3;
    }


*///?}
    private static void hideChrome(Minecraft client) {
        client.options.hideGui = true;
        client.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
        McBridge.clearChat(client);
    }

//? if dev_catalog {
    /*private record LabyFrame(String kind, String name, byte[] png) {
    }


*///?}
    private enum State {
        WAIT_TITLE, OPEN_WORLD, WAIT_PLAYER, CAPE, WORLD, GALLERY, BACK, LABY, COMPARE, CLOAK, AUDIT, FLOWS, UI, DONE
    }
}
