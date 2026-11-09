package dev.quarkos.capes.client.harness;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.LocalFiles;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.net.CapeHttp;
import dev.quarkos.capes.client.net.LabyCatalog;
import dev.quarkos.capes.client.net.LabySource;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.render.CapeLayerWatch;
import dev.quarkos.capes.client.render.CapeTextures;
import dev.quarkos.capes.client.render.LabyWear;
import net.minecraft.client.Minecraft;
import net.minecraft.client.tutorial.TutorialSteps;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Still views of five LabyMod cosmetics beside laby.net's own render, then a walking side view.
 * Writes only the named files under {@code quarkos_capes.compare}.
 */
public final class LabyCompare {
    private static final int SCALE = Integer.getInteger("quarkos_capes.timeoutScale", 1);
    private static final float DISTANCE = 5.6f;
    private static final float SWING_DISTANCE = 6.4f;
    private static final float PITCH = 2f;
    private static final int FOV = 34;
    private static final String RENDER = "https://laby.net/api/v3/render/cape/";
    private static final Item[] ITEMS = {
            new Item("migrator", "5b37a01fde6a3e075f3bc5694c18e667"),
            new Item("pan", "a60d608f0cac6ad8c50f2258b6c4db92"),
            new Item("labymod-blue", "a0c8e54d8f03aaa9f1ba58190b75ef4a"),
            new Item("minecon-2016", "5064684a769e82814e8f62091335b2d8"),
            new Item("minecon-2012", "fae278f51848b74716fe333f19e0b963")
    };
    private static final int SWING_FRAMES = 12;

    private static int ticks;
    private static int index;
    private static int phase;
    private static int swingShot = -1;
    private static String error;
    private static List<Frame> frames;

    private LabyCompare() {
    }

    public static void arm(Minecraft client) {
        ticks = 0;
        index = 0;
        phase = 0;
        swingShot = -1;
        error = null;
        frames = null;
        client.options.hideGui = true;
        client.options.tutorialStep = TutorialSteps.NONE;
        client.getTutorial().setStep(TutorialSteps.NONE);
        client.options.fov().set(FOV);
        client.options.gamma().set(1.0);
        McBridge.thirdPerson(client.options);
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
        ShotView.install(client);
        ShotView.frame(0f, PITCH, DISTANCE);
        Thread thread = new Thread(LabyCompare::load, "quarkos-laby-compare");
        thread.setDaemon(true);
        thread.start();
    }

    /** @return true when every comparison file has been written */
    public static boolean tick(Minecraft client) {
        ticks++;
        if (client.player == null) {
            fail("player missing for labymod compare");
            return false;
        }
        hold(client);
        if (error != null) {
            fail(error);
            return false;
        }
        if (phase == 0) {
            if (ticks % 10 == 1) {
                client.player.connection.sendCommand("forceload add -1 -1 1 2");
                client.player.connection.sendCommand("fill -4 140 -4 4 140 20 minecraft:barrier");
                client.player.connection.sendCommand("fill -5 141 -5 5 156 22 minecraft:air");
                client.player.connection.sendCommand("tp @s 0.5 141 0.5 0 0");
            }
            float y = (float) client.player.getY();
            if (client.player.onGround() && y > 140f && y < 143f) {
                phase = 1;
                ticks = 0;
                return false;
            }
            if (ticks > timeout(20 * 20)) {
                fail("could not stand the player at y=" + client.player.getY() + " ground=" + client.player.onGround());
            }
            return false;
        }
        if (frames == null) {
            if (ticks > timeout(20 * 90)) {
                fail("labymod compare textures did not load");
            }
            return false;
        }
        if (index < ITEMS.length) {
            return still(client);
        }
        return swing(client);
    }

