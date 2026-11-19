package dev.quarkos.capes.sim;

import java.util.ArrayList;
import java.util.List;

/** Cape quads in blocks. The cape layer's pose already has the player flip applied. */
public final class CapeGeometry {
    public record Quad(
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float u0, float v0, float u1, float v1,
            float nx, float ny, float nz
    ) {
    }

    private record MeshKey(String json, int frame, int count, int width, int height, boolean vertical) { }
    private static final java.util.Map<MeshKey, List<Quad>> MESHES = new java.util.LinkedHashMap<>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<MeshKey, List<Quad>> entry) { return size() > 128; }
    };

    public static List<Quad> model(String json, int frame, int count, int width, int height, boolean vertical) {
        synchronized (MESHES) {
            MeshKey key = new MeshKey(json, frame, count, width, height, vertical);
            List<Quad> mesh = MESHES.get(key);
            if (mesh != null) return mesh;
            try { mesh = List.copyOf(cubes(CapeModelJson.parse(json, width, height), frame, count, width, height, vertical)); }
            catch (RuntimeException invalid) { mesh = List.of(); }
            MESHES.put(key, mesh);
            return mesh;
        }
    }

    private CapeGeometry() {
    }

    public static List<Quad> flat(int frame, int frameCount, boolean vertical) {
        MeshKey key = new MeshKey(null, frame, frameCount, 64, 32, vertical);
        synchronized (MESHES) {
            List<Quad> found = MESHES.get(key);
            if (found != null) return found;
            int count = Math.max(1, frameCount);
            float u0 = (vertical ? 0f : frame / (float) count) + (1f / 64f) / (vertical ? 1 : count);
            float u1 = u0 + (10f / 64f) / (vertical ? 1 : count);
            float v0 = (vertical ? frame / (float) count : 0f) + (1f / 32f) / (vertical ? count : 1);
            float v1 = v0 + (16f / 32f) / (vertical ? count : 1);
            List<Quad> mesh = List.of(panel(-5f / 16f, 0f, 2f / 16f, 5f / 16f, 1f, 2f / 16f, u0, v0, u1, v1));
            MESHES.put(key, mesh);
            return mesh;
        }
    }

    public static List<Quad> cubes(List<CapeModelJson.Cube> cubes, int frame, int frameCount, int frameWidth, int frameHeight, boolean vertical) {
        List<Quad> out = new ArrayList<>();
        float frameU = vertical ? 0f : frame / (float) Math.max(1, frameCount);
        float frameV = vertical ? frame / (float) Math.max(1, frameCount) : 0f;
        float scaleU = vertical ? 1f : 1f / Math.max(1, frameCount);
        float scaleV = vertical ? 1f / Math.max(1, frameCount) : 1f;
        int width = Math.max(1, frameWidth);
        int height = Math.max(1, frameHeight);
        for (CapeModelJson.Cube cube : cubes) {
            // A 64×32 cape sheet paints the outside on columns 1–10, rows 1–16.
            // Model x=-5 meets column 1, and model y=0 is the shoulder row. u,v shift that sheet.
            float x0 = cube.x() / 16f;
            float y0 = cube.y() / 16f;
            float z0 = 2f / 16f + cube.z() / 16f;
            float x1 = (cube.x() + cube.w()) / 16f;
            float y1 = (cube.y() + cube.h()) / 16f;
            float z1 = z0 + cube.d() / 16f;
            float left = cube.x() + 6f + cube.u();
            float right = left + cube.w();
            float top = cube.y() + 1f + cube.v();
            float bottom = top + cube.h();
            float u0 = map(left + 0.05f, width, frameU, scaleU);
            float u1 = map(right - 0.05f, width, frameU, scaleU);
            float v0 = map(top + 0.05f, height, frameV, scaleV);
            float v1 = map(bottom - 0.05f, height, frameV, scaleV);
            out.add(corners(x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, u0, v0, u1, v1, 0f, 0f, 1f));
            float innerLeft = left + 11f;
            float innerRight = Math.min(right + 11f, cube.u() + 22f);
            float iu0 = map(innerRight - 0.05f, width, frameU, scaleU);
            float iu1 = map(innerLeft + 0.05f, width, frameU, scaleU);
            out.add(corners(x1, y0, z0, x1, y1, z0, x0, y1, z0, x0, y0, z0, iu0, v0, iu1, v1, 0f, 0f, -1f));
            // Sides take one column. The outer silhouette uses the sheet's edge; an inset step uses
            // the column at that edge of the panel, so the emblem stays on the back face.
            float leftPx = cube.x() <= -5 ? cube.u() : left;
            float rightPx = cube.x() + cube.w() >= 5 ? cube.u() + 11f : right - 1f;
            out.add(corners(
                    x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1,
                    map(leftPx + 0.25f, width, frameU, scaleU), v0,
                    map(leftPx + 0.75f, width, frameU, scaleU), v1,
                    -1f, 0f, 0f
            ));
            out.add(corners(
                    x1, y0, z1, x1, y1, z1, x1, y1, z0, x1, y0, z0,
                    map(rightPx + 0.25f, width, frameU, scaleU), v0,
                    map(rightPx + 0.75f, width, frameU, scaleU), v1,
                    1f, 0f, 0f
            ));
            solid(out, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, leftPx, top, width, height, frameU, frameV, scaleU, scaleV, 0f, -1f, 0f);
            solid(out, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, leftPx, bottom - 1f, width, height, frameU, frameV, scaleU, scaleV, 0f, 1f, 0f);
        }
        return out;
    }

    private static void solid(
            List<Quad> out,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float pixelX, float pixelY,
            int width, int height,
            float frameU, float frameV, float scaleU, float scaleV,
            float nx, float ny, float nz
    ) {
        float u0 = map(pixelX + 0.3f, width, frameU, scaleU);
        float u1 = map(pixelX + 0.7f, width, frameU, scaleU);
        float v0 = map(pixelY + 0.3f, height, frameV, scaleV);
        float v1 = map(pixelY + 0.7f, height, frameV, scaleV);
        out.add(corners(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, u0, v0, u1, v1, nx, ny, nz));
    }

    private static float map(float pixel, int size, float frameOrigin, float frameScale) {
        return frameOrigin + (pixel / (float) size) * frameScale;
    }

    public static List<Quad> cloth(
            WaveCloth.State state,
            WaveCloth.Settings settings,
            float[] classicPitch,
            float[] classicRoll,
            int frame,
            int frameCount,
            boolean vertical,
            boolean blocky
    ) {
        return cloth(state, settings, classicPitch, classicRoll, frame, frameCount, vertical, blocky, 1f);
    }

    public static List<Quad> cloth(WaveCloth.State state, WaveCloth.Settings settings,
                                   float[] classicPitch, float[] classicRoll, int frame, int frameCount,
                                   boolean vertical, boolean blocky, float partial) {
        float[] xs = state.renderX, ys = state.renderY, zs = state.renderZ;
        partial = Math.clamp(partial, 0f, 1f);
        if (settings.movement() == WaveCloth.Movement.CLASSIC && classicPitch != null && classicRoll != null) {
            classicPoints(classicPitch, classicRoll, xs, ys, zs);
        } else {
            float scale = (16f / 15f) / 16f;
            // The cape layer pivot is already on the shoulder line.
            for (int i = 0; i < WaveCloth.SEGMENTS; i++) {
                xs[i] = (state.pz[i] + (state.z[i] - state.pz[i]) * partial) * scale;
                ys[i] = -(state.py[i] + (state.y[i] - state.py[i]) * partial) * scale;
                zs[i] = 2f / 16f + (state.px[i] + (state.x[i] - state.px[i]) * partial) * scale;
            }
        }
        int count = Math.max(1, frameCount);
        float u0 = (vertical ? 0f : frame / (float) count) + (1f / 64f) / (vertical ? 1 : count);
        float u1 = u0 + (10f / 64f) / (vertical ? 1 : count);
        float originV = (vertical ? frame / (float) count : 0f) + (1f / 32f) / (vertical ? count : 1);
        float spanV = (16f / 32f) / (vertical ? count : 1);
        List<Quad> out = new ArrayList<>(blocky ? 30 : 15);
        for (int i = 0; i < WaveCloth.SEGMENTS; i++) {
            int next = Math.min(WaveCloth.SEGMENTS - 1, i + 1), prev = Math.max(0, i - 1);
            float tx = xs[next] - xs[prev], ty = ys[next] - ys[prev];
            float length = (float) Math.sqrt(tx * tx + ty * ty);
            float lx = (length < 1.0e-4f ? 1f : ty / length) * (5f / 16f);
            float ly = (length < 1.0e-4f ? 0f : -tx / length) * (5f / 16f);
            state.leftX[i] = xs[i] - lx; state.leftY[i] = ys[i] - ly;
            state.rightX[i] = xs[i] + lx; state.rightY[i] = ys[i] + ly;
        }
        int parts = WaveCloth.SEGMENTS - 1;
        for (int i = 0; i < parts; i++) {
            float v0 = originV + spanV * i / parts, v1 = originV + spanV * (i + 1) / parts;
            float rx = state.rightX[i] - state.leftX[i], ry = state.rightY[i] - state.leftY[i];
            float dx = xs[i + 1] - xs[i], dy = ys[i + 1] - ys[i], dz = zs[i + 1] - zs[i];
            float nx = ry * dz, ny = -rx * dz, nz = rx * dy - ry * dx;
            float normalLength = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (normalLength > 1.0e-6f) { nx /= normalLength; ny /= normalLength; nz /= normalLength; }
            else { nx = ny = 0; nz = 1; }
            out.add(new Quad(
                    state.leftX[i], state.leftY[i], zs[i],
                    state.leftX[i + 1], state.leftY[i + 1], zs[i + 1],
                    state.rightX[i + 1], state.rightY[i + 1], zs[i + 1],
                    state.rightX[i], state.rightY[i], zs[i],
                    u0, v0, u1, v1,
                    nx, ny, nz
            ));
            if (blocky) {
                out.add(new Quad(
                        state.rightX[i], state.rightY[i], zs[i] - 1f / 16f,
                        state.rightX[i + 1], state.rightY[i + 1], zs[i + 1] - 1f / 16f,
                        state.leftX[i + 1], state.leftY[i + 1], zs[i + 1] - 1f / 16f,
                        state.leftX[i], state.leftY[i], zs[i] - 1f / 16f,
                        u0, v0, u1, v1,
                        -nx, -ny, -nz
                ));
            }
        }
        return out;
    }

    private static void classicPoints(float[] pitch, float[] roll, float[] xs, float[] ys, float[] zs) {
        xs[0] = 0f;
        ys[0] = 0f;
        zs[0] = 2f / 16f;
        for (int i = 1; i < WaveCloth.SEGMENTS; i++) {
            float step = (16f / 15f) / 16f;
            float p = pitch[Math.min(i, pitch.length - 1)];
            float r = roll[Math.min(i, roll.length - 1)];
            float plane = step * (float) Math.cos(r);
            xs[i] = xs[i - 1] + (float) Math.sin(r) * step;
            ys[i] = ys[i - 1] + (float) Math.cos(p) * plane;
            zs[i] = zs[i - 1] + (float) Math.sin(p) * plane;
        }
    }

    private static Quad panel(float x0, float y0, float z, float x1, float y1, float ignored, float u0, float v0, float u1, float v1) {
        return corners(x0, y0, z, x0, y1, z, x1, y1, z, x1, y0, z, u0, v0, u1, v1, 0f, 0f, 1f);
    }

    private static Quad corners(
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float u0, float v0, float u1, float v1,
            float nx, float ny, float nz
    ) {
        return new Quad(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, u0, v0, u1, v1, nx, ny, nz);
    }
}
