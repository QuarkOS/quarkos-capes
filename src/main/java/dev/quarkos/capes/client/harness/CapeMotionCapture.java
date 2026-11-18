package dev.quarkos.capes.client.harness;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.screen.CapeBoard;
import dev.quarkos.capes.sim.CapeGeometry;
import dev.quarkos.capes.sim.WaveCloth;
import net.minecraft.client.Minecraft;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.UUID;

/** Captures six frames and records the mesh or model angles used for each rendered cape. */
public final class CapeMotionCapture {
    private record Shape(String name, CapeBook.Kind kind, String style) { }
    private record Sample(float angle, float[] values) { }
    record Result(String name, String detail, String failure) { }

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
    private static final float MIN_CHANGE = 0.02f;
    private static final double MIN_SPRINT_SPEED = 0.24;
    private static final long TIMEOUT = 20_000_000_000L;
    private static CapeMotionCapture active;

    private final Path directory;
    private final List<Path> files = new ArrayList<>(FRAMES);
    private final List<Sample> samples = new ArrayList<>(FRAMES);
    private int sequence, age, nextShot, firstShot;
    private long deadline;
    private UUID playerId;
    private Sample latest, captured;
    private double startX, startZ, travel, speed;

    CapeMotionCapture(Path directory) { this.directory = directory; }

    boolean finished() { return sequence == SHAPES.size() * GAITS.length; }
    private Shape shape() { return SHAPES.get(sequence / GAITS.length); }
    private Gait gait() { return GAITS[sequence % GAITS.length]; }
    private String scene() { return "shape-" + shape().name() + "-" + gait().suffix; }

    Result tick(Minecraft client) {
        String name = scene();
        try {
            if (age == 0) start(client);
            age++;
            drive(client, files.size() < FRAMES ? gait() : Gait.IDLE);
            if (files.size() < FRAMES && age >= nextShot && (latest != captured || age >= nextShot + 8)) capture(client, name);
            if (System.nanoTime() > deadline) return complete(client, name, "Timed out waiting for six rendered frames");
            if (files.size() < FRAMES) return null;
            for (Path file : files) if (!UiHarness.completePng(file)) return null;
            stitch(directory.resolve(name + "-strip.png"));
            return complete(client, name, null);
        } catch (IOException | RuntimeException | AssertionError failure) {
            return complete(client, name, failure.toString());
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
        active = this;
        nextShot = SETTLE_TICKS;
        deadline = System.nanoTime() + TIMEOUT;
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
        if (latest != null && latest != captured) {
            captured = latest;
            samples.add(latest);
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
    }

    private Result complete(Minecraft client, String name, String failure) {
        stop(client);
        float change = maxChange(samples);
        StringJoiner angles = new StringJoiner(", ", "[", "]");
        for (Sample sample : samples) angles.add(String.format(Locale.ROOT, "%.1f", sample.angle()));
        String detail = String.format(Locale.ROOT, "travel=%.3f blocks, speed=%.3f blocks/tick, cape change=%.3f, anglesDeg=%s",
                travel, speed, change, angles);
        if (failure == null) failure = validate(change);
        files.clear();
        samples.clear();
        latest = captured = null;
        travel = speed = 0;
        sequence++;
        age = 0;
        return new Result(name, detail, failure);
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

    public static void rigid(UUID id, CapeBook.Kind kind, Quaternionfc rotation) {
        if (!tracking(id, kind)) return;
        var down = rotation.transform(new Vector3f(0f, 1f, 0f));
        active.latest = new Sample(angleFromVertical(down.x, down.y, down.z), new float[]{down.x, down.y, down.z});
    }

    public static void cloth(UUID id, List<CapeGeometry.Quad> quads) {
        if (!tracking(id, CapeBook.Kind.CLOTH)) return;
        int stride = quads.size() / (WaveCloth.SEGMENTS - 1);
        CapeGeometry.Quad shoulder = quads.getFirst();
        CapeGeometry.Quad middle = quads.get(stride * 7);
        CapeGeometry.Quad hem = quads.get(quads.size() - stride);
        // Use the chord between the shoulder and hem centers, including lateral displacement.
        float dx = (hem.x1() + hem.x2() - shoulder.x0() - shoulder.x3()) / 2f;
        float dy = (hem.y1() + hem.y2() - shoulder.y0() - shoulder.y3()) / 2f;
        float dz = (hem.z1() + hem.z2() - shoulder.z0() - shoulder.z3()) / 2f;
        active.latest = new Sample(angleFromVertical(dx, dy, dz),
                new float[]{middle.x1(), middle.y1(), middle.z1(), hem.x1(), hem.y1(), hem.z1()});
    }

    private static float angleFromVertical(float x, float down, float z) {
        return (float) Math.toDegrees(Math.atan2(Math.hypot(x, z), down));
    }

    public static boolean tracking(UUID id, CapeBook.Kind kind) {
        return active != null && id.equals(active.playerId) && active.shape().kind() == kind;
    }
}
