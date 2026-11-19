package dev.quarkos.capes.client.harness;

final class CapeSmoothness {
    record Stats(int frames, int missing, double fps, double maxFrameMs, double meanStep, float maxStep, float jerk,
                 double stepFrac) { }

    private static final float STILL = 0.05f;
    private static final float JUMP = 2.25f;
    private int frames, missing, steps, secondDifferences, jumps;
    private float lastAngle, lastDelta, maxStep, jerk;
    private double totalStep;
    private long firstFrame, lastFrame, maxFrameNanos;
    private boolean awaitingDraw;
    private int consecutive;

    void frame(long now) {
        finishFrame();
        if (frames + missing == 0) firstFrame = now;
        else maxFrameNanos = Math.max(maxFrameNanos, now - lastFrame);
        lastFrame = now;
        awaitingDraw = true;
    }

    void draw(float angle) {
        if (!awaitingDraw) return;
        if (!Float.isFinite(angle)) {
            finishFrame();
            return;
        }
        awaitingDraw = false;
        frames++;
        if (consecutive > 0) {
            float delta = angle - lastAngle;
            float step = Math.abs(delta);
            steps++;
            totalStep += step;
            maxStep = Math.max(maxStep, step);
            if (consecutive > 1) {
                secondDifferences++;
                jerk = Math.max(jerk, Math.abs(delta - lastDelta));
                if (Math.abs(lastDelta) <= STILL && step >= JUMP) jumps++;
            }
            lastDelta = delta;
        }
        lastAngle = angle;
        consecutive++;
    }

    Stats finish() {
        finishFrame();
        double fps = lastFrame > firstFrame ? (frames + missing - 1) * 1e9 / (lastFrame - firstFrame) : 0;
        return new Stats(frames, missing, fps, maxFrameNanos / 1e6, totalStep / Math.max(1, steps), maxStep, jerk,
                jumps / (double) Math.max(1, secondDifferences));
    }

    private void finishFrame() {
        if (awaitingDraw) {
            missing++;
            consecutive = 0;
            awaitingDraw = false;
        }
    }
}
