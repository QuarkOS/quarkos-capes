package dev.quarkos.capes.client.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Immutable metadata captured on the client thread; PNG bytes are replaced, never edited. */
public record LocalSnapshot(String settings, String cape, byte[] png) {
    public void write(Path config, Path folder) throws IOException {
        Files.createDirectories(folder);
        replace(config, settings.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        replace(folder.resolve("local.json"), cape.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        replace(folder.resolve("local.png"), png);
    }

    private static void replace(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        Files.write(temporary, bytes);
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
