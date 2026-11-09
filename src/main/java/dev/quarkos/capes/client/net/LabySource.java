package dev.quarkos.capes.client.net;

import dev.quarkos.capes.sim.JsonValues;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Other players' LabyMod capes and cloaks, from the public GETs laby.net documents
 * and the ones its own pages call. Nothing is bundled. A closed gate sends no request.
 */
public final class LabySource {
    public static final String EQUIPPED = "https://dl.labymod.net/capes/";
    public static final String TEXTURES = "https://laby.net/api/v3/user/";
    public static final String TEXTURE_FILE = "https://texture.laby.net/";
    private static final long MISS_MS = 10 * 60 * 1000L;
    private static final Map<String, Gate> GATES = new ConcurrentHashMap<>();

    private LabySource() {
    }

    public record Hit(int status, byte[] body, String contentType, int limit, int remaining, long resetAt) {
    }

    public interface Get {
        Hit get(String url) throws IOException;
    }

    /** Equipped cosmetic, then the active cloak hash, then the active cape hash. */
    public static byte[] worn(boolean allowed, UUID id, Path cache, Get http) throws IOException {
        if (!allowed || id == null || http == null) {
            return null;
        }
        Path root = cache.resolve("labymod");
        Path miss = root.resolve("miss").resolve(id.toString());
        if (freshMiss(miss)) {
            return null;
        }
        // "worn", not "equipped": older files stored a cropped back panel.
        Path equippedFile = root.resolve("worn").resolve(id + ".png");
        byte[] cached = readPng(equippedFile);
        if (cached != null) {
            return cached;
        }
        byte[] equipped = fetchPng(EQUIPPED + id, "equipped", http);
        if (equipped != null) {
            byte[] shown = present(equipped);
            if (shown != null) {
                writePng(equippedFile, shown);
                return shown;
            }
        }
        String jsonUrl = TEXTURES + id + "/textures";
        Hit doc = fetch(jsonUrl, "textures", http);
        if (doc == null || doc.status / 100 != 2 || doc.body == null) {
            if (doc != null && doc.status == 404) {
                writeMiss(miss);
            }
            return null;
        }
        String text = new String(doc.body, java.nio.charset.StandardCharsets.UTF_8);
        String hash = activeHash(text, "CLOAK");
        if (hash == null) {
            hash = activeHash(text, "CAPE");
        }
        if (hash == null) {
            writeMiss(miss);
            return null;
        }
        byte[] texture = texture(hash, root, http);
        if (texture == null) {
            writeMiss(miss);
            return null;
        }
        writePng(equippedFile, texture);
        return texture;
    }

    public static byte[] texture(String hash, Path labyRoot, Get http) throws IOException {
        if (!hashOk(hash) || http == null) {
            return null;
        }
        Path file = labyRoot.resolve("atlas").resolve(hash.toLowerCase() + ".png");
        byte[] cached = readPng(file);
        if (cached != null) {
            return cached;
        }
        byte[] raw = fetchPng(TEXTURE_FILE + hash + ".png", "texture", http);
        if (raw == null) {
            return null;
        }
        byte[] shown = present(raw);
        if (shown == null) {
            return null;
        }
        writePng(file, shown);
        return shown;
    }

