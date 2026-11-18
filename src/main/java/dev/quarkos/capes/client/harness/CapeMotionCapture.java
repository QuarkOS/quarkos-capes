package dev.quarkos.capes.client.harness;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.screen.CapeBoard;
import dev.quarkos.capes.sim.CapeGeometry;
import dev.quarkos.capes.sim.WaveCloth;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Captures six frames and records the mesh or model angles used for each rendered cape. */
public final class CapeMotionCapture {
    private record Shape(String name, CapeBook.Kind kind, String style) { }
    private record Sample(float[] values) { }
    record Result(String name, String detail, String failure) { }

    private static final List<Shape> SHAPES = List.of(new Shape("2d", CapeBook.Kind.QUAD, "flat"),
            new Shape("wavy", CapeBook.Kind.CLOTH, "wave"), new Shape("3d", CapeBook.Kind.MODEL, "model"));
    private static final int FRAMES = 6;
    private static final int FRAME_INTERVAL = 4;
    private static final int SETTLE_TICKS = 24;
    private static final float MIN_CHANGE = 0.02f;
    private static final long TIMEOUT = 20_000_000_000L;
    private static CapeMotionCapture active;

    private final Path directory;
    private final List<Path> files = new ArrayList<>(FRAMES);
    private final List<Sample> samples = new ArrayList<>(FRAMES);
    private int sequence, age, nextShot;
    private long deadline;
    private UUID playerId;
    private Sample latest, captured;
    private double startX, startZ, travel;

    CapeMotionCapture(Path directory) { this.directory = directory; }

    boolean finished() { return sequence == SHAPES.size() * 2; }
    private Shape shape() { return SHAPES.get(sequence / 2); }
    private boolean walking() { return sequence % 2 == 1; }
    private String scene() { return "shape-" + shape().name() + (walking() ? "-walk" : "-idle"); }

    Result tick(Minecraft client) {
        String name = scene();
        try {
            if (age == 0) start(client);
            age++;
            client.options.keyUp.setDown(walking() && files.size() < FRAMES);
            if (files.size() < FRAMES && age >= nextShot && (latest != captured || age >= nextShot + 8)) capture(client, name);
            if (System.nanoTime() > deadline) return complete(client, name, "", "Timed out waiting for six rendered frames");
            if (files.size() < FRAMES) return null;
            for (Path file : files) if (!UiHarness.completePng(file)) return null;
            stitch(directory.resolve(name + "-strip.png"));
            float change = maxChange(samples);
            String detail = String.format(Locale.ROOT, "travel=%.3f blocks, cape change=%.3f", travel, change);
            String failure = null;
            if (samples.size() != FRAMES) failure = "Missing cape draw observations for " + shape().name();
            else if (walking() && travel < 0.8) failure = "The player did not walk forward";
            else if (!walking() && travel > 0.08) failure = "The player moved during idle capture";
            else if ((walking() || shape().kind() == CapeBook.Kind.CLOTH) && change < MIN_CHANGE) {
                failure = "Cape was static across the captures";
            }
            return complete(client, name, detail, failure);
        } catch (IOException | RuntimeException | AssertionError failure) {
            return complete(client, name, "", failure.toString());
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
        }
        travel = Math.max(travel, Math.hypot(client.player.getX() - startX, client.player.getZ() - startZ));
        if (latest != null && latest != captured) {
            captured = latest;
            samples.add(latest);
        }
        Path file = directory.resolve(name + "-" + files.size() + ".png");
        Files.deleteIfExists(file);
        McBridge.shot(client, file);
        files.add(file);
        nextShot = age + FRAME_INTERVAL;
        if (files.size() == FRAMES) client.options.keyUp.setDown(false);
    }

    void stop(Minecraft client) {
        active = null;
        client.options.keyUp.setDown(false);
    }

    private Result complete(Minecraft client, String name, String detail, String failure) {
        stop(client);
        files.clear();
        samples.clear();
        latest = captured = null;
        travel = 0;
        sequence++;
        age = 0;
        return new Result(name, detail, failure);
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
            float[] previous = samples.get(i - 1).values();
            float[] current = samples.get(i).values();
            for (int component = 0; component < current.length; component++) {
                if (!Float.isFinite(previous[component]) || !Float.isFinite(current[component])) {
                    throw new AssertionError("Cape pose contained non-finite coordinates");
                }
                largest = Math.max(largest, Math.abs(current[component] - previous[component]));
            }
        }
        return largest;
    }

    public static void vanilla(UUID id, float pitch, float yaw, float roll) {
        if (tracking(id, CapeBook.Kind.QUAD)) active.latest = new Sample(new float[]{pitch, yaw, roll});
    }

    public static void custom(UUID id, CapeBook.Kind kind, List<CapeGeometry.Quad> quads, float pitch, float roll) {
        if (!tracking(id, kind)) return;
        if (kind == CapeBook.Kind.CLOTH) {
            int stride = quads.size() / (WaveCloth.SEGMENTS - 1);
            CapeGeometry.Quad middle = quads.get(stride * 7);
            CapeGeometry.Quad hem = quads.get(quads.size() - stride);
            active.latest = new Sample(new float[]{middle.x1(), middle.y1(), middle.z1(), hem.x1(), hem.y1(), hem.z1()});
        } else {
            active.latest = new Sample(new float[]{pitch, roll});
        }
    }

    public static boolean tracking(UUID id, CapeBook.Kind kind) {
        return active != null && id.equals(active.playerId) && active.shape().kind() == kind;
    }
}
