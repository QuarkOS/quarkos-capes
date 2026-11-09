package dev.quarkos.capes.sim;

/**
 * Original cape cloth. The settings match the ones Wavey Capes exposes
 * (movement, mesh, wind, gravity, height, strafe, gravity vector). The step
 * itself is not that mod's code: a 16-point chain whose links cannot grow
 * past rest length, with a traveling ripple while the player moves.
 */
public final class WaveCloth {
    public static final int SEGMENTS = 16;

    public enum Movement {
        CLASSIC, SIMULATION, SIMULATION_V2, DUNGEONS
    }

    public enum Mesh {
        BLOCKY, SMOOTH
    }

    public enum Wind {
        NONE, WAVES
    }

    public record Settings(
            Movement movement,
            Mesh mesh,
            Wind wind,
            int gravity,
            int heightMultiplier,
            int straveMultiplier,
            boolean computeGravityVector
    ) {
        public static Settings defaults() {
            return new Settings(Movement.SIMULATION_V2, Mesh.SMOOTH, Wind.NONE, 25, 6, 2, false);
        }

        public Settings clamped() {
            if (movement != null && mesh != null && wind != null && gravity >= 5 && gravity <= 32
                    && heightMultiplier >= 4 && heightMultiplier <= 16 && straveMultiplier >= 1 && straveMultiplier <= 8) return this;
            return new Settings(
                    movement == null ? Movement.SIMULATION_V2 : movement,
                    mesh == null ? Mesh.SMOOTH : mesh,
                    wind == null ? Wind.NONE : wind,
                    clamp(gravity, 5, 32),
                    clamp(heightMultiplier, 4, 16),
                    clamp(straveMultiplier, 1, 8),
                    computeGravityVector
            );
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    /**
     * Player motion for one tick, in player space.
     * {@code forward} positive means the player moved forward, so the cloth lags backward.
     */
    public record Motion(
            float forward,
            float vertical,
            float lateral,
            boolean underwater,
            boolean swimming,
            float swimPitchDeg,
            boolean sneaking,
            boolean sneakStarted,
            long timeMs,
            float gravX,
            float gravY,
            float gravZ
    ) {
        public static Motion still() {
            return new Motion(0, 0, 0, false, false, 0, false, false, 0, 0, -1, 0);
        }
    }

    /** Converts tick displacement into player space; teleports do not inject motion. */
    public static Motion playerMotion(double dx, double dy, double dz, float yaw, boolean underwater,
                                      boolean swimming, float pitch, boolean sneaking, boolean sneakStarted,
                                      long timeMs, float gravX, float gravY, float gravZ) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)
                || dx * dx + dy * dy + dz * dz > 16.0) dx = dy = dz = 0;
        double radians = Math.toRadians(yaw);
        float forward = (float) (-dx * Math.sin(radians) + dz * Math.cos(radians));
        float lateral = (float) (dx * Math.cos(radians) + dz * Math.sin(radians));
        return new Motion(forward, (float) dy, lateral, underwater, swimming, pitch, sneaking, sneakStarted,
                timeMs, gravX, gravY, gravZ);
    }

    /** Pitch (back swing, radians) and roll (side, radians) for the classic bend. */
    public record ClassicPose(float[] pitch, float[] roll) {
    }

    public static final class State {
        public final float[] x = new float[SEGMENTS];
        public final float[] y = new float[SEGMENTS];
        public final float[] z = new float[SEGMENTS];
        public final float[] px = new float[SEGMENTS];
        public final float[] py = new float[SEGMENTS];
        public final float[] pz = new float[SEGMENTS];

        final float[] targetX = new float[SEGMENTS], targetY = new float[SEGMENTS], targetZ = new float[SEGMENTS];
        final float[] renderX = new float[SEGMENTS], renderY = new float[SEGMENTS], renderZ = new float[SEGMENTS];
        final float[] leftX = new float[SEGMENTS], leftY = new float[SEGMENTS];
        final float[] rightX = new float[SEGMENTS], rightY = new float[SEGMENTS];
        final float[] classicPitch = new float[SEGMENTS], classicRoll = new float[SEGMENTS];

        public State() {
            reset();
        }

        public void reset() {
            for (int i = 0; i < SEGMENTS; i++) {
                x[i] = 0;
                y[i] = -i;
                z[i] = 0;
                px[i] = x[i];
                py[i] = y[i];
                pz[i] = z[i];
            }
        }

        public State copy() {
            State copy = new State();
            System.arraycopy(x, 0, copy.x, 0, SEGMENTS);
            System.arraycopy(y, 0, copy.y, 0, SEGMENTS);
            System.arraycopy(z, 0, copy.z, 0, SEGMENTS);
            System.arraycopy(px, 0, copy.px, 0, SEGMENTS);
            System.arraycopy(py, 0, copy.py, 0, SEGMENTS);
            System.arraycopy(pz, 0, copy.pz, 0, SEGMENTS);
            return copy;
        }
    }

    private WaveCloth() {
    }

    public static ClassicPose classic(Settings settings, Motion motion) {
        return classic(settings, motion, new float[SEGMENTS], new float[SEGMENTS]);
    }

    public static ClassicPose classic(Settings settings, Motion motion, State state) {
        return classic(settings, motion, state.classicPitch, state.classicRoll);
    }

    private static ClassicPose classic(Settings settings, Motion motion, float[] pitch, float[] roll) {
        Settings tuned = settings.clamped();
        float trail = clamp(motion.forward * 1.6f, -0.35f, 1.25f);
        float lift = clamp(-motion.vertical * (tuned.heightMultiplier / 6f) * 0.4f, -0.7f, 0.9f);
        if (motion.sneakStarted) {
            lift += 0.3f;
        }
        float side = tuned.movement == Movement.SIMULATION ? 0f : motion.lateral * tuned.straveMultiplier * 0.08f;
        for (int i = 0; i < SEGMENTS; i++) {
            float along = i / (float) (SEGMENTS - 1);
            pitch[i] = (trail + lift) * along + windBend(tuned, motion, i);
            roll[i] = side * along;
        }
        return new ClassicPose(pitch, roll);
    }

    /** Advances a chain. Classic movement does not use this state. */
    public static void step(State state, Settings settings, Motion motion) {
        Settings tuned = settings.clamped();
        if (tuned.movement == Movement.CLASSIC) {
            return;
        }
        float[] tx = state.targetX;
        float[] ty = state.targetY;
        float[] tz = state.targetZ;
        shape(tuned, motion, tx, ty, tz);
        float follow = tuned.movement == Movement.DUNGEONS ? 0.55f : 0.42f;
        for (int i = 1; i < SEGMENTS; i++) {
            state.px[i] = state.x[i];
            state.py[i] = state.y[i];
            state.pz[i] = state.z[i];
            state.x[i] += (tx[i] - state.x[i]) * follow;
            state.y[i] += (ty[i] - state.y[i]) * follow;
            state.z[i] += (tz[i] - state.z[i]) * follow;
        }
        // One pass from the shoulder pin. A link may bunch, but it cannot grow past rest length.
        pin(state);
        for (int i = 0; i < SEGMENTS - 1; i++) {
            float dx = state.x[i + 1] - state.x[i];
            float dy = state.y[i + 1] - state.y[i];
            float dz = state.z[i + 1] - state.z[i];
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 1f) {
                float scale = 1f / dist;
                state.x[i + 1] = state.x[i] + dx * scale;
                state.y[i + 1] = state.y[i] + dy * scale;
                state.z[i + 1] = state.z[i] + dz * scale;
            }
        }
        pin(state);
    }

    /**
     * Rest pose is a straight hang of {@code SEGMENTS - 1} links.
     * Moving lifts that hang into the 45–80 degree range. A bulge then travels
     * down the cloth. Positive {@code x} is behind the player. Negative {@code y} is down.
     * Every link of the shape is at most one rest length.
     */
    private static void shape(Settings tuned, Motion motion, float[] tx, float[] ty, float[] tz) {
        float gravity = motion.underwater ? tuned.gravity / 10f : tuned.gravity;
        float weight = gravity / 25f;
        float speed = Math.abs(motion.forward);
        float lift = liftDegrees(speed) / (0.65f + 0.35f * weight);
        if (motion.vertical > 0f) {
            lift += Math.min(12f, motion.vertical * tuned.heightMultiplier);
        } else if (motion.vertical < 0f) {
            lift += Math.max(-18f, motion.vertical * tuned.heightMultiplier * 0.5f);
        }
        if (motion.sneakStarted) {
            lift += 8f;
        }
        if (motion.swimming) {
            lift *= 0.55f;
        }
        lift = clamp(lift, 0f, 80f);
        float dir = 1f; // The body prevents the chain from swinging through the torso when reversing.
        float sideAmp = tuned.movement == Movement.SIMULATION ? 0f : motion.lateral * tuned.straveMultiplier * 0.08f;
        if (tuned.computeGravityVector) {
            sideAmp += motion.gravX * 0.15f;
            lift += Math.max(0f, -motion.gravY) * 8f;
            lift = clamp(lift, 0f, 80f);
        }
        tx[0] = 0f;
        ty[0] = 0f;
        tz[0] = 0f;
        float pitch = (float) Math.toRadians(lift);
        float dx = (float) Math.sin(pitch) * dir;
        float dy = -(float) Math.cos(pitch);
        // Perpendicular to the hang, in the swing plane, so the ripple is a belly in the cloth.
        float px = (float) Math.cos(pitch) * dir;
        float py = (float) Math.sin(pitch);
        // Double, not float. currentTimeMillis * a small rate underflows a float and the wave freezes.
        double travel = motion.timeMs * 0.008;
        float amp = speed < 0.03f ? 0f : 3.2f;
        for (int i = 1; i < SEGMENTS; i++) {
            float along = i / (float) (SEGMENTS - 1);
            // Zero at the shoulder and the hem, so the ripple is a belly and the chord stays at the lift.
            float bulge = (float) (Math.sin(travel - along * 6.0) * amp * Math.sin(along * Math.PI));
            tx[i] = Math.max(0f, dx * i + px * bulge);
            ty[i] = dy * i + py * bulge;
            tz[i] = sideAmp * along * 4f;
        }
        for (int i = 0; i < SEGMENTS - 1; i++) {
            float lx = tx[i + 1] - tx[i];
            float ly = ty[i + 1] - ty[i];
            float lz = tz[i + 1] - tz[i];
            float dist = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
            if (dist > 1f) {
                float scale = 1f / dist;
                tx[i + 1] = tx[i] + lx * scale;
                ty[i + 1] = ty[i] + ly * scale;
                tz[i + 1] = tz[i] + lz * scale;
            }
        }
    }

    /** How far a moving cape swings back from a straight hang, in degrees. */
    private static float liftDegrees(float speed) {
        if (speed < 0.03f) {
            return 0f;
        }
        if (speed < 0.2f) {
            float t = (speed - 0.03f) / 0.17f;
            return 52f + t * 8f;
        }
        float t = Math.min(1f, (speed - 0.2f) / 0.25f);
        return 64f + t * 10f;
    }

    public static void run(State state, Settings settings, Motion motion, int ticks) {
        for (int i = 0; i < ticks; i++) {
            step(state, settings, motion);
        }
    }

    private static void pin(State state) {
        state.x[0] = 0;
        state.y[0] = 0;
        state.z[0] = 0;
        state.px[0] = 0;
        state.py[0] = 0;
        state.pz[0] = 0;
    }

    /**
     * Extra bend for waves, in radians. Amplitude is 3 degrees and the phase
     * advances three times slower underwater. It is not a force in {@link #step}.
     */
    public static float windBend(Settings settings, Motion motion, int index) {
        if (settings == null || settings.wind != Wind.WAVES) {
            return 0f;
        }
        double divisor = motion.underwater ? 9.0 : 3.0;
        double phase = (motion.timeMs / divisor) * 0.02 + index * 0.35;
        return (float) (Math.toRadians(3.0) * Math.sin(phase));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
