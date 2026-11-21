package dev.quarkos.capes.client.harness;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.render.CapeDraw;
import dev.quarkos.capes.client.screen.CapeBoard;
import dev.quarkos.capes.sim.CapeGeometry;
import dev.quarkos.capes.sim.WaveCloth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Quaternionf;
import org.joml.Vector3f;
//? if >=1.21.11 {
import net.minecraft.client.InactivityFpsLimit;
//?}

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.UUID;

/** Captures angle checks and compares smoothness at render cadence. */
public final class CapeMotionCapture {
    private record Shape(String name, CapeBook.Kind kind, String style) { }
    private record Sample(float angle, float[] values) { }
    private record Trial(CapeSmoothness.Stats stats, double speed) { }
    record Result(String name, String detail, String failure, List<String> metrics) { }

    private record Runway(int x, int z) {
        private static final int FEET_Y = 311;

        static Runway build(LocalPlayer player) {
            // Keep the fill in the player's chunk and the two chunks directly ahead.
            int x = Math.floorDiv((int) Math.floor(player.getX()), 16) * 16 + 8;
            int z = Math.floorDiv((int) Math.floor(player.getZ()), 16) * 16 + 4;
            String atStart = "execute positioned " + x + " " + FEET_Y + " " + z + " run ";
            player.connection.sendCommand(atStart + "fill ~-4 ~-1 ~-4 ~4 ~-1 ~40 minecraft:smooth_stone");
            player.connection.sendCommand(atStart + "fill ~-4 ~ ~-4 ~4 ~8 ~40 minecraft:air");
            return new Runway(x, z);
        }

        boolean atStart(LocalPlayer player) {
            return player.onGround() && player.getYRot() == 0f
                    && Math.abs(player.getX() - (x + 0.5)) < 0.05
                    && Math.abs(player.getY() - FEET_Y) < 0.05
                    && Math.abs(player.getZ() - (z + 0.5)) < 0.05;
        }

        void reset(LocalPlayer player) {
            player.connection.sendCommand("tp @s " + (x + 0.5) + " " + FEET_Y + " " + (z + 0.5) + " 0 0");
        }
    }

    private enum Phase { SHOTS, LEGACY, SMOOTHED }

    private enum Gait {
        IDLE("idle", 0f, 15f), WALK("walk", 15f, 45f), SPRINT("sprint", 40f, 70f);

        final String suffix;
        final float minAngle;
        final float maxAngle;

        Gait(String suffix, float minAngle, float maxAngle) {
            this.suffix = suffix;
            this.minAngle = minAngle;
            this.maxAngle = maxAngle;
        }

        boolean accepts(float angle) {
            return angle >= minAngle && (this == IDLE ? angle < maxAngle : angle <= maxAngle);
        }
    }

    private static final List<Shape> SHAPES = List.of(new Shape("2d", CapeBook.Kind.QUAD, "flat"),
            new Shape("wavy", CapeBook.Kind.CLOTH, "wave"), new Shape("3d", CapeBook.Kind.MODEL, "model"));
    private static final Gait[] GAITS = Gait.values();
    private static final int FRAMES = 6;
    private static final int FRAME_INTERVAL = 4;
    private static final int SETTLE_TICKS = 24;
    private static final int TRIAL_TICKS = 40;
    private static final int TRIAL_FPS = 60;
    private static final int MIN_TRIAL_FPS = 40;
    private static final int MIN_TRIAL_FRAMES = TRIAL_TICKS * MIN_TRIAL_FPS / 20;
    private static final float MIN_CHANGE = 0.02f;
    private static final double MIN_SPRINT_SPEED = 0.24;
    private static final long TIMEOUT = 20_000_000_000L;
    private static final String RUNWAY_TIMEOUT = "Timed out waiting for the motion capture runway start";
    private static CapeMotionCapture active;

