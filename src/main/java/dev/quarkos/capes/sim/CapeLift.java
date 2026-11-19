package dev.quarkos.capes.sim;

/** Three consecutive tick targets for the speed-driven part of a rigid cape's pitch. */
public record CapeLift(float earlier, float previous, float current) {
    public static final CapeLift RESTING = new CapeLift(WaveCloth.REST_ANGLE_DEGREES,
            WaveCloth.REST_ANGLE_DEGREES, WaveCloth.REST_ANGLE_DEGREES);

    public CapeLift advance(double dx, double dz) {
        return new CapeLift(previous, current, target(dx, dz));
    }

    public static float target(double dx, double dz) {
        return WaveCloth.targetAngleDegrees((float) Math.hypot(dx, dz));
    }

    public float at(float partial) {
        float t = Math.clamp(partial, 0f, 1f);
        float remaining = 1f - t;
        // A quadratic B-spline. Both the midpoint angle and slope match at the next tick.
        return previous + (earlier - previous) * (0.5f * remaining * remaining)
                + (current - previous) * (0.5f * t * t);
    }
}
