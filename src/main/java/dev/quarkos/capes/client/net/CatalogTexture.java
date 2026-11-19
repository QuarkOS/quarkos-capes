package dev.quarkos.capes.client.net;

import dev.quarkos.capes.sim.ImageLimits;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

final class CatalogTexture {
    private CatalogTexture() { }

    static byte[] localPng(CatalogClient.Item item, byte[] png) throws IOException {
        if (!item.verticalStrip() || item.frameCount() <= 1) return png;

        int width = item.frameWidth(), height = item.frameHeight(), count = item.frameCount();
        if (!ImageLimits.png(png) || !ImageLimits.dimensions(width * count, height)) {
            throw new CatalogClient.Failure(0, "image_error");
        }
        var header = ByteBuffer.wrap(png);
        if (header.getInt(16) != width || header.getInt(20) != height * count) {
            throw new CatalogClient.Failure(0, "image_error");
        }
        BufferedImage strip = ImageIO.read(new ByteArrayInputStream(png));
        if (strip == null) throw new CatalogClient.Failure(0, "image_error");

        BufferedImage horizontal = new BufferedImage(width * count, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[width * height];
        for (int frame = 0; frame < count; frame++) {
            strip.getRGB(0, frame * height, width, height, pixels, 0, width);
            horizontal.setRGB(frame * width, 0, width, height, pixels, 0, width);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(horizontal, "png", output)) throw new CatalogClient.Failure(0, "image_error");
        return output.toByteArray();
    }
}
