package dev.quarkos.capes.jar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Drops {@code module-info.class} from a jar and from every jar nested inside it.
 * A shaded dependency's module descriptor at the root of a Forge jar becomes the mod's
 * module name and collides with other mods.
 */
public final class ModuleInfoStrip {
    private ModuleInfoStrip() {
    }

    public static boolean isModuleInfo(String name) {
        return "module-info.class".equals(name) || name.endsWith("/module-info.class");
    }

    public static byte[] strip(byte[] jar) throws IOException {
        List<Stored> entries = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                byte[] data = read(in);
                if (isModuleInfo(entry.getName())) {
                    continue;
                }
                if (!entry.isDirectory() && entry.getName().endsWith(".jar")) {
                    data = strip(data);
                }
                entries.add(new Stored(entry.getName(), entry.getTime(), entry.isDirectory(), data));
            }
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(jar.length);
        try (ZipOutputStream out = new ZipOutputStream(buffer)) {
            write(out, entries, true);
            write(out, entries, false);
        }
        return buffer.toByteArray();
    }

    public static void stripJar(Path jar) throws IOException {
        byte[] stripped = strip(Files.readAllBytes(jar));
        Path temp = jar.resolveSibling(jar.getFileName() + ".strip");
        Files.write(temp, stripped);
        Files.move(temp, jar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("usage: ModuleInfoStrip <jar>...");
        }
        for (String arg : args) {
            Path jar = Path.of(arg);
            stripJar(jar);
            System.out.println("stripped module-info " + jar);
        }
    }

    private static void write(ZipOutputStream out, List<Stored> entries, boolean manifest) throws IOException {
        for (Stored stored : entries) {
            boolean isManifest = "META-INF/MANIFEST.MF".equals(stored.name);
            if (isManifest != manifest) {
                continue;
            }
            ZipEntry copy = new ZipEntry(stored.name);
            copy.setTime(stored.time);
            out.putNextEntry(copy);
            if (!stored.directory) {
                out.write(stored.data);
            }
            out.closeEntry();
        }
    }

    private static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        in.transferTo(data);
        return data.toByteArray();
    }

    private record Stored(String name, long time, boolean directory, byte[] data) {
    }
}
