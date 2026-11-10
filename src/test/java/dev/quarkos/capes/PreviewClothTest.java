package dev.quarkos.capes;

import dev.quarkos.capes.sim.PreviewCloth;
import dev.quarkos.capes.sim.WaveCloth;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PreviewClothTest {
    @Test void switchingSamplesCannotCarryWorldSwingIntoThePreview() {
        for (var movement : WaveCloth.Movement.values()) {
            var settings = new WaveCloth.Settings(movement, WaveCloth.Mesh.SMOOTH, WaveCloth.Wind.NONE, 25, 6, 2, true);
            var world = new WaveCloth.State();
            var falling = WaveCloth.playerMotion(1, -3, 1, 180, false, false, 0, false, false, 1, 0, -1, 0);
            WaveCloth.run(world, settings, falling, 20);
            var saved = world.copy();
            var first = PreviewCloth.state(settings);
            // Simulate a stale pose from a previously equipped sample.
            java.util.Arrays.fill(first.y, 100f);
            java.util.Arrays.fill(first.py, 100f);
            var next = PreviewCloth.state(settings);
            for (int i = 1; i < WaveCloth.SEGMENTS; i++) {
                assertTrue(Float.isFinite(next.y[i]) && next.y[i] < 0, movement + " cape must hang below its pin");
                assertEquals(next.y[i], next.py[i], 0.001f, "settled interpolation must not reintroduce a swing");
            }
            assertArrayEquals(saved.y, world.y, "preview must leave world physics alone");
            assertArrayEquals(saved.py, world.py);
        }
    }
}
