package dev.quarkos.capes.jar;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleInfoStripTest {
    @Test void shadedModuleInfoIsRemovedFromTheJarAndFromNestedJars() throws Exception {
        byte[] nested = jar(
                entry("module-info.class", "module shaded.dep;"),
                entry("META-INF/versions/9/module-info.class", "module shaded.dep;"),
                entry("org/example/Bar.class", "bar")
        );
        byte[] outer = jar(
                entry("module-info.class", "module shaded.into.root;"),
                entry("META-INF/versions/11/module-info.class", "module multi;"),
                entry("com/example/Foo.class", "foo"),
                entry("com/example/ModuleInfo.class", "keep"),
                entry("META-INF/jars/shaded.jar", nested),
                entry("META-INF/jarjar/ok.jar", nested)
        );
        Path jar = Files.createTempFile("module-info-strip", ".jar");
        Files.write(jar, outer);
        ModuleInfoStrip.stripJar(jar);

        byte[] stripped = Files.readAllBytes(jar);
        assertFalse(containsModuleInfo(stripped), "root jar still has a module descriptor");
        assertEquals("foo", text(stripped, "com/example/Foo.class"));
        assertEquals("keep", text(stripped, "com/example/ModuleInfo.class"));
        byte[] shaded = bytes(stripped, "META-INF/jars/shaded.jar");
        byte[] jarjar = bytes(stripped, "META-INF/jarjar/ok.jar");
        assertFalse(containsModuleInfo(shaded), "shaded jar still has a module descriptor");
        assertFalse(containsModuleInfo(jarjar), "jar-in-jar still has a module descriptor");
        assertEquals("bar", text(shaded, "org/example/Bar.class"));
        assertEquals("bar", text(jarjar, "org/example/Bar.class"));
        assertTrue(text(stripped, "META-INF/MANIFEST.MF").contains("Manifest-Version"));
    }

    private static ZipEntryData entry(String name, String text) {
        return new ZipEntryData(name, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static ZipEntryData entry(String name, byte[] data) {
        return new ZipEntryData(name, data);
    }

    private static byte[] jar(ZipEntryData... entries) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(buffer)) {
            ZipEntry manifest = new ZipEntry("META-INF/MANIFEST.MF");
            out.putNextEntry(manifest);
            out.write("Manifest-Version: 1.0\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
            for (ZipEntryData data : entries) {
                out.putNextEntry(new ZipEntry(data.name));
                out.write(data.bytes);
                out.closeEntry();
            }
        }
        return buffer.toByteArray();
    }

    private static boolean containsModuleInfo(byte[] jar) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new java.io.ByteArrayInputStream(jar))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (ModuleInfoStrip.isModuleInfo(entry.getName())) {
                    return true;
                }
                if (entry.getName().endsWith(".jar")) {
                    if (containsModuleInfo(in.readAllBytes())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String text(byte[] jar, String name) throws Exception {
        return new String(bytes(jar, name), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] bytes(byte[] jar, String name) throws Exception {
        try (ZipInputStream in = new ZipInputStream(new java.io.ByteArrayInputStream(jar))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (name.equals(entry.getName())) {
                    return in.readAllBytes();
                }
            }
        }
        throw new AssertionError("missing " + name);
    }

    private record ZipEntryData(String name, byte[] bytes) {
    }
}