    private final Path directory;
    private final List<Path> files = new ArrayList<>(FRAMES);
    private final List<Sample> samples = new ArrayList<>(FRAMES);
    private final float[] latestPose = new float[6];
    private final Quaternionf rotation = new Quaternionf();
    private final Vector3f down = new Vector3f();
    private int sequence, age, nextShot, firstShot;
    private long deadline;
    private UUID playerId;
    private Runway runway;
    private boolean waitingForRunway;
    private float latestAngle;
    private int latestSize, draws, capturedDraw;
    private double startX, startZ, travel, speed;
    private Phase phase = Phase.SHOTS;
    private CapeSmoothness window;
    private Trial legacy;
    private int oldFps = -1;
    private boolean oldVsync;
    //? if >=1.21.11 {
    private InactivityFpsLimit oldInactivityLimit;
    //?}

    CapeMotionCapture(Path directory) { this.directory = directory; }

    boolean finished() { return sequence == SHAPES.size() * GAITS.length; }
    private Shape shape() { return SHAPES.get(sequence / GAITS.length); }
    private Gait gait() { return GAITS[sequence % GAITS.length]; }
    private String scene() { return "shape-" + shape().name() + "-" + gait().suffix; }

    Result tick(Minecraft client) {
        if (phase != Phase.SHOTS) return measure(client);
        String name = scene();
        try {
            if (active != this) start(client);
            if (waitingForStart(client)) {
                return System.nanoTime() > deadline ? completeShots(client, name, RUNWAY_TIMEOUT) : null;
            }
            age++;
            drive(client, files.size() < FRAMES ? gait() : Gait.IDLE);
            if (files.size() < FRAMES && age >= nextShot && (draws != capturedDraw || age >= nextShot + 8)) capture(client, name);
            if (System.nanoTime() > deadline) return completeShots(client, name, "Timed out waiting for six rendered frames");
            if (files.size() < FRAMES) return null;
            for (Path file : files) if (!UiHarness.completePng(file)) return null;
            stitch(directory.resolve(name + "-strip.png"));
            return completeShots(client, name, null);
        } catch (IOException | RuntimeException | AssertionError failure) {
            return completeShots(client, name, failure.toString());
        }
    }

    private static void drive(Minecraft client, Gait gait) {
        client.options.keyUp.setDown(gait != Gait.IDLE);
        boolean sprinting = gait == Gait.SPRINT;
        var sprintKey = client.options.keySprint;
        if (sprintKey.isDown() != sprinting) {
            // With toggle sprint, a key press switches either way.
            sprintKey.setDown(sprinting || client.options.toggleSprint().get());
        }
    }

    private void start(Minecraft client) throws IOException {
        if (client.player == null) throw new AssertionError("No local player for cape capture");
        client.setScreen(null);
        CapeBoard.equipSample("cloak");
        CapeBoard.style(shape().style());
        CapeBook.preview = false;
        CapeBook.draft = null;
        if (!CapeBoard.sampleOn("cloak") || OwnerCape.get().png.length == 0) throw new AssertionError("Cloak did not equip");
        McBridge.showCape(client.options);
        McBridge.thirdPerson(client.options);
        McBridge.hideHud(client, true);
        ShotView.frame(43f, 8f, 3.5f);
        Files.deleteIfExists(directory.resolve(scene() + "-strip.png"));
        playerId = client.player.getUUID();
        resetStart(client);
        active = this;
        CapeDraw.harnessSmoothing(true);
        nextShot = SETTLE_TICKS;
    }

    private void resetStart(Minecraft client) {
        drive(client, Gait.IDLE);
        LocalPlayer player = client.player;
        if (runway == null) runway = Runway.build(player);
        waitingForRunway = !runway.atStart(player);
        if (waitingForRunway) runway.reset(player);
        deadline = System.nanoTime() + TIMEOUT;
    }

    private boolean waitingForStart(Minecraft client) {
        if (waitingForRunway && client.player != null && runway.atStart(client.player)) {
            waitingForRunway = false;
            deadline = System.nanoTime() + TIMEOUT;
        }
        return waitingForRunway;
    }