    private static boolean still(Minecraft client) {
        Frame frame = frames.get(index);
        if (phase == 1) {
            wear(client, frame);
            ShotView.frame(0f, PITCH, DISTANCE);
            phase = 2;
            ticks = 0;
            return false;
        }
        if (phase == 2) {
            if (ticks < 30 || !ready(client)) {
                if (ticks > timeout(20 * 20)) {
                    fail("no back view for " + frame.name
                            + " showing=" + LabyWear.showing()
                            + " texture=" + CapeLayerWatch.localTexture
                            + " calls=" + CapeLayerWatch.localCalls
                            + " camera=" + client.options.getCameraType()
                            + " y=" + client.player.getY()
                            + " ground=" + client.player.onGround());
                }
                return false;
            }
            McBridge.shot(client, raw(frame.name + "-back.png"));
            phase = 3;
            ticks = 0;
            return false;
        }
        if (phase == 3) {
            if (ticks < 4 || !written(raw(frame.name + "-back.png"))) {
                if (ticks > timeout(20 * 20)) {
                    fail("back shot missing for " + frame.name);
                }
                return false;
            }
            compose(frame, frame.name + "-back.png");
            ShotView.frame(50f, PITCH, DISTANCE);
            phase = 4;
            ticks = 0;
            return false;
        }
        if (phase == 4) {
            if (ticks < 8 || !ready(client)) {
                if (ticks > timeout(20 * 20)) {
                    fail("no three-quarter view for " + frame.name);
                }
                return false;
            }
            McBridge.shot(client, raw(frame.name + "-three-quarter.png"));
            phase = 5;
            ticks = 0;
            return false;
        }
        if (ticks < 4 || !written(raw(frame.name + "-three-quarter.png"))) {
            if (ticks > timeout(20 * 20)) {
                fail("three-quarter shot missing for " + frame.name);
            }
            return false;
        }
        compose(frame, frame.name + "-three-quarter.png");
        index++;
        phase = 1;
        ticks = 0;
        return false;
    }

    private static boolean swing(Minecraft client) {
        Frame blue = frames.get(2);
        if (phase != 6 && phase != 7 && phase != 8) {
            wear(client, blue);
            ShotView.frame(82f, PITCH, SWING_DISTANCE);
            client.options.keyUp.setDown(false);
            phase = 6;
            ticks = 0;
            swingShot = 0;
            return false;
        }
        if (phase == 6) {
            if (ticks < 10 || !ready(client)) {
                if (ticks > timeout(20 * 20)) {
                    fail("no side view for the blue cloak");
                }
                return false;
            }
            client.options.keyUp.setDown(true);
            phase = 7;
            ticks = 0;
            return false;
        }
        if (phase == 7) {
            client.options.keyUp.setDown(true);
            if (ticks >= 8 && ticks % 2 == 0 && swingShot < SWING_FRAMES) {
                swingShot++;
                McBridge.shot(client, raw(String.format("swing-%02d.png", swingShot)));
            }
            if (swingShot >= SWING_FRAMES && ticks > 8 + SWING_FRAMES * 2) {
                client.options.keyUp.setDown(false);
                phase = 8;
                ticks = 0;
            }
            return false;
        }
        for (int i = 1; i <= SWING_FRAMES; i++) {
            if (!written(raw(String.format("swing-%02d.png", i)))) {
                if (ticks > timeout(20 * 30)) {
                    fail("swing frame " + i + " was not written");
                }
                return false;
            }
        }
        gif();
        ShotView.clear();
        client.options.keyUp.setDown(false);
        return true;
    }

    private static void wear(Minecraft client, Frame frame) {
        client.options.hideGui = true;
        ClientSettings.get().enableLabyMod = true;
        ClientSettings.get().localOnly = false;
        LabyWear.wear(frame.png);
        CapeLayerWatch.reset();
        McBridge.clearChat(client);
    }

    private static void hold(Minecraft client) {
        client.options.hideGui = true;
        McBridge.thirdPerson(client.options);
        client.options.fov().set(FOV);
        stopWalk(client);
        if (phase == 7) {
            client.options.keyUp.setDown(true);
        }
        client.player.setYRot(0f);
        client.player.setYBodyRot(0f);
        client.player.setYHeadRot(0f);
        client.player.setXRot(0f);
        client.player.getAbilities().flying = false;
        if (phase != 7) {
            var motion = client.player.getDeltaMovement();
            client.player.setDeltaMovement(0.0, motion.y, 0.0);
        }
    }

