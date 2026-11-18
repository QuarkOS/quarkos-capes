//? if fabric {
package dev.quarkos.capes.client.harness;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.Samples;
import dev.quarkos.capes.client.net.CapeHttp;
import dev.quarkos.capes.client.net.GalleryPages;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.render.CapeTextures;
import dev.quarkos.capes.client.render.PlayerFacts;
import dev.quarkos.capes.client.screen.CapeActions;
import dev.quarkos.capes.client.screen.CapeGalleryScreen;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.gui.screens.ConnectScreen;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Two offline clients on a dedicated server. Dev auth stays in this harness. */
public final class ShareHarness {
    private static final UUID CAPE_A = UUID.fromString("3befb6c6-35ed-3de0-baf2-708342de5c88");
    private static boolean armed;
    private static String role = "";
    private static State state = State.WAIT_TITLE;
    private static int ticks;
    private static int shotTick = -1;
    private static boolean workStarted;
    private static volatile String workNote = "";
    private static String equippedId;
    private static CapeGalleryScreen gallery;

    private ShareHarness() {
    }

    public static void start(Minecraft client, String which) {
        role = which;
        if (!armed) {
            armed = true;
            client.options.pauseOnLostFocus = false;
            client.options.onboardAccessibility = false;
            //? if >=1.21 {
            client.options.menuBackgroundBlurriness().set(0);
            //?}
            client.options.guiScale().set(2);
            client.options.renderDistance().set(8);
            client.options.simulationDistance().set(8);
            client.options.framerateLimit().set(60);
            client.options.fov().set(30);
            online();
        }
        ticks++;
        if (client.player != null) {
            McBridge.clearChat(client);
            McBridge.clearToasts(client);
            McBridge.showCape(client.options);
            if (ticks % 20 == 0) {
                client.options.broadcastOptions();
            }
        }
        try {
            if ("a".equals(role)) {
                clientA(client);
            } else {
                clientB(client);
            }
        } catch (RuntimeException e) {
            fail(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private static void clientA(Minecraft client) {
        switch (state) {
            case WAIT_TITLE -> title(client);
            case JOIN -> join(client);
            case WAIT_PLAYER -> player(client, State.SETUP);
            case SETUP -> setupA(client);
            case WAIT_WORK -> waitWork(client, "Published.", State.GALLERY);
            case GALLERY -> galleryShot(client, "gallery-publish", State.FRAME);
            case FRAME -> frame(client, 0, 200, 0, 12, true, 40, "client-a", State.HOLD);
            case HOLD -> hold(client);
            case DONE -> finish();
            default -> fail("unexpected state " + state);
        }
    }

    private static void clientB(Minecraft client) {
        switch (state) {
            case WAIT_TITLE -> {
                if (!Files.isRegularFile(share().resolve("published"))) {
                    if (ticks > 20 * 180) {
                        fail("publish marker did not appear");
                    }
                    return;
                }
                if (ticks > 1) {
                    ticks = 0;
                }
                title(client);
            }
            case JOIN -> join(client);
            case WAIT_PLAYER -> player(client, State.SETUP);
            case SETUP -> setupB(client);
            case WAIT_WORK -> waitSearch(client);
            case GALLERY -> galleryShot(client, "gallery-search", State.EQUIP);
            case EQUIP -> equip(client);
            case REPORT -> report(client);
            case REPORT_SHOT -> galleryShot(client, "gallery-report", State.FRAME);
            case FRAME -> frame(client, 16, 200, 0, 12, true, 40, "client-b", State.LOOK);
            case LOOK -> frame(client, 0, 200, 5, 8, false, 30, "client-b-sees-a", State.DONE);
            case DONE -> finish();
            default -> fail("unexpected state " + state);
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
        }
        if (!(McBridge.currentScreen(client) instanceof TitleScreen) || client.getOverlay() != null) {
            if (ticks > 20 * 120) {
                fail("title screen did not open");
            }
            return;
        }
        state = State.JOIN;
        ticks = 0;
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
        state = State.WAIT_PLAYER;
        ticks = 0;
    }

    private static void player(Minecraft client, State next) {
        if (client.player == null || client.level == null) {
            if (ticks > 20 * 120) {
                fail("did not join the dedicated server");
            }
            return;
        }
        if (ticks < 30) {
            return;
        }
        state = next;
        ticks = 0;
    }

    private static void setupA(Minecraft client) {
        if (ticks == 1) {
            command(client, "gamemode creative @s");
            command(client, "tp @s 0 200 0 180 12");
            command(client, "gamerule send_command_feedback false");
            command(client, "gamerule log_admin_commands false");
            command(client, "gamerule advance_time false");
            command(client, "time set day");
            CapeActions.bands();
            CapeActions.style("flat", false);
        }
        holdPose(client, 0, 200, 0, 12);
        if (ticks == 20 && !workStarted) {
            workStarted = true;
            if (!HarnessAuth.active()) {
                fail("dev auth is off");
                return;
            }
            Thread thread = new Thread(() -> {
                try {
                    HarnessAuth.Session session = HarnessAuth.complete(dev.quarkos.capes.client.net.CapeCalls.DEFAULT_SERVICE_URL, "CapeA");
                    byte[] png = Samples.bands();
                    int gallery = HarnessAuth.putGallery(dev.quarkos.capes.client.net.CapeCalls.DEFAULT_SERVICE_URL, session, "bands", png, "Bands");
                    int worn = HarnessAuth.putWorn(dev.quarkos.capes.client.net.CapeCalls.DEFAULT_SERVICE_URL, session, png);
                    if (gallery / 100 != 2 || worn / 100 != 2) {
                        workNote = "publish status " + gallery + " worn " + worn;
                        return;
                    }
                    Files.writeString(share().resolve("published"), "bands\n");
                    workNote = "Published.";
                } catch (Exception e) {
                    workNote = e.getMessage() == null ? e.toString() : e.getMessage();
                }
            }, "quarkos-capes-dev");
            thread.setDaemon(true);
            thread.start();
        }
        if (ticks > 20 * 40) {
            fail(workNote.isBlank() ? "publish did not finish" : workNote);
        }
        if ("Published.".equals(workNote)) {
            state = State.WAIT_WORK;
            ticks = 0;
        }
    }

    private static void waitWork(Minecraft client, String expected, State next) {
        if (!expected.equals(workNote)) {
            if (ticks > 10) {
                fail(workNote.isBlank() ? "publish did not finish" : workNote);
            }
            return;
        }
        gallery = new CapeGalleryScreen(null);
        McBridge.setScreen(client, gallery);
        state = next;
        ticks = 0;
    }

    private static void setupB(Minecraft client) {
        if (ticks == 1) {
            command(client, "gamemode creative @s");
            command(client, "tp @s 16 200 0 180 12");
            command(client, "gamerule send_command_feedback false");
            gallery = new CapeGalleryScreen(null);
            gallery.search("band");
            McBridge.setScreen(client, gallery);
        }
        if (ticks >= 2) {
            state = State.WAIT_WORK;
            ticks = 0;
        }
    }

    private static void waitSearch(Minecraft client) {
        String note = gallery.status();
        if (note.startsWith("Search for \"band\" returned ")) {
            boolean found = false;
            for (GalleryPages.Entry entry : gallery.sharedEntries()) {
                String title = entry.title() == null ? "" : entry.title().toLowerCase();
                if (title.contains("band")) {
                    found = true;
                    equippedId = entry.id();
                }
            }
            if (!found) {
                fail(note);
                return;
            }
            state = State.GALLERY;
            ticks = 0;
            return;
        }
        if (ticks > 20 * 20 && !note.isBlank() && !note.startsWith("Search")) {
            fail(note);
        }
        if (ticks > 20 * 30) {
            fail(note.isBlank() ? "search did not finish" : note);
        }
    }

    private static void galleryShot(Minecraft client, String name, State next) {
        McBridge.hideHud(client, true);
        if (McBridge.currentScreen(client) != gallery) {
            if (ticks > 20) {
                fail(name + " screen did not open");
            }
            return;
        }
        if (ticks == 12) {
            McBridge.clearChat(client);
            McBridge.clearToasts(client);
            McBridge.shot(client, captures().resolve(name + ".png"));
        }
        if (ticks > 30) {
            state = next;
            ticks = 0;
        }
    }

    private static void equip(Minecraft client) {
        if (ticks == 1) {
            CapeHttp.open(equippedId, opened -> {
                CapeHttp.equip(opened.detail(), opened.png());
                workNote = "equipped";
            }, message -> workNote = message);
        }
        if ("equipped".equals(workNote)) {
            workNote = "";
            CapeHttp.report(equippedId, message -> workNote = message);
            state = State.REPORT;
            ticks = 0;
            return;
        }
        if (ticks > 20 * 20) {
            fail(workNote.isBlank() ? "equip did not finish" : workNote);
        }
    }

    private static void report(Minecraft client) {
        String note = workNote;
        if (note.startsWith("Reported.")) {
            state = State.REPORT_SHOT;
            ticks = 0;
            return;
        }
        if (note.startsWith("Report did not finish")) {
            fail(note);
        }
        if (ticks > 20 * 20) {
            fail(note.isBlank() ? "report did not finish" : note);
        }
    }

    private static void frame(Minecraft client, double x, double y, double z, float pitch, boolean third, int at, String shot, State next) {
        if (client.player == null) {
            fail("player missing");
            return;
        }
        if (ticks == 1) {
            shotTick = -1;
            McBridge.hideHud(client, false);
            McBridge.setScreen(client, null);
            command(client, "tp @s " + trim(x) + " " + trim(y) + " " + trim(z) + " 180 " + (int) pitch);
            client.options.setCameraType(third ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
            client.options.fov().set(30);
            client.options.broadcastOptions();
        }
        holdPose(client, x, y, z, pitch);
        client.options.setCameraType(third ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
        if (client.player.getY() < y - 2 && ticks % 20 == 0) {
            command(client, "tp @s " + trim(x) + " " + trim(y) + " " + trim(z) + " 180 " + (int) pitch);
        }
        if (client.player.getY() < y - 2) {
            if (ticks > 20 * 20) {
                fail("player stayed at y=" + client.player.getY());
            }
            return;
        }
        if (!third && !remoteReady()) {
            if (ticks > 20 * 40) {
                fail("CapeA's published cape was not on CapeB");
            }
            return;
        }
        if (shotTick < 0 && ticks >= at) {
            McBridge.clearChat(client);
            McBridge.clearToasts(client);
            McBridge.shot(client, captures().resolve(shot + ".png"));
            System.out.println("quarkos_capes shot " + shot + " y=" + client.player.getY());
            shotTick = ticks;
        }
        if (shotTick >= 0 && ticks > shotTick + 25) {
            if ("a".equals(role) && "client-a".equals(shot)) {
                command(client, "tp @s 0 200 0 180 0");
            }
            shotTick = -1;
            state = next;
            ticks = 0;
        }
    }

    private static String trim(double value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static boolean remoteReady() {
        PlayerFacts.Fact fact = PlayerFacts.byUuid(CAPE_A);
        return fact != null && fact.capePart()
                && CapeBook.remote(CAPE_A) != null
                && CapeTextures.get(CapeBook.textureKey(CAPE_A)) != null;
    }

    private static void hold(Minecraft client) {
        holdPose(client, 0, 200, 0, 0);
        client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (client.player != null && client.player.getY() < 190 && ticks % 20 == 0) {
            command(client, "tp @s 0 200 0 180 0");
        }
        if (Files.isRegularFile(share().resolve("b-done"))) {
            state = State.DONE;
        }
        if (ticks > 20 * 240) {
            fail("client B did not finish");
        }
    }

    private static void holdPose(Minecraft client, double x, double y, double z, float pitch) {
        var player = client.player;
        if (player == null) {
            return;
        }
        player.setYRot(180f);
        player.setXRot(pitch);
        player.yRotO = 180f;
        player.xRotO = pitch;
        player.yBodyRot = 180f;
        player.yBodyRotO = 180f;
        player.yHeadRot = 180f;
        player.yHeadRotO = 180f;
        player.setDeltaMovement(0, 0, 0);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        if (player.getY() < y - 0.5 || player.getY() > y + 0.5) {
            player.setPos(x, y, z);
        }
    }

    private static void command(Minecraft client, String command) {
        client.player.connection.sendCommand(command);
        McBridge.clearChat(client);
    }

    private static void online() {
        ClientSettings settings = ClientSettings.get();
        settings.localOnly = false;
        settings.showOthers = true;
        settings.baseUrl = dev.quarkos.capes.client.net.CapeCalls.DEFAULT_SERVICE_URL;
        settings.enableOptifine = false;
        settings.enableLabyMod = false;
        settings.enableMinecraftCapesMod = false;
        settings.enableCosmetica = false;
        settings.enableCloaksPlus = false;
    }

    private static Path share() {
        String configured = System.getProperty("quarkos_capes.share");
        Path path = configured == null || configured.isBlank() ? Path.of("run/share") : Path.of(configured);
        try {
            Files.createDirectories(path);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return path;
    }

    private static Path captures() {
        String configured = System.getProperty("quarkos_capes.captures");
        Path path = configured == null || configured.isBlank() ? Path.of("build/captures") : Path.of(configured);
        try {
            Files.createDirectories(path);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return path;
    }

    private static void finish() {
        try {
            if ("b".equals(role)) {
                Files.writeString(share().resolve("b-done"), "ok\n");
            }
        } catch (Exception e) {
            fail(e.getMessage());
            return;
        }
        System.out.println("quarkos_capes share " + role + " ok");
        System.exit(0);
    }

    private static void fail(String message) {
        System.err.println("quarkos_capes share " + role + " fail: " + message);
        System.exit(1);
    }

    private enum State {
        WAIT_TITLE, JOIN, WAIT_PLAYER, SETUP, WAIT_WORK, GALLERY, EQUIP, REPORT, REPORT_SHOT, FRAME, LOOK, HOLD, DONE
    }
}
//?}
