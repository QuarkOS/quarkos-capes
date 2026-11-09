package dev.quarkos.capes;

import com.sun.management.ThreadMXBean;
import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.LocalSnapshot;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.local.Samples;
import dev.quarkos.capes.client.net.LabyCatalog;
import dev.quarkos.capes.client.net.RefreshGate;
import dev.quarkos.capes.sim.CapeGeometry;
import dev.quarkos.capes.sim.WaveCloth;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BughuntTest {
    private static volatile Object sink;

    private static WaveCloth.Motion motion(double x, double y, double z, float yaw) {
        return WaveCloth.playerMotion(x, y, z, yaw, false, false, 0, false, false, 1700000000000L, 0, -1, 0);
    }

    @Test void motionUsesPlayerAxesAndRejectsTeleports() {
        var south = motion(0, 0, 0.2, 0);
        var west = motion(-0.2, 0, 0, 90);
        var north = motion(0, 0, -0.2, 180);
        assertEquals(south.forward(), west.forward(), 0.00001);
        assertEquals(south.forward(), north.forward(), 0.00001);
        assertEquals(0, west.lateral(), 0.00001);
        assertEquals(0.2, motion(0.2, 0, 0, 0).lateral(), 0.00001);
        assertEquals(0, motion(1000, 0, -1000, 0).forward());
        assertEquals(0, motion(Double.NaN, 0, 0, 0).lateral());
    }

    @Test void reverseAndTeleportClothStaysFiniteBehindBody() {
        WaveCloth.State state = new WaveCloth.State();
        var settings = WaveCloth.Settings.defaults();
        for (int tick = 0; tick < 500; tick++) {
            var moving = tick == 250 ? motion(10000, 10000, -10000, 0) : motion(0, 0, -0.25, 0);
            WaveCloth.step(state, settings, moving);
            for (int i = 0; i < WaveCloth.SEGMENTS; i++) {
                assertTrue(Float.isFinite(state.x[i]) && Float.isFinite(state.y[i]) && Float.isFinite(state.z[i]));
                assertTrue(state.x[i] >= -0.0001, "torso collision at " + i);
                if (i > 0) {
                    double dx = state.x[i] - state.x[i - 1], dy = state.y[i] - state.y[i - 1], dz = state.z[i] - state.z[i - 1];
                    assertTrue(Math.sqrt(dx * dx + dy * dy + dz * dz) <= 1.001);
                }
            }
        }
    }

    @Test void stripAndClothSampleCapeAtlasNotPadding() {
        var horizontal = CapeGeometry.flat(2, 4, false).getFirst();
        assertEquals((2 + 1f / 64) / 4, horizontal.u0(), 0.00001);
        assertEquals((2 + 11f / 64) / 4, horizontal.u1(), 0.00001);
        assertEquals(1f / 32, horizontal.v0(), 0.00001);
        var vertical = CapeGeometry.flat(2, 4, true).getFirst();
        assertEquals((2 + 1f / 32) / 4, vertical.v0(), 0.00001);
        WaveCloth.State state = new WaveCloth.State();
        var cloth = CapeGeometry.cloth(state, WaveCloth.Settings.defaults(), WaveCloth.Motion.still(), null, null, 2, 4, true, false);
        assertEquals(vertical.u0(), cloth.getFirst().u0());
        assertEquals(vertical.v0(), cloth.getFirst().v0());
        assertEquals(vertical.v1(), cloth.getLast().v1());
        assertEquals(0, cloth.getFirst().y0());
    }

    @Test void renderingInterpolatesWithoutAdvancingSimulation() {
        WaveCloth.State state = new WaveCloth.State();
        var settings = WaveCloth.Settings.defaults();
        WaveCloth.step(state, settings, motion(0, 0, 0.2, 0));
        float simulated = state.x[15];
        var previous = CapeGeometry.cloth(state, settings, motion(0, 0, 0.2, 0), null, null, 0, 1, false, false, 0f).getLast();
        var middle = CapeGeometry.cloth(state, settings, motion(0, 0, 0.2, 0), null, null, 0, 1, false, false, 0.5f).getLast();
        var current = CapeGeometry.cloth(state, settings, motion(0, 0, 0.2, 0), null, null, 0, 1, false, false, 1f).getLast();
        assertEquals((previous.z1() + current.z1()) / 2, middle.z1(), 0.00001);
        assertEquals(simulated, state.x[15]);
    }

    @Test void cachedModelPreservesGeometryAndEvictsOldModels() {
        String model = Samples.cloak();
        var expected = CapeGeometry.cubes(dev.quarkos.capes.sim.CapeModelJson.parse(model, 64, 32), 0, 1, 64, 32, false);
        var cached = CapeGeometry.model(model, 0, 1, 64, 32, false);
        assertEquals(expected, cached);
        assertSame(cached, CapeGeometry.model(model, 0, 1, 64, 32, false));
        for (int i = 0; i < 140; i++) CapeGeometry.model(model + " ".repeat(i + 1), 0, 1, 64, 32, false);
        assertNotSame(cached, CapeGeometry.model(model, 0, 1, 64, 32, false));
        assertEquals(expected, CapeGeometry.model(model, 0, 1, 64, 32, false));
    }

    @Test void hotPathsHaveGenerousAllocationBounds() {
        var management = ManagementFactory.getThreadMXBean();
        assumeTrue(management instanceof ThreadMXBean && ((ThreadMXBean) management).isThreadAllocatedMemorySupported());
        ThreadMXBean bean = (ThreadMXBean) management;
        bean.setThreadAllocatedMemoryEnabled(true);
        long id = Thread.currentThread().threadId();
        String json = Samples.cloak();
        var state = new WaveCloth.State();
        var settings = WaveCloth.Settings.defaults();
        var moving = motion(0, 0, 0.2, 0);
        for (int i = 0; i < 5000; i++) {
            sink = CapeGeometry.model(json, 0, 1, 64, 32, false);
            WaveCloth.step(state, settings, moving);
            sink = CapeGeometry.cloth(state, settings, moving, null, null, 0, 1, false, false);
        }
        long start = bean.getThreadAllocatedBytes(id);
        for (int i = 0; i < 10000; i++) sink = CapeGeometry.model(json, 0, 1, 64, 32, false);
        assertTrue((bean.getThreadAllocatedBytes(id) - start) / 10000 < 256, "model cache must avoid parsing/tessellation per draw");
        start = bean.getThreadAllocatedBytes(id);
        for (int i = 0; i < 10000; i++) WaveCloth.step(state, settings, moving);
        assertTrue((bean.getThreadAllocatedBytes(id) - start) / 10000 < 64, "step must reuse scratch arrays");
        start = bean.getThreadAllocatedBytes(id);
        for (int i = 0; i < 10000; i++) sink = CapeGeometry.cloth(state, settings, moving, null, null, 0, 1, false, false);
        assertTrue((bean.getThreadAllocatedBytes(id) - start) / 10000 < 3000, "cloth must avoid per-vertex arrays");
    }

    @Test void queuedRefreshCannotBuildUpUntilCompleted() {
        RefreshGate gate = new RefreshGate();
        assertTrue(gate.begin());
        for (int tick = 0; tick < 10000; tick++) assertFalse(gate.begin());
        gate.finish();
        assertTrue(gate.begin());
        gate.finish();
    }

    @Test void capturedSettingsAndCapeRoundTripTogether() throws Exception {
        var folder = Files.createTempDirectory("capes-snapshot");
        var settings = new ClientSettings();
        settings.enableLabyMod = true;
        settings.showOthers = false;
        var cape = OwnerCape.sampleSlide();
        var captured = new LocalSnapshot(settings.json(), dev.quarkos.capes.sim.JsonValues.write(cape.meta()), cape.png);
        settings.showOthers = true;
        captured.write(folder.resolve("settings.json"), folder.resolve("cape"));
        assertTrue(Files.readString(folder.resolve("settings.json")).contains("\"showOthers\":false"));
        var loaded = OwnerCape.load(folder.resolve("cape/local.png"), folder.resolve("cape/local.json"));
        assertEquals(4, loaded.frameCount);
        assertArrayEquals(cape.png, loaded.png);
    }

    @Test void flatStyleStopsStripPlaybackAndKeepsTheSelectedTexture() {
        OwnerCape previous = OwnerCape.get();
        try {
            OwnerCape slide = OwnerCape.sampleSlide();
            OwnerCape.set(slide);
            dev.quarkos.capes.client.screen.CapeActions.style("flat", false);
            assertFalse(OwnerCape.get().animateFrames);
            assertSame(slide.png, OwnerCape.get().png);
            assertEquals(4, OwnerCape.get().frameCount);
        } finally { OwnerCape.set(previous); }
    }

    @Test void galleryMetadataCannotAllocateUnboundedFrameArrays() {
        var detail = dev.quarkos.capes.client.net.GalleryPages.detail("{\"api\":1,\"id\":\"test\",\"frameCount\":2147483647}");
        assertEquals(256, detail.frameCount());
        assertEquals(256, detail.frameDelayMs().length);
    }

    @Test void nonTrueCatalogFlagsNeverPlanOrSendRequests() throws Exception {
        assumeTrue(!"true".equals(System.getenv(LabyCatalog.FLAG)));
        String previous = System.getProperty(LabyCatalog.FLAG);
        try {
            for (String value : new String[] {"", "false", "TRUE", "1", "yes", " true", "true "}) {
                System.setProperty(LabyCatalog.FLAG, value);
                AtomicInteger requests = new AtomicInteger();
                assertFalse(LabyCatalog.enabled());
                assertEquals("", LabyCatalog.pageUrl("cape", 36, 0));
                assertEquals("", LabyCatalog.pageUrl("cloak", 36, 0));
                assertTrue(LabyCatalog.page(true, "cape", 36, 0, url -> { requests.incrementAndGet(); return null; }).isEmpty());
                assertEquals(0, requests.get());
            }
        } finally {
            if (previous == null) System.clearProperty(LabyCatalog.FLAG); else System.setProperty(LabyCatalog.FLAG, previous);
        }
    }
}