    private void capture(Minecraft client, String name) throws IOException {
        if (client.player == null || client.screen != null) throw new AssertionError("Gameplay was interrupted during cape capture");
        if (files.isEmpty()) {
            startX = client.player.getX();
            startZ = client.player.getZ();
            firstShot = age;
        } else {
            double distance = Math.hypot(client.player.getX() - startX, client.player.getZ() - startZ);
            travel = Math.max(travel, distance);
            speed = distance / (age - firstShot);
        }
        if (draws != capturedDraw) {
            capturedDraw = draws;
            samples.add(new Sample(latestAngle, Arrays.copyOf(latestPose, latestSize)));
        }
        Path file = directory.resolve(name + "-" + files.size() + ".png");
        Files.deleteIfExists(file);
        McBridge.shot(client, file);
        files.add(file);
        nextShot = age + FRAME_INTERVAL;
        if (files.size() == FRAMES) drive(client, Gait.IDLE);
    }

    void stop(Minecraft client) {
        if (active != this) return;
        active = null;
        drive(client, Gait.IDLE);
        CapeDraw.harnessSmoothing(null);
        if (oldFps >= 0) {
            client.options.framerateLimit().set(oldFps);
            client.options.enableVsync().set(oldVsync);
            //? if >=1.21.11 {
            client.options.inactivityFpsLimit().set(oldInactivityLimit);
            //?}
            oldFps = -1;
        }
    }

    private Result completeShots(Minecraft client, String name, String failure) {
        float change = maxChange(samples);
        StringJoiner angles = new StringJoiner(", ", "[", "]");
        for (Sample sample : samples) angles.add(String.format(Locale.ROOT, "%.1f", sample.angle()));
        String detail = String.format(Locale.ROOT, "travel=%.3f blocks, speed=%.3f blocks/tick, cape change=%.3f, anglesDeg=%s",
                travel, speed, change, angles);
        if (failure == null) failure = validate(change);
        files.clear();
        samples.clear();
        draws = capturedDraw = 0;
        travel = speed = 0;
        age = 0;
        if (!waitingForRunway && gait() != Gait.IDLE && active == this && client.player != null && client.screen == null) {
            oldFps = client.options.framerateLimit().get();
            oldVsync = client.options.enableVsync().get();
            client.options.framerateLimit().set(TRIAL_FPS);
            client.options.enableVsync().set(false);
            //? if >=1.21.11 {
            oldInactivityLimit = client.options.inactivityFpsLimit().get();
            client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
            //?}
            startTrial(client, Phase.LEGACY);
        } else {
            advance(client);
        }
        return new Result(name, detail, failure, List.of());
    }

    private void startTrial(Minecraft client, Phase next) {
        resetStart(client);
        CapeDraw.harnessSmoothing(next == Phase.SMOOTHED);
        phase = next;
        age = 0;
    }

    private Result measure(Minecraft client) {
        if (client.player == null || client.screen != null || !playerId.equals(client.player.getUUID())) {
            return completeTrials(client, null, "Gameplay was interrupted during the smoothness capture");
        }
        if (System.nanoTime() > deadline) {
            return completeTrials(client, null, waitingForRunway ? RUNWAY_TIMEOUT : "Timed out during the smoothness capture");
        }
        if (waitingForStart(client)) return null;
        age++;
        if (age == SETTLE_TICKS) {
            startX = client.player.getX();
            startZ = client.player.getZ();
            window = new CapeSmoothness();
        }
        if (age < SETTLE_TICKS + 1 + TRIAL_TICKS) {
            drive(client, age > SETTLE_TICKS ? gait() : Gait.IDLE);
            return null;
        }

        double distance = Math.hypot(client.player.getX() - startX, client.player.getZ() - startZ);
        Trial trial = new Trial(window.finish(), distance / TRIAL_TICKS);
        window = null;
        if (phase == Phase.LEGACY) {
            legacy = trial;
            startTrial(client, Phase.SMOOTHED);
            return null;
        }
        return completeTrials(client, trial, smoothnessFailure(legacy, trial));
    }

