package dev.quarkos.capes.client.local;

import dev.quarkos.capes.sim.CapeModelJson;
import dev.quarkos.capes.sim.JsonValues;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The cape this client is wearing. Gallery equips copy into this record and do not publish. */
public final class OwnerCape {
    private static OwnerCape current = sampleBands();

    public String style = "flat";
    public boolean animateFrames;
    public int frameCount = 1;
    public int frameWidth = 64;
    public int frameHeight = 32;
    public int[] frameDelayMs = new int[] {100};
    public String modelJson;
    public byte[] png = new byte[0];

    public static OwnerCape get() {
        return current;
    }

    public static void set(OwnerCape cape) {
        current = cape;
    }

    public OwnerCape copy() {
        OwnerCape copy = new OwnerCape();
        copy.style = style;
        copy.animateFrames = animateFrames;
        copy.frameCount = frameCount;
        copy.frameWidth = frameWidth;
        copy.frameHeight = frameHeight;
        copy.frameDelayMs = frameDelayMs.clone();
        copy.modelJson = modelJson;
        copy.png = png.clone();
        return copy;
    }

    public static OwnerCape sampleBands() {
        return fromSample("flat", false, Samples.bands(), null);
    }

    public static OwnerCape sampleSlide() {
        OwnerCape cape = fromSample("frames", true, Samples.slide(), null);
        cape.frameCount = 4;
        cape.frameWidth = 64;
        cape.frameHeight = 32;
        cape.frameDelayMs = new int[] {100, 100, 100, 100};
        return cape;
    }

    public static OwnerCape sampleCloak() {
        return fromSample("model", false, Samples.cloakPng(), Samples.cloak());
    }

    public void save(Path pngFile, Path jsonFile) throws IOException {
        Files.createDirectories(pngFile.getParent());
        Files.write(pngFile, png);
        Files.writeString(jsonFile, JsonValues.write(meta()));
    }

    public static OwnerCape load(Path pngFile, Path jsonFile) {
        if (!Files.isRegularFile(pngFile) || !Files.isRegularFile(jsonFile)) {
            return sampleBands();
        }
        try {
            OwnerCape cape = new OwnerCape();
            cape.png = Files.readAllBytes(pngFile);
            Map<String, Object> root = JsonValues.object(JsonValues.parse(Files.readString(jsonFile)));
            cape.style = root.get("style") instanceof String s ? s : "flat";
            cape.animateFrames = root.get("animateFrames") instanceof Boolean b && b;
            cape.frameCount = root.get("frameCount") instanceof Long n ? Math.clamp(n, 1, 256) : 1;
            cape.frameWidth = root.get("frameWidth") instanceof Long n ? n.intValue() : 64;
            cape.frameHeight = root.get("frameHeight") instanceof Long n ? n.intValue() : 32;
            cape.frameDelayMs = delays(root.get("frameDelayMs"), cape.frameCount);
            if (root.get("model") instanceof Map<?, ?>) {
                cape.modelJson = JsonValues.write(root.get("model"));
            }
            if ("model".equals(cape.style) && cape.modelJson != null) {
                CapeModelJson.parse(cape.modelJson, cape.frameWidth, cape.frameHeight);
            }
            return cape;
        } catch (RuntimeException | IOException ignored) {
            return sampleBands();
        }
    }

    public Map<String, Object> meta() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("api", 1);
        root.put("style", style);
        root.put("animateFrames", animateFrames);
        root.put("frameCount", frameCount);
        root.put("frameWidth", frameWidth);
        root.put("frameHeight", frameHeight);
        root.put("frameDelayMs", frameDelayMs);
        if ("model".equals(style) && modelJson != null) {
            root.put("model", JsonValues.parse(modelJson));
        } else {
            root.put("model", null);
        }
        return root;
    }

    private static OwnerCape fromSample(String style, boolean animate, byte[] png, String model) {
        OwnerCape cape = new OwnerCape();
        cape.style = style;
        cape.animateFrames = animate;
        cape.png = png;
        cape.modelJson = model;
        if (png == Samples.slide()) {
            cape.frameCount = 4;
        }
        return cape;
    }

    private static int[] delays(Object value, int count) {
        int n = Math.max(1, count);
        int[] delays = new int[n];
        Arrays.fill(delays, 100);
        if (value instanceof List<?> list) {
            for (int i = 0; i < Math.min(n, list.size()); i++) {
                if (list.get(i) instanceof Long number) {
                    delays[i] = number.intValue();
                }
            }
        }
        return delays;
    }
}