    public static String activeHash(String json, String type) {
        if (json == null || type == null) {
            return null;
        }
        try {
            Map<String, Object> root = JsonValues.object(JsonValues.parse(json));
            if (!(root.get(type) instanceof List<?> entries)) {
                return null;
            }
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                if (!Boolean.TRUE.equals(map.get("active"))) {
                    continue;
                }
                if (map.get("image_hash") instanceof String hash && hashOk(hash)) {
                    return hash;
                }
            }
        } catch (JsonValues.Bad ignored) {
            return null;
        }
        return null;
    }

    /**
     * Lays the bytes out for the vanilla cape box, which samples a 64×32 atlas
     * (or the same ratio at a larger scale) by fractions of the texture.
     * A Minecraft cape is already that atlas and is returned unchanged.
     * A 22×17 net, including an HD scale or a vertical strip of nets, is copied
     * into the top-left of each frame so the outside, the inside, and the edges land
     * on the texels the box already uses.
     */
    public static byte[] present(byte[] png) {
        if (png == null || png.length < 8 || !isPng(png)) {
            return null;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null) {
                return null;
            }
            if (placeholder(image)) {
                return null;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            if (vanillaAtlas(width, height)) {
                return png;
            }
            BufferedImage atlas = netAtlas(image);
            if (atlas == null) {
                return png;
            }
            return write(atlas);
        } catch (IOException e) {
            return null;
        }
    }

    /** Width is a multiple of 64 and every frame is half as tall, which is the vanilla cape ratio. */
    static boolean vanillaAtlas(int width, int height) {
        if (width < 64 || width % 64 != 0) {
            return false;
        }
        int frame = width / 2;
        return frame > 0 && height >= frame && height % frame == 0;
    }

    public static void resetRates() {
        GATES.clear();
    }

    /** One rate-limited GET. A closed gate returns null and does not call {@code http}. */
    public static Hit exchange(String url, String gate, Get http) throws IOException {
        if (url == null || url.isBlank() || gate == null || gate.isBlank() || http == null) {
            return null;
        }
        return fetch(url, gate, http);
    }

    private static byte[] fetchPng(String url, String gate, Get http) throws IOException {
        Hit hit = fetch(url, gate, http);
        if (hit == null || hit.status / 100 != 2 || hit.body == null || !isPng(hit.body)) {
            return null;
        }
        return hit.body;
    }

    private static Hit fetch(String url, String gate, Get http) throws IOException {
        Gate book = GATES.computeIfAbsent(gate, key -> new Gate());
        long now = System.currentTimeMillis();
        if (!book.open(now)) {
            return null;
        }
        Hit hit = http.get(url);
        if (hit == null) {
            return null;
        }
        long resetAt = hit.resetAt;
        int remaining = hit.remaining;
        int limit = hit.limit;
        if (hit.status == 429 && resetAt <= 0) {
            limit = 1;
            remaining = 0;
            resetAt = System.currentTimeMillis() + 60_000L;
        }
        book.note(limit, remaining, resetAt);
        return hit;
    }

    /**
     * A pure net is {@code k*22} by {@code k*17} per frame. Vanilla UVs are fractions of a
     * {@code k*64} by {@code k*32} frame, so the net is copied into that frame's top-left.
     * The texel at unit (1, 1) stays at pixel {@code k}.
     */
    private static BufferedImage netAtlas(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width < 22 || width % 22 != 0) {
            return null;
        }
        int scale = width / 22;
        int netFrame = 17 * scale;
        if (netFrame <= 0 || height < netFrame || height % netFrame != 0) {
            return null;
        }
        int frames = height / netFrame;
        int frameHeight = 32 * scale;
        BufferedImage atlas = new BufferedImage(64 * scale, frameHeight * frames, BufferedImage.TYPE_INT_ARGB);
        for (int frame = 0; frame < frames; frame++) {
            int srcY = frame * netFrame;
            int dstY = frame * frameHeight;
            for (int y = 0; y < netFrame; y++) {
                for (int x = 0; x < width; x++) {
                    atlas.setRGB(x, dstY + y, image.getRGB(x, srcY + y));
                }
            }
        }
        return atlas;
    }

    /**
     * The empty LabyMod cape puts the placeholder UUID in the first four pixels and
     * nowhere else. Real cloaks can carry those same four pixels in the corner, so a
     * varied image is kept.
     */
    private static boolean placeholder(BufferedImage image) {
        if (image.getWidth() < 1 || image.getHeight() < 1 || !markedCorner(image)) {
            return false;
        }
        int distinct = 0;
        int previous = 0;
        int step = Math.max(1, Math.max(image.getWidth(), image.getHeight()) / 24);
        for (int y = 0; y < image.getHeight(); y += step) {
            for (int x = 0; x < image.getWidth(); x += step) {
                int pixel = image.getRGB(x, y);
                if (distinct == 0) {
                    previous = pixel;
                    distinct = 1;
                } else if (pixel != previous) {
                    previous = pixel;
                    distinct++;
                    if (distinct > 4) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean markedCorner(BufferedImage image) {
        int[] argb = new int[4];
        int[] abgr = new int[4];
        int i = 0;
        for (int y = 0; y < image.getHeight() && i < 4; y++) {
            for (int x = 0; x < image.getWidth() && i < 4; x++) {
                int pixel = image.getRGB(x, y);
                argb[i] = pixel;
                int a = (pixel >>> 24) & 255;
                int r = (pixel >>> 16) & 255;
                int g = (pixel >>> 8) & 255;
                int b = pixel & 255;
                abgr[i] = (a << 24) | (b << 16) | (g << 8) | r;
                i++;
            }
        }
        return LabyMark.placeholder(argb) || LabyMark.placeholder(abgr);
    }

    private static byte[] write(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static boolean isPng(byte[] body) {
        return body.length >= 8
                && (body[0] & 0xFF) == 0x89
                && body[1] == 0x50
                && body[2] == 0x4E
                && body[3] == 0x47;
    }

    static boolean hashOk(String hash) {
        if (hash == null || hash.length() < 8 || hash.length() > 64) {
            return false;
        }
        for (int i = 0; i < hash.length(); i++) {
            char c = hash.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static byte[] readPng(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(file);
            return isPng(bytes) ? bytes : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static void writePng(Path file, byte[] png) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".part");
        Files.write(temp, png);
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static boolean freshMiss(Path miss) {
        try {
            if (!Files.isRegularFile(miss)) {
                return false;
            }
            long at = Long.parseLong(Files.readString(miss).trim());
            return System.currentTimeMillis() - at < MISS_MS;
        } catch (Exception e) {
            return false;
        }
    }

    private static void writeMiss(Path miss) throws IOException {
        Files.createDirectories(miss.getParent());
        Files.writeString(miss, Long.toString(System.currentTimeMillis()));
    }

    private static final class Gate {
        private int remaining = Integer.MAX_VALUE;
        private long resetAt;

        boolean open(long now) {
            return remaining > 0 || now >= resetAt;
        }

        void note(int limit, int remaining, long resetAt) {
            if (limit > 0) {
                this.remaining = Math.max(0, remaining);
            }
            if (resetAt > 0) {
                this.resetAt = resetAt;
            }
        }
    }
}