    private Result completeTrials(Minecraft client, Trial smoothed, String failure) {
        List<String> lines = new ArrayList<>(2);
        if (legacy != null) lines.add(metric("off", legacy));
        if (smoothed != null) lines.add(metric("on", smoothed));
        String name = scene() + "-smoothness";
        advance(client);
        return new Result(name, "", failure, lines);
    }

    private void advance(Minecraft client) {
        stop(client);
        legacy = null;
        window = null;
        phase = Phase.SHOTS;
        age = 0;
        sequence++;
    }

    private String metric(String mode, Trial trial) {
        var stats = trial.stats();
        return String.format(Locale.ROOT,
                "CAPE_SMOOTH shape=%s gait=%s smooth=%s frames=%d missing=%d fps=%.1f maxFrameMs=%.1f "
                        + "speed=%.3f meanStep=%.4f maxStep=%.4f jerk=%.4f stepFrac=%.4f",
                shape().name(), gait().suffix, mode, stats.frames(), stats.missing(), stats.fps(), stats.maxFrameMs(), trial.speed(),
                stats.meanStep(), stats.maxStep(), stats.jerk(), stats.stepFrac());
    }

    private String smoothnessFailure(Trial before, Trial after) {
        var off = before.stats();
        var on = after.stats();
        if (off.missing() != 0 || on.missing() != 0) return "Missing cape angle samples in rendered frames";
        if (Math.min(off.frames(), on.frames()) < MIN_TRIAL_FRAMES || Math.min(off.fps(), on.fps()) < MIN_TRIAL_FPS
                || Math.max(off.fps(), on.fps()) > 1.25 * Math.min(off.fps(), on.fps())) {
            return "Need comparable trials at 40 FPS or above";
        }
        double minSpeed = gait() == Gait.SPRINT ? MIN_SPRINT_SPEED : 0.15;
        if (Math.min(before.speed(), after.speed()) < minSpeed || Math.abs(before.speed() - after.speed()) > 0.02) {
            return "The player did not reach comparable gait speeds";
        }
        if (on.stepFrac() > 0.025) return "Cape still shows flat frames followed by jumps";
        if (shape().kind() == CapeBook.Kind.CLOTH) {
            return on.jerk() > off.jerk() * 1.25f + 0.5f ? "Wavy cape jerk increased" : null;
        }
        return on.jerk() >= off.jerk() * 0.8f ? "Rigid cape jerk did not drop by 20%" : null;
    }

    private String validate(float change) {
        if (samples.size() != FRAMES) return "Missing cape draw observations for " + shape().name();
        Gait gait = gait();
        if (gait == Gait.IDLE && travel > 0.08) return "The player moved during idle capture";
        if (gait == Gait.WALK && travel < 0.8) return "The player did not walk forward";
        if (gait == Gait.SPRINT && speed < MIN_SPRINT_SPEED) return "The player did not reach sprint speed";
        for (int i = 0; i < samples.size(); i++) {
            float angle = samples.get(i).angle();
            if (!gait.accepts(angle)) {
                String range = gait == Gait.IDLE ? "below 15" : String.format(Locale.ROOT, "%.0f–%.0f", gait.minAngle, gait.maxAngle);
                return String.format(Locale.ROOT, "Frame %d cape angle %.1f° from vertical, expected %s°", i, angle, range);
            }
        }
        if (!Float.isFinite(change)) return "Cape pose contained non-finite coordinates";
        if ((gait != Gait.IDLE || shape().kind() == CapeBook.Kind.CLOTH) && change < MIN_CHANGE) {
            return "Cape was static across the captures";
        }
        return null;
    }

