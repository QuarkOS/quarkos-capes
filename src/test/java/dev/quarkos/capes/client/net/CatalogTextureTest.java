package dev.quarkos.capes.client.net;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class CatalogTextureTest {
    @Test
    void featuredWearParsesAndStacksFramesIntoTheLocalLayout() throws Exception {
        var item = CatalogClient.wearing("""
                {"api":1,"wearing":{"source":"featured","id":"strip","style":"frames",
                "frameCount":2,"frameWidth":64,"frameHeight":32,"frameDelayMs":[120,210],
                "animateFrames":true,"stripLayout":"vertical"}}
                """);
        assertNotNull(item);
        assertArrayEquals(new int[] {120, 210}, item.delays());

        var strip = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        strip.setRGB(5, 7, 0xfffc3737);
        strip.setRGB(5, 39, 0xff008f80);
        var png = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(strip, "png", png));
        byte[] local = CatalogTexture.localPng(item, png.toByteArray());
        var decoded = ImageIO.read(new ByteArrayInputStream(local));
        assertEquals(128, decoded.getWidth());
        assertEquals(32, decoded.getHeight());
        assertEquals(0xfffc3737, decoded.getRGB(5, 7));
        assertEquals(0xff008f80, decoded.getRGB(69, 7));
        assertThrows(CatalogClient.Failure.class, () -> CatalogTexture.localPng(item, local));

        var upload = CatalogClient.wearing("""
                {"api":1,"wearing":{"source":"ours","id":"upload","frameCount":2,"animateFrames":true}}
                """);
        assertArrayEquals(local, CatalogTexture.localPng(upload, local));
    }
}
