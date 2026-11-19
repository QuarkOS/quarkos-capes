package dev.quarkos.capes.client.harness;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CapeSmoothnessTest {
    @Test
    void countsRenderedFramesAndMeasuresTheChangeInAngularSteps() {
        var capture = new CapeSmoothness();
        float[] angles = {6f, 6f, 9f, 10f};
        for (int i = 0; i < angles.length; i++) {
            capture.frame(i * 20_000_000L);
            capture.draw(angles[i]);
            capture.draw(75f);
        }
        var result = capture.finish();
        assertEquals(4, result.frames());
        assertEquals(0, result.missing());
        assertEquals(50, result.fps());
        assertEquals(20, result.maxFrameMs());
        assertEquals(4.0 / 3, result.meanStep());
        assertEquals(3f, result.maxStep());
        assertEquals(3f, result.jerk());
        assertEquals(0.5, result.stepFrac());
    }

    @Test
    void reportsMissingDrawsWithoutCountingAGapAsAnAngleStep() {
        var capture = new CapeSmoothness();
        capture.frame(0);
        capture.draw(6f);
        capture.frame(20_000_000);
        capture.draw(Float.NaN);
        capture.frame(40_000_000);
        capture.draw(60f);
        capture.frame(60_000_000);

        var result = capture.finish();
        assertEquals(2, result.frames());
        assertEquals(2, result.missing());
        assertEquals(0f, result.maxStep());
    }
}