    private static boolean ready(Minecraft client) {
        boolean third = client.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_BACK;
        float y = (float) client.player.getY();
        boolean standing = client.player.onGround() && y > 140f && y < 143f;
        return LabyWear.showing()
                && CapeLayerWatch.localTexture != null
                && CapeLayerWatch.localCalls > 0
                && third
                && standing;
    }

    private static void load() {
        try {
            Path cache = LocalFiles.folder().resolve("labymod");
            List<Frame> loaded = new ArrayList<>();
            for (Item item : ITEMS) {
                byte[] png = LabySource.texture(item.hash, cache, CapeHttp::labyExchange);
                if (png == null) {
                    throw new IllegalStateException("missing texture " + item.name);
                }
                LabySource.Hit hit = LabySource.exchange(RENDER + item.hash + ".png", "render", CapeHttp::labyExchange);
                if (hit == null || hit.status() / 100 != 2 || hit.body() == null || hit.body().length < 8) {
                    throw new IllegalStateException("missing render " + item.name);
                }
                loaded.add(new Frame(item.name, item.hash, png, hit.body()));
            }
            frames = loaded;
        } catch (Exception e) {
            error = e.getMessage() == null ? e.toString() : e.getMessage();
        }
    }

    private static void compose(Frame frame, String name) {
        try {
            BufferedImage game = ImageIO.read(raw(name).toFile());
            BufferedImage render = ImageIO.read(new ByteArrayInputStream(frame.render));
            if (game == null || render == null) {
                fail("could not read " + name);
                return;
            }
            int height = game.getHeight();
            int renderWidth = Math.max(1, render.getWidth() * height / render.getHeight());
            BufferedImage out = new BufferedImage(game.getWidth() + renderWidth, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D draw = out.createGraphics();
            draw.drawImage(game, 0, 0, null);
            draw.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            draw.drawImage(render, game.getWidth(), 0, renderWidth, height, null);
            draw.dispose();
            Path dest = compare().resolve(name);
            Files.createDirectories(dest.getParent());
            ImageIO.write(out, "png", dest.toFile());
        } catch (Exception e) {
            fail("compose " + name + ": " + e.getMessage());
        }
    }

    private static void gif() {
        Path dest = compare().resolve("labymod-blue-swing.gif");
        try {
            Files.createDirectories(dest.getParent());
            Path log = raw("ffmpeg.log");
            Process process = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-start_number", "1",
                    "-framerate", "10",
                    "-i", raw("swing-%02d.png").toString(),
                    "-vf", "split[s0][s1];[s0]palettegen=stats_mode=diff[p];[s1][p]paletteuse",
                    "-loop", "0",
                    dest.toString()
            ).redirectOutput(log.toFile()).redirectErrorStream(true).start();
            int code = process.waitFor();
            if (code != 0 || !Files.isRegularFile(dest) || Files.size(dest) < 1000) {
                String detail = Files.isRegularFile(log) ? Files.readString(log) : "";
                fail("swing gif failed: " + detail);
            }
        } catch (Exception e) {
            fail("swing gif failed: " + e.getMessage());
        }
    }

    private static boolean written(Path file) {
        try {
            return Files.isRegularFile(file) && Files.size(file) >= 5000;
        } catch (Exception e) {
            return false;
        }
    }

    private static Path raw(String name) {
        String configured = System.getProperty("quarkos_capes.captures");
        Path root = configured == null || configured.isBlank() ? Path.of("build/laby-compare-raw") : Path.of(configured);
        return root.resolve(name);
    }

    private static Path compare() {
        String configured = System.getProperty("quarkos_capes.compare");
        if (configured == null || configured.isBlank()) {
            return Path.of("build/laby-compare");
        }
        return Path.of(configured);
    }

    private static void stopWalk(Minecraft client) {
        if (phase == 7) {
            return;
        }
        client.options.keyUp.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false);
    }

    private static int timeout(int value) {
        return Math.max(1, value) * Math.max(1, SCALE);
    }

    private static void fail(String message) {
        System.err.println("quarkos_capes harness fail: " + message);
        System.exit(1);
    }

    private record Item(String name, String hash) {
    }

    private record Frame(String name, String hash, byte[] png, byte[] render) {
    }
}
