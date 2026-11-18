package dev.quarkos.capes;

import dev.quarkos.capes.client.harness.HarnessAuth;
import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.Samples;
import dev.quarkos.capes.client.net.CapeCalls;
import dev.quarkos.capes.client.net.CapeHttp;
import dev.quarkos.capes.sim.CapeGeometry;
import dev.quarkos.capes.client.screen.GalleryRows;
import dev.quarkos.capes.client.net.GalleryPages;
import dev.quarkos.capes.client.net.LabyMark;
import dev.quarkos.capes.sim.CapeModelJson;
import dev.quarkos.capes.sim.StripFrames;
import dev.quarkos.capes.sim.WaveCloth;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapeCoreTest {
    private static final UUID ID = UUID.fromString("12345678-1234-1234-1234-123456789abc");

    @Test
    void stripUsesTheDelayFormula() {
        assertEquals(2, StripFrames.index(250, new int[] {100, 100, 100, 100}));
        assertEquals(1, StripFrames.index(120, new int[] {100, 50, 100}));
        assertEquals(0, StripFrames.index(5000, new int[] {100}));
    }

    @Test
    void waveIsDeterministicAndSeparatesMovement() {
        WaveCloth.Settings defaults = WaveCloth.Settings.defaults();
        WaveCloth.Motion motion = new WaveCloth.Motion(0.4f, 0.8f, 0.5f, false, false, 0f, false, false, 1000L, 0f, -1f, 0f);
        WaveCloth.State a = new WaveCloth.State();
        WaveCloth.State b = new WaveCloth.State();
        WaveCloth.run(a, defaults, motion, 20);
        WaveCloth.run(b, defaults, motion, 20);
        assertEquals(a.y[15], b.y[15]);
        assertEquals(a.z[15], b.z[15]);

        WaveCloth.State light = new WaveCloth.State();
        WaveCloth.State heavy = new WaveCloth.State();
        WaveCloth.run(light, new WaveCloth.Settings(WaveCloth.Movement.SIMULATION_V2, WaveCloth.Mesh.SMOOTH, WaveCloth.Wind.NONE, 5, 6, 2, false), motion, 12);
        WaveCloth.run(heavy, new WaveCloth.Settings(WaveCloth.Movement.SIMULATION_V2, WaveCloth.Mesh.SMOOTH, WaveCloth.Wind.NONE, 32, 6, 2, false), motion, 12);
        assertTrue(heavy.y[15] < light.y[15]);

        WaveCloth.State flat = new WaveCloth.State();
        WaveCloth.State sway = new WaveCloth.State();
        WaveCloth.run(flat, new WaveCloth.Settings(WaveCloth.Movement.SIMULATION, WaveCloth.Mesh.SMOOTH, WaveCloth.Wind.NONE, 25, 6, 2, false), motion, 16);
        WaveCloth.run(sway, defaults, motion, 16);
        assertEquals(0f, flat.z[15]);
        assertNotEquals(0f, sway.z[15]);

        WaveCloth.State calm = new WaveCloth.State();
        WaveCloth.State windy = new WaveCloth.State();
        WaveCloth.Motion waves = new WaveCloth.Motion(0.4f, 0.8f, 0.5f, false, false, 0f, false, false, 1000L, 0f, -1f, 0f);
        WaveCloth.run(calm, defaults, waves, 8);
        WaveCloth.run(windy, new WaveCloth.Settings(WaveCloth.Movement.SIMULATION_V2, WaveCloth.Mesh.SMOOTH, WaveCloth.Wind.WAVES, 25, 6, 2, false), waves, 8);
        assertEquals(calm.x[15], windy.x[15]);
        assertEquals(0f, WaveCloth.windBend(defaults, waves, 3));
        assertNotEquals(0f, WaveCloth.windBend(new WaveCloth.Settings(WaveCloth.Movement.SIMULATION_V2, WaveCloth.Mesh.SMOOTH, WaveCloth.Wind.WAVES, 25, 6, 2, false), waves, 3));
    }

    @Test
    void clothKeepsCapeLengthAndLiftsWhileMoving() {
        WaveCloth.Settings defaults = WaveCloth.Settings.defaults();
        WaveCloth.State stand = new WaveCloth.State();
        WaveCloth.run(stand, defaults, WaveCloth.Motion.still(), 40);
        assertCapeLength(stand);
        assertTrue(Math.abs(stand.x[15]) < 0.35f);
        assertTrue(stand.y[15] < -14f);

        WaveCloth.Motion walk = new WaveCloth.Motion(0.12f, 0f, 0f, false, false, 0f, false, false, 800L, 0f, -1f, 0f);
        WaveCloth.Motion sprint = new WaveCloth.Motion(0.28f, 0f, 0f, false, false, 0f, false, false, 800L, 0f, -1f, 0f);
        WaveCloth.State walking = new WaveCloth.State();
        WaveCloth.State sprinting = new WaveCloth.State();
        WaveCloth.run(walking, defaults, walk, 40);
        WaveCloth.run(sprinting, defaults, sprint, 40);
        assertCapeLength(walking);
        assertCapeLength(sprinting);
        float walkAngle = (float) Math.toDegrees(Math.atan2(walking.x[15], -walking.y[15]));
        float sprintAngle = (float) Math.toDegrees(Math.atan2(sprinting.x[15], -sprinting.y[15]));
        assertTrue(walkAngle >= 45f && walkAngle <= 80f, "walk " + walkAngle);
        assertTrue(sprintAngle >= 45f && sprintAngle <= 80f, "sprint " + sprintAngle);
        assertTrue(sprintAngle > walkAngle + 4f, "walk " + walkAngle + " sprint " + sprintAngle);
        // One block behind is the whole cape. The old sim ran many blocks past that.
        assertTrue(sprinting.x[15] <= 15.02f, "tipx " + sprinting.x[15]);
        // A ripple means the middle is not on the straight line from the shoulder to the hem.
        float mid = 8f / 15f;
        float line = sprinting.x[15] * mid;
        assertTrue(Math.abs(sprinting.x[8] - line) > 0.4f, "ripple " + Math.abs(sprinting.x[8] - line));
    }

    private static void assertCapeLength(WaveCloth.State state) {
        double length = 0.0;
        for (int i = 0; i < WaveCloth.SEGMENTS - 1; i++) {
            double dx = state.x[i + 1] - state.x[i];
            double dy = state.y[i + 1] - state.y[i];
            double dz = state.z[i + 1] - state.z[i];
            double link = Math.sqrt(dx * dx + dy * dy + dz * dz);
            assertTrue(link <= 1.001, "link " + i + " is " + link);
            length += link;
        }
        assertTrue(length <= WaveCloth.SEGMENTS - 1 + 0.02);
    }

    @Test
    void cloakSampleIsAFittedCape() {
        var cubes = CapeModelJson.parse(Samples.cloak(), 64, 32);
        assertEquals(List.of(
                new CapeModelJson.Cube(-6, 0, 0, 12, 3, 3, 0, 0),
                new CapeModelJson.Cube(-5, 2, 0, 10, 10, 2, 0, 0),
                new CapeModelJson.Cube(-4, 5, 1, 8, 4, 2, 0, 0),
                new CapeModelJson.Cube(-3, 8, 2, 6, 3, 2, 0, 0),
                new CapeModelJson.Cube(-4, 11, 1, 8, 5, 2, 0, 0)
        ), cubes);
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxReach = 0;
        int widest = 0;
        for (CapeModelJson.Cube cube : cubes) {
            assertEquals(0, cube.x() * 2 + cube.w());
            assertTrue(cube.d() >= 2);
            minX = Math.min(minX, cube.x());
            maxX = Math.max(maxX, cube.x() + cube.w());
            maxReach = Math.max(maxReach, cube.z() + cube.d());
            widest = Math.max(widest, cube.w());
        }
        CapeModelJson.Cube mantle = cubes.get(0);
        CapeModelJson.Cube panel = cubes.get(1);
        assertEquals(widest, mantle.w());
        assertTrue(mantle.y() <= panel.y());
        assertTrue(panel.h() >= 10 && panel.d() >= 2);
        assertTrue(cubes.get(3).z() > cubes.get(2).z());
        assertTrue(cubes.get(4).w() < panel.w());
        assertTrue(minX >= -6 && maxX <= 6);
        assertTrue(maxReach <= 4);
        var quads = CapeGeometry.cubes(cubes, 0, 1, 64, 32, false);
        assertEquals(cubes.size() * 6, quads.size());
        assertTrue(quads.stream().anyMatch(quad -> quad.ny() < 0f));
        assertTrue(quads.stream().anyMatch(quad -> quad.ny() > 0f));
        assertTrue(quads.stream().anyMatch(quad -> quad.nx() != 0f));
        assertTrue(quads.stream().anyMatch(quad -> quad.nz() > 0f));
        CapeGeometry.Quad emblem = quads.get(2 * 6);
        assertTrue(emblem.nz() > 0f);
        assertTrue(emblem.u0() * 64f < 5f && emblem.u1() * 64f > 7f, "emblem u " + emblem.u0() + " " + emblem.u1());
        assertTrue(emblem.v0() * 32f < 8f && emblem.v1() * 32f > 9f, "emblem v " + emblem.v0() + " " + emblem.v1());
        CapeGeometry.Quad side = quads.get(3);
        assertTrue(side.nx() > 0f);
        float sideLo = Math.min(side.u0(), side.u1()) * 64f;
        float sideHi = Math.max(side.u0(), side.u1()) * 64f;
        assertTrue(sideHi - sideLo < 1f, "side span " + (sideHi - sideLo));
        assertTrue(sideLo > 10f, "side column " + sideLo);
        CapeGeometry.Quad hem = quads.get(4 * 6);
        assertTrue(hem.nz() > 0f);
        assertTrue(hem.v0() * 32f > 11.5f && hem.v1() * 32f < 17.2f, "hem v " + hem.v0() + " " + hem.v1());
    }

    @Test
    void cloakSampleTextureIsTheBundledSheet() throws Exception {
        byte[] png = Samples.cloakPng();
        assertEquals(64, ((png[16] & 0xff) << 24) | ((png[17] & 0xff) << 16) | ((png[18] & 0xff) << 8) | (png[19] & 0xff));
        assertEquals(32, ((png[20] & 0xff) << 24) | ((png[21] & 0xff) << 16) | ((png[22] & 0xff) << 8) | (png[23] & 0xff));
        try (InputStream in = Samples.class.getResourceAsStream("/assets/quarkos_capes/textures/cape/cloak.png")) {
            assertTrue(in != null);
            assertArrayEquals(png, in.readAllBytes());
        }
    }

    @Test
    void modelRejectsAnExtraFieldAndAnOversizedCube() {
        assertThrows(CapeModelJson.Invalid.class, () -> CapeModelJson.parse("{\"cubes\":[],\"note\":1}", 64, 32));
        assertThrows(CapeModelJson.Invalid.class, () -> CapeModelJson.parse("{\"cubes\":[{\"x\":0,\"y\":0,\"z\":0,\"w\":33,\"h\":1,\"d\":1,\"u\":0,\"v\":0}]}", 64, 32));
    }

    @Test
    void localOnlyBuildsNoRequest() {
        for (CapeCalls.Kind kind : CapeCalls.Kind.values()) {
            assertTrue(CapeCalls.plan(true, kind, CapeCalls.DEFAULT_BASE, "Steve", ID, "optifine", "gallery-id", "cape", "wave", 8, "cursor").isEmpty());
        }
        assertNull(CapeCalls.request(true, "GET", URI.create("http://127.0.0.1/v1/gallery")));
        assertTrue(CapeCalls.equip().isEmpty());
        String agent = CapeCalls.USER_AGENT.toLowerCase();
        assertFalse(agent.contains("mozilla"));
        assertFalse(agent.contains("optifine"));
    }

    @Test
    void galleryAndProvidersHaveUrlsWhenOnline() {
        var list = CapeCalls.plan(false, CapeCalls.Kind.GALLERY_LIST, CapeCalls.DEFAULT_BASE, "Steve", ID, null, null, "cape", "frames", 8, "abc");
        assertEquals("GET", list.get(0).method());
        assertTrue(list.get(0).url().contains("/v1/gallery?q="));
        assertTrue(list.get(0).url().contains("style=frames"));
        var report = CapeCalls.plan(false, CapeCalls.Kind.GALLERY_REPORT, CapeCalls.DEFAULT_BASE, "Steve", ID, null, "gallery-id", "", "", 8, "");
        assertEquals("POST", report.get(0).method());
        assertTrue(report.get(0).url().endsWith("/v1/gallery/gallery-id/report"));
        var put = CapeCalls.plan(false, CapeCalls.Kind.GALLERY_PUT, CapeCalls.DEFAULT_BASE, "Steve", ID, null, ID.toString(), "", "", 8, "");
        assertEquals("PUT", put.get(0).method());
        var provider = CapeCalls.plan(false, CapeCalls.Kind.PROVIDER, CapeCalls.DEFAULT_BASE, "Steve", ID, "optifine");
        assertTrue(provider.get(0).url().startsWith("http://s.optifine.net/capes/"));
        assertTrue(CapeCalls.plan(false, CapeCalls.Kind.MOJANG_JOIN, CapeCalls.DEFAULT_BASE, "Steve", ID, null).get(0).url().startsWith("https://sessionserver.mojang.com/"));
    }

    @Test
    void productionPathDoesNotSendADevToken() {
        assertFalse(CapeHttp.productionDevAuth());
        assertFalse(CapeHttp.mojangSession(null));
        assertFalse(CapeHttp.mojangSession(""));
        assertFalse(CapeHttp.mojangSession("0"));
        assertFalse(HarnessAuth.enabled(null, "0123456789abcdef"));
        assertFalse(HarnessAuth.enabled("true", "short"));
        assertFalse(HarnessAuth.enabled("TRUE", "0123456789abcdef"));
        assertFalse(HarnessAuth.enabled("yes", "0123456789abcdef"));
        assertTrue(HarnessAuth.enabled("true", "0123456789abcdef"));
        assertEquals("3befb6c6-35ed-3de0-baf2-708342de5c88", HarnessAuth.offlineUuid("CapeA").toString());
        assertEquals("45637a7f-0a9a-3524-bc32-28ee50dc4b8c", HarnessAuth.offlineUuid("CapeB").toString());
        String dev = HarnessAuth.bearer(HarnessAuth.offlineUuid("CapeA"), "0123456789abcdef");
        assertTrue(dev.startsWith("dev1.3befb6c6-35ed-3de0-baf2-708342de5c88."));
        assertEquals(64, dev.substring(dev.lastIndexOf('.') + 1).length());
        assertFalse(CapeHttp.mojangSession(dev));
        assertNull(CapeCalls.sessionHeader(dev));
        assertNull(CapeCalls.sessionHeader(null));
        assertEquals("Bearer session-token", CapeCalls.sessionHeader("session-token"));
        var complete = CapeCalls.request(false, "POST", URI.create("http://127.0.0.1:8787/v1/auth/complete"));
        assertFalse(complete.headers().map().containsKey("Authorization"));
        assertTrue(CapeHttp.mojangSession("real-access-token"));
    }

    @Test
    void galleryPageKeepsEveryStyle() {
        GalleryPages.Page page = GalleryPages.page("""
                {"api":1,"items":[
                  {"id":"a","title":"Flat","style":"flat","animateFrames":false,"frameCount":1},
                  {"id":"b","title":"Slide","style":"frames","animateFrames":true,"frameCount":4},
                  {"id":"c","title":"Cloak","style":"model","animateFrames":false,"frameCount":1}
                ],"nextCursor":null}
                """);
        assertEquals(3, page.entries().size());
        assertNull(page.nextCursor());
        GalleryPages.Page older = GalleryPages.page("""
                {"api":1,"entries":[{"id":"a","title":"Flat","style":"flat","animateFrames":false,"frameCount":1}],"nextCursor":"n"}
                """);
        assertEquals(1, older.entries().size());
        assertEquals("frames", page.entries().get(1).style());
        assertEquals("model", page.entries().get(2).style());
        assertEquals("n", older.nextCursor());
        assertEquals("", page.entries().get(0).author());
        GalleryPages.Page owned = GalleryPages.page("""
                {"api":1,"items":[{"id":"bands","title":"Bands","style":"flat","animateFrames":false,"frameCount":1,"owner":"3befb6c6-35ed-3de0-baf2-708342de5c88"}]}
                """);
        assertEquals("3befb6c6-35ed-3de0-baf2-708342de5c88", owned.entries().get(0).author());
    }

    @Test
    void gallerySearchListsOnlyMatches() {
        assertEquals("0 shared capes", GalleryRows.sharedCapes(0));
        assertEquals("1 shared cape", GalleryRows.sharedCapes(1));
        assertEquals("2 shared capes", GalleryRows.sharedCapes(2));
        assertEquals("Search for \"band\" returned 1 shared cape.", GalleryRows.searchNote("band", 1));
        var locals = java.util.List.of(
                new GalleryRows.Local("Bands", "CapeB"),
                new GalleryRows.Local("Slide", "CapeB"),
                new GalleryRows.Local("Cloak", "CapeB"));
        var shared = java.util.List.of(new GalleryRows.Shared("bands", "Bands", "CapeA"));
        var matches = GalleryRows.visible("band", locals, shared);
        assertEquals(1, matches.size());
        assertEquals("Bands", matches.get(0).name());
        assertEquals("CapeA", matches.get(0).author());
        assertEquals("shared", matches.get(0).tag());
        assertEquals("bands", matches.get(0).id());
        var slide = GalleryRows.visible("slide", locals, shared);
        assertEquals(1, slide.size());
        assertEquals("local", slide.get(0).tag());
        var idle = GalleryRows.visible("", locals, shared);
        assertEquals(3, idle.size());
        assertEquals("local", idle.get(0).tag());
        assertEquals("local", idle.get(2).tag());
    }

    @Test
    void settingsStaySetAfterReload() throws Exception {
        Path dir = Files.createTempDirectory("quarkos-capes");
        Path file = dir.resolve("quarkos_capes.json");
        ClientSettings settings = ClientSettings.get();
        settings.localOnly = false;
        settings.showOthers = false;
        settings.enableOptifine = true;
        settings.enableElytraTexture = false;
        settings.baseUrl = "http://127.0.0.1:8787";
        settings.equippedGalleryId = null;
        settings.cloth = new WaveCloth.Settings(WaveCloth.Movement.CLASSIC, WaveCloth.Mesh.BLOCKY, WaveCloth.Wind.WAVES, 12, 8, 4, true);
        settings.save(file);
        ClientSettings.load(file);
        ClientSettings loaded = ClientSettings.get();
        assertFalse(loaded.localOnly);
        assertFalse(loaded.showOthers);
        assertTrue(loaded.enableOptifine);
        assertFalse(loaded.enableElytraTexture);
        assertEquals("http://127.0.0.1:8787", loaded.baseUrl);
        assertEquals(WaveCloth.Movement.CLASSIC, loaded.cloth.movement());
        assertEquals(WaveCloth.Mesh.BLOCKY, loaded.cloth.mesh());
        assertEquals(WaveCloth.Wind.WAVES, loaded.cloth.wind());
        assertEquals(12, loaded.cloth.gravity());
        assertEquals(8, loaded.cloth.heightMultiplier());
        assertEquals(4, loaded.cloth.straveMultiplier());
        assertTrue(loaded.cloth.computeGravityVector());
        ClientSettings.load(dir.resolve("missing.json"));
    }

    @Test
    void labyPlaceholderMatchesTheKnownPixels() {
        int[] pixels = new int[] {0xFF305F81, 0xFF305F90, 0xFF305F90, 0xFF305F90};
        assertTrue(LabyMark.placeholder(pixels));
        assertFalse(LabyMark.placeholder(new int[] {1, 2, 3, 4}));
    }
}
