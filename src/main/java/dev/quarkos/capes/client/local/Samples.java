package dev.quarkos.capes.client.local;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public final class Samples {
    private Samples() {
    }

    public static byte[] bands() {
        return read("/assets/quarkos_capes/samples/bands.png");
    }

    public static byte[] slide() {
        return read("/assets/quarkos_capes/samples/slide.png");
    }

    public static String cloak() {
        return new String(read("/assets/quarkos_capes/samples/cloak.json"), java.nio.charset.StandardCharsets.UTF_8);
    }

    public static byte[] cloakPng() {
        return read("/assets/quarkos_capes/samples/cloak.png");
    }

    private static byte[] read(String path) {
        try (InputStream in = Samples.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
