package dev.quarkos.capes;

import dev.quarkos.capes.client.net.LabyMark;
import dev.quarkos.capes.client.net.LabySource;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabySourceTest {
    private static final UUID ID = UUID.fromString("34e57efa-5783-46c7-a9fc-890296aaba1f");

    @Test
    void closedGateSendsNothing() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        byte[] png = LabySource.worn(false, ID, Files.createTempDirectory("laby"), url -> {
            calls.incrementAndGet();
            return new LabySource.Hit(200, new byte[] {1}, "image/png", 10, 9, 0);
        });
        assertNull(png);
        assertEquals(0, calls.get());
    }

    @Test
    void cloakImageWinsBeforeTheCapeHash() throws Exception {
        LabySource.resetRates();
        Path cache = Files.createTempDirectory("laby");
        byte[] cloak = solid(40, 30, 0xFF2266AA);
        List<String> urls = new ArrayList<>();
        byte[] worn = LabySource.worn(true, ID, cache, url -> {
            urls.add(url);
            if (url.equals(LabySource.EQUIPPED + ID)) {
                return new LabySource.Hit(200, cloak, "image/png", 100, 99, 0);
            }
            throw new AssertionError(url);
        });
        assertNotNull(worn);
        assertEquals(List.of(LabySource.EQUIPPED + ID), urls);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(worn));
        assertEquals(40, image.getWidth());
        assertEquals(30, image.getHeight());
        byte[] again = LabySource.worn(true, ID, cache, url -> {
            throw new AssertionError(url);
        });
        assertEquals(worn.length, again.length);
    }

    @Test
    void activeCloakHashBeatsTheCapeWhenTheEquippedFileIsMissing() throws Exception {
        LabySource.resetRates();
        Path cache = Files.createTempDirectory("laby");
        String cloak = "a0c8e54d8f03aaa9f1ba58190b75ef4a";
        String cape = "5b37a01fde6a3e075f3bc5694c18e667";
        byte[] banner = solid(48, 36, 0xFF112233);
        String json = "{\"CLOAK\":[{\"image_hash\":\"" + cloak + "\",\"active\":true}],"
                + "\"CAPE\":[{\"image_hash\":\"" + cape + "\",\"active\":true}]}";
        List<String> urls = new ArrayList<>();
        byte[] worn = LabySource.worn(true, ID, cache, url -> {
            urls.add(url);
            if (url.equals(LabySource.EQUIPPED + ID)) {
                return new LabySource.Hit(404, new byte[0], "text/plain", 100, 99, 0);
            }
            if (url.equals(LabySource.TEXTURES + ID + "/textures")) {
                return new LabySource.Hit(200, json.getBytes(), "application/json", 10, 9, 0);
            }
            if (url.equals(LabySource.TEXTURE_FILE + cloak + ".png")) {
                return new LabySource.Hit(200, banner, "image/png", 100, 98, 0);
            }
            throw new AssertionError(url);
        });
        assertNotNull(worn);
        assertTrue(urls.contains(LabySource.TEXTURE_FILE + cloak + ".png"));
        assertTrue(urls.stream().noneMatch(url -> url.contains(cape)));
    }

    @Test
    void rateLimitStopsTheTexturesRead() throws Exception {
        LabySource.resetRates();
        Path cache = Files.createTempDirectory("laby");
        AtomicInteger textures = new AtomicInteger();
        long reset = System.currentTimeMillis() + 60_000L;
        LabySource.Get http = url -> {
            if (url.equals(LabySource.EQUIPPED + ID)) {
                return new LabySource.Hit(404, new byte[0], "text/plain", -1, -1, 0);
            }
            if (url.contains("/textures")) {
                textures.incrementAndGet();
                return new LabySource.Hit(429, new byte[0], "application/json", 10, 0, reset);
            }
            throw new AssertionError(url);
        };
        assertNull(LabySource.worn(true, ID, cache, http));
        assertNull(LabySource.worn(true, ID, cache, http));
        assertEquals(1, textures.get());
    }

    @Test
    void vanillaCapeKeepsItsLayout() throws Exception {
        BufferedImage src = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        src.setRGB(2, 2, 0xFFFF0000);
        src.setRGB(40, 2, 0xFF0000FF);
        byte[] shown = LabySource.present(png(src));
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(shown));
        assertEquals(64, out.getWidth());
        assertEquals(32, out.getHeight());
        assertEquals(0xFFFF0000, out.getRGB(2, 2));
        assertEquals(0xFF0000FF, out.getRGB(40, 2));
        BufferedImage strip = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        strip.setRGB(2, 2, 0xFF00FF00);
        strip.setRGB(2, 34, 0xFFFFFF00);
        BufferedImage stacked = ImageIO.read(new ByteArrayInputStream(LabySource.present(png(strip))));
        assertEquals(64, stacked.getWidth());
        assertEquals(64, stacked.getHeight());
        assertEquals(0xFF00FF00, stacked.getRGB(2, 2));
        assertEquals(0xFFFFFF00, stacked.getRGB(2, 34));
    }

    @Test
    void netIsCopiedIntoTheVanillaFrame() throws Exception {
        BufferedImage net = new BufferedImage(22, 17, BufferedImage.TYPE_INT_ARGB);
        net.setRGB(1, 1, 0xFFFF0000);
        net.setRGB(12, 8, 0xFF0000FF);
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(LabySource.present(png(net))));
        assertEquals(64, out.getWidth());
        assertEquals(32, out.getHeight());
        assertEquals(0xFFFF0000, out.getRGB(1, 1));
        assertEquals(0xFF0000FF, out.getRGB(12, 8));
        assertEquals(0, out.getRGB(22, 0));
        assertEquals(0, out.getRGB(0, 17));
    }

    @Test
    void scaledNetKeepsUnitTexelAtPixelK() throws Exception {
        int scale = 16;
        BufferedImage net = new BufferedImage(22 * scale, 17 * scale, BufferedImage.TYPE_INT_ARGB);
        net.setRGB(scale, scale, 0xFFFF8800);
        net.setRGB(12 * scale, 8 * scale, 0xFF0088FF);
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(LabySource.present(png(net))));
        assertEquals(64 * scale, out.getWidth());
        assertEquals(32 * scale, out.getHeight());
        assertEquals(0xFFFF8800, out.getRGB(scale, scale));
        assertEquals(0xFF0088FF, out.getRGB(12 * scale, 8 * scale));
        assertEquals(0, out.getRGB(22 * scale, 0));
        BufferedImage strip = new BufferedImage(44, 68, BufferedImage.TYPE_INT_ARGB);
        strip.setRGB(2, 2, 0xFF112233);
        strip.setRGB(2, 36, 0xFF332211);
        BufferedImage stacked = ImageIO.read(new ByteArrayInputStream(LabySource.present(png(strip))));
        assertEquals(128, stacked.getWidth());
        assertEquals(128, stacked.getHeight());
        assertEquals(0xFF112233, stacked.getRGB(2, 2));
        assertEquals(0xFF332211, stacked.getRGB(2, 66));
    }

    @Test
    void placeholderImageIsDropped() throws Exception {
        long most = 0xff305f81ff305f90L;
        long least = 0xff305f90ff305f90L;
        int[] pixels = new int[] {
                (int) (most >>> 32),
                (int) most,
                (int) (least >>> 32),
                (int) least
        };
        assertTrue(LabyMark.placeholder(pixels));
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, pixels[0]);
        image.setRGB(1, 0, pixels[1]);
        image.setRGB(0, 1, pixels[2]);
        image.setRGB(1, 1, pixels[3]);
        assertNull(LabySource.present(png(image)));
        BufferedImage cloak = new BufferedImage(48, 36, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < cloak.getHeight(); y++) {
            for (int x = 0; x < cloak.getWidth(); x++) {
                cloak.setRGB(x, y, 0xFF000000 | ((x * 13 + y * 29) << 8));
            }
        }
        cloak.setRGB(0, 0, pixels[0]);
        cloak.setRGB(1, 0, pixels[1]);
        cloak.setRGB(0, 1, pixels[2]);
        cloak.setRGB(1, 1, pixels[3]);
        assertNotNull(LabySource.present(png(cloak)));
    }

    @Test
    void activeHashIgnoresHistory() {
        String json = "{\"CAPE\":[{\"image_hash\":\"aaaaaaaaaaaaaaaa\",\"active\":false},"
                + "{\"image_hash\":\"bbbbbbbbbbbbbbbb\",\"active\":true}]}";
        assertEquals("bbbbbbbbbbbbbbbb", LabySource.activeHash(json, "CAPE"));
        assertNull(LabySource.activeHash(json, "CLOAK"));
    }

    private static byte[] solid(int width, int height, int argb) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return png(image);
    }

    private static byte[] png(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
