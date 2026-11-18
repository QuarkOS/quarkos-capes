package dev.quarkos.capes;

import dev.quarkos.capes.client.render.CapeDraw;
import dev.quarkos.capes.client.render.PlayerFacts;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RigidCapeMotionTest {
    private static final UUID ID = new UUID(0, 1);
    private static final float EPSILON = 0.0001f;

    @Test
    void rigidPitchFollowsHorizontalSpeed() {
        assertEquals(6f, pitch(0, 0, 0, 0), EPSILON);
        assertEquals(34f, pitch(0.216, 0, 0, 0), EPSILON);
        assertEquals(56f, pitch(0, -0.281, 0, 0), EPSILON);
        double diagonal = 0.216 / Math.sqrt(2);
        assertEquals(34f, pitch(diagonal, diagonal, 0, 0), EPSILON);
    }

    @Test
    void bobAndSwayKeepWalkingAndSprintingInRange() {
        // Vanilla bounds bob at 0.1 and multiplies it by 32 degrees.
        for (float bob : new float[]{-3.2f, 3.2f}) {
            for (float sway : new float[]{0f, 20f}) {
                float walk = tilt(pitch(0.216, 0, bob, sway), sway);
                float sprint = tilt(pitch(0.281, 0, bob, sway), sway);
                assertTrue(walk >= 30f && walk <= 40f, "walk " + walk);
                assertTrue(sprint >= 50f && sprint <= 65f, "sprint " + sprint);
            }
        }
    }

    @Test
    void rigidPitchCapsCombinedTiltAndRetainsVerticalFlap() {
        assertEquals(6f, pitch(Double.NaN, 0, 0, 0), EPSILON);
        assertEquals(6f, pitch(0, Double.POSITIVE_INFINITY, 0, 0), EPSILON);
        assertEquals(75f, pitch(1, 0, 0, 0), EPSILON);
        assertEquals(75f, tilt(pitch(1, 0, 32f, -20f), -20f), EPSILON);
        assertEquals(44f, pitch(0.216, 0, 10f, 20f), EPSILON);
        assertEquals(28f, pitch(0.216, 0, -6f, 20f), EPSILON);
    }

    private static float pitch(double dx, double dz, float flap, float sway) {
        PlayerFacts.Fact fact = new PlayerFacts.Fact(ID, "Player", 1, false, false, false, false, false, true,
                0f, 0f, 0f, 0f, 1, 10 + dx, 65, 20 + dz, 10, 64, 20);
        float lean = CapeDraw.rigidLean(fact, 100f, false);
        return 6f + lean / 2f + CapeDraw.boundedFlap(lean, flap, sway, false);
    }

    private static float tilt(float pitch, float sway) {
        return (float) Math.toDegrees(Math.acos(Math.cos(Math.toRadians(pitch)) * Math.cos(Math.toRadians(sway / 2f))));
    }
}
