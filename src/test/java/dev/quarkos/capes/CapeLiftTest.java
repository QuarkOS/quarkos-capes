package dev.quarkos.capes;

import dev.quarkos.capes.sim.CapeLift;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapeLiftTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void liftChangesBetweenFramesAndContinuesAcrossTicks() {
        CapeLift accelerating = CapeLift.RESTING.advance(0.216, 0);
        CapeLift sprinting = accelerating.advance(0.281, 0);

        assertTrue(accelerating.at(0.5f) > 6f);
        assertTrue(accelerating.at(0.5f) < accelerating.at(1f));
        assertEquals(accelerating.at(1f), sprinting.at(0f), EPSILON);

        float before = accelerating.at(1f) - accelerating.at(0.99f);
        float after = sprinting.at(0.01f) - sprinting.at(0f);
        assertEquals(before, after, 0.005f);
    }

    @Test
    void liftAtTheSameGameTimeIsIndependentOfFrameRate() {
        CapeLift lift = CapeLift.RESTING.advance(0.216, 0).advance(0.281, 0);
        float reference = lift.at(2f / 3f);
        for (int fps : new int[]{60, 120, 240}) {
            float angle = 0f;
            for (int frame = 0; frame <= fps / 30; frame++) {
                angle = lift.at(frame * 20f / fps);
            }
            assertEquals(reference, angle, EPSILON);
        }
    }

    @Test
    void liftSettlesAtTheExistingSpeedTargetsAndStaysWithinTheCap() {
        CapeLift lift = CapeLift.RESTING;
        double[] speeds = {0.216, 0.281, 1.0, 0};
        float[] targets = {34f, 56f, 75f, 6f};
        for (int i = 0; i < speeds.length; i++) {
            for (int tick = 0; tick < 3; tick++) {
                lift = lift.advance(speeds[i], 0);
                for (int frame = 0; frame <= 12; frame++) {
                    float angle = lift.at(frame / 12f);
                    assertTrue(angle >= 6f && angle <= 75f, "lift " + angle);
                }
            }
            assertEquals(targets[i], lift.at(0.5f), EPSILON);
        }
    }
}
