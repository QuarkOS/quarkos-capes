package dev.quarkos.capes.sim;

/**
 * Original cape cloth with Wavey Capes-style settings. A 16-point chain keeps
 * links within rest length while the cloth ripples and settles with player motion.
 */
public final class WaveCloth {
    public static final int SEGMENTS = 16;
    public static final float REST_ANGLE_DEGREES = 6f;
    private static final float MAX_ANGLE_DEGREES = 75f;
    private static final double MIN_DOWN = Math.cos(Math.toRadians(MAX_ANGLE_DEGREES));
    private static final float WALK_SPEED = 0.216f;
    private static final float SPRINT_SPEED = 0.281f;
    private static final float MAX_SPEED = 0.44f;
    private static final float WALK_ANGLE = 34f;
    private static final float SPRINT_ANGLE = 56f;

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
            return idle(0);
        }

        public static Motion idle(long timeMs) {
            return new Motion(0, 0, 0, false, false, 0, false, false, timeMs, 0, -1, 0);
        }
    }

    /** Converts tick displacement into player space; teleports do not inject motion. */
    public static Motion playerMotion(double dx, double dy, double dz, float yaw, boolean underwater,
                                      boolean swimming, float pitch, boolean sneaking, boolean sneakStarted,
                                      long timeMs, float gravX, float gravY, float gravZ) {
        return playerMotion(dx, dy, dz, yaw, 0f, underwater, swimming, pitch, sneaking, sneakStarted,
                timeMs, gravX, gravY, gravZ);
    }

    public static Motion playerMotion(double dx, double dy, double dz, float yaw, float turn, boolean underwater,
                                      boolean swimming, float pitch, boolean sneaking, boolean sneakStarted,
                                      long timeMs, float gravX, float gravY, float gravZ) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)
                || dx * dx + dy * dy + dz * dz > 16.0) dx = dy = dz = 0;
        double radians = Math.toRadians(yaw);
        float forward = (float) (-dx * Math.sin(radians) + dz * Math.cos(radians));
        float lateral = (float) (dx * Math.cos(radians) + dz * Math.sin(radians)) - clamp(turn, -45f, 45f) * 0.04f;
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

        final float[] renderX = new float[SEGMENTS], renderY = new float[SEGMENTS], renderZ = new float[SEGMENTS];
        final float[] leftX = new float[SEGMENTS], leftY = new float[SEGMENTS];
        final float[] rightX = new float[SEGMENTS], rightY = new float[SEGMENTS];
        final float[] pitch = new float[SEGMENTS], roll = new float[SEGMENTS];

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
        float[] pitch = new float[SEGMENTS];
        float[] roll = new float[SEGMENTS];
        shape(settings.clamped(), motion, pitch, roll);
        return new ClassicPose(pitch, roll);
    }

    public static ClassicPose classic(Settings settings, Motion motion, State state) {
        shape(settings.clamped(), motion, state.pitch, state.roll);
        return new ClassicPose(state.pitch, state.roll);
    }

    /** Advances a chain. Classic movement does not use this state. */
    public static void step(State state, Settings settings, Motion motion) {
        Settings tuned = settings.clamped();
        if (tuned.movement == Movement.CLASSIC) {
            return;
        }
        shape(tuned, motion, state.pitch, state.roll);
        float follow = tuned.movement == Movement.DUNGEONS ? 0.55f : 0.42f;
        float x = 0f, y = 0f, z = 0f;
        pin(state);
        // The target links have unit length. Interpolating all points by the same amount
        // keeps every link at or below that length, including while the cape is settling.
        for (int i = 1; i < SEGMENTS; i++) {
            float pitch = state.pitch[i], roll = state.roll[i];
            float plane = (float) Math.cos(roll);
            x += (float) Math.sin(pitch) * plane;
            y -= (float) Math.cos(pitch) * plane;
            z += (float) Math.sin(roll);
            state.px[i] = state.x[i];
            state.py[i] = state.y[i];
            state.pz[i] = state.z[i];
            state.x[i] += (x - state.x[i]) * follow;
            state.y[i] += (y - state.y[i]) * follow;
            state.z[i] += (z - state.z[i]) * follow;
        }
    }

    /** Fills the angle of each link, measured back and sideways from a straight hang. */
    private static void shape(Settings tuned, Motion motion, float[] pitch, float[] roll) {
        float speed = Math.abs(motion.forward);
        float lift = REST_ANGLE_DEGREES + (targetAngleDegrees(speed) - REST_ANGLE_DEGREES) / weight(tuned, motion);
        lift += clamp(motion.vertical * (tuned.heightMultiplier / 6f) * 16f, -18f, 12f);
        if (motion.sneakStarted) {
            lift += 8f;
        }
        if (motion.swimming) {
            lift *= 0.55f;
        }
        boolean sway = tuned.movement != Movement.SIMULATION;
        float side = sway ? motion.lateral * tuned.straveMultiplier * 25f : 0f;
        if (tuned.computeGravityVector) {
            side += motion.gravX * 8f;
            lift += motion.gravZ * 8f;
        }
        lift = clamp(lift, 0f, MAX_ANGLE_DEGREES);
        float motionRipple = 23f * clamp(speed / SPRINT_SPEED, 0f, 1f);
        float idleRipple = tuned.wind == Wind.WAVES ? 5.6f : 2.2f;
        float idleSway = sway ? (tuned.wind == Wind.WAVES ? 2f : 0.8f) : 0f;
        double time = motion.timeMs * (motion.underwater ? 0.4 : 1.0);
        double breathingPhase = time * 0.0038;
        double motionPhase = time * 0.008;
        for (int i = 1; i < SEGMENTS; i++) {
            float along = i / (float) (SEGMENTS - 1);
            double idlePhase = breathingPhase - along * 1.6;
            float ripple = idleRipple * (float) Math.sin(idlePhase)
                    + motionRipple * (float) Math.sin(motionPhase - along * 6.0);
            float sideAngle = clamp((side + idleSway * (float) Math.cos(idlePhase)) * along, -20f, 20f);
            // The delayed phase travels through the hem, and the shoulder moves the least.
            pitch[i] = (float) Math.toRadians(clamp(lift + ripple * along, 0f, maxPitchDegrees(sideAngle)));
            roll[i] = (float) Math.toRadians(sideAngle);
        }
    }

    /** Steady pitch from vertical at default settings for forward displacement in blocks per tick. */
    public static float targetAngleDegrees(float forwardSpeed) {
        if (!Float.isFinite(forwardSpeed)) return REST_ANGLE_DEGREES;
        float speed = Math.abs(forwardSpeed);
        if (speed <= WALK_SPEED) {
            return REST_ANGLE_DEGREES + (WALK_ANGLE - REST_ANGLE_DEGREES) * (speed / WALK_SPEED);
        }
        if (speed <= SPRINT_SPEED) {
            float progress = (speed - WALK_SPEED) / (SPRINT_SPEED - WALK_SPEED);
            return WALK_ANGLE + (SPRINT_ANGLE - WALK_ANGLE) * progress;
        }
        float progress = Math.min(1f, (speed - SPRINT_SPEED) / (MAX_SPEED - SPRINT_SPEED));
        return SPRINT_ANGLE + (MAX_ANGLE_DEGREES - SPRINT_ANGLE) * progress;
    }

    /** The pitch budget at a given side angle, so their combined tilt stays within the limit. */
    public static float maxPitchDegrees(float rollDegrees) {
        return (float) Math.toDegrees(Math.acos(MIN_DOWN / Math.cos(Math.toRadians(rollDegrees))));
    }

    private static float weight(Settings settings, Motion motion) {
        return 0.65f + 0.35f * settings.gravity / (motion.underwater ? 250f : 25f);
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

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