    private void stitch(Path stripFile) throws IOException {
        BufferedImage strip = null;
        for (int i = 0; i < files.size(); i++) {
            BufferedImage frame = ImageIO.read(files.get(i).toFile());
            if (frame == null) throw new IOException("Invalid cape capture " + files.get(i).getFileName());
            int width = Math.min(frame.getWidth(), 384);
            int height = Math.max(1, frame.getHeight() * width / frame.getWidth());
            if (strip == null) strip = new BufferedImage(width * FRAMES, height, BufferedImage.TYPE_INT_RGB);
            var graphics = strip.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(frame, i * width, 0, width, height, null);
            } finally {
                graphics.dispose();
            }
        }
        if (strip == null || !ImageIO.write(strip, "png", stripFile.toFile())) throw new IOException("Could not write " + stripFile);
    }

    private static float maxChange(List<Sample> samples) {
        float largest = 0;
        for (int i = 1; i < samples.size(); i++) {
            float[] current = samples.get(i).values();
            for (int j = 0; j < i; j++) {
                float[] earlier = samples.get(j).values();
                for (int component = 0; component < current.length; component++) {
                    largest = Math.max(largest, Math.abs(current[component] - earlier[component]));
                }
            }
        }
        return largest;
    }

    public static void frame(UUID cameraPlayerId) {
        if (active != null && active.window != null && cameraPlayerId.equals(active.playerId)) {
            active.window.frame(System.nanoTime());
        }
    }

    public static void rigid(UUID id, CapeBook.Kind kind, float pitch, float sway) {
        if (!tracking(id, kind)) return;
        CapeDraw.rigidRotation(active.rotation, pitch, sway);
        active.observeRigid();
    }

    public static void rigidModel(UUID id, ModelPart body) {
        if (!tracking(id, CapeBook.Kind.QUAD)) return;
        var cape = body.getChild("cape");
        active.rotation.rotationZYX(cape.zRot, cape.yRot, cape.xRot);
        active.observeRigid();
    }

    private void observeRigid() {
        rotation.transform(down.set(0f, 1f, 0f));
        float angle = angleFromVertical(down.x, down.y, down.z);
        if (window != null) {
            window.draw(angle);
        } else {
            latestAngle = angle;
            latestPose[0] = down.x;
            latestPose[1] = down.y;
            latestPose[2] = down.z;
            latestSize = 3;
            draws++;
        }
    }

    public static void cloth(UUID id, List<CapeGeometry.Quad> quads) {
        if (!tracking(id, CapeBook.Kind.CLOTH)) return;
        int stride = quads.size() / (WaveCloth.SEGMENTS - 1);
        CapeGeometry.Quad shoulder = quads.getFirst();
        CapeGeometry.Quad hem = quads.get(quads.size() - stride);
        // Use the chord between the shoulder and hem centers, including lateral displacement.
        float dx = (hem.x1() + hem.x2() - shoulder.x0() - shoulder.x3()) / 2f;
        float dy = (hem.y1() + hem.y2() - shoulder.y0() - shoulder.y3()) / 2f;
        float dz = (hem.z1() + hem.z2() - shoulder.z0() - shoulder.z3()) / 2f;
        float angle = angleFromVertical(dx, dy, dz);
        if (active.window != null) {
            active.window.draw(angle);
        } else {
            CapeGeometry.Quad middle = quads.get(stride * 7);
            active.latestAngle = angle;
            active.latestPose[0] = middle.x1();
            active.latestPose[1] = middle.y1();
            active.latestPose[2] = middle.z1();
            active.latestPose[3] = hem.x1();
            active.latestPose[4] = hem.y1();
            active.latestPose[5] = hem.z1();
            active.latestSize = 6;
            active.draws++;
        }
    }

    private static float angleFromVertical(float x, float down, float z) {
        return (float) Math.toDegrees(Math.atan2(Math.hypot(x, z), down));
    }

    private static boolean tracking(UUID id, CapeBook.Kind kind) {
        return active != null && (active.phase == Phase.SHOTS || active.window != null)
                && id.equals(active.playerId) && active.shape().kind() == kind;
    }
}
