package dev.quarkos.capes;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.net.LabyCatalog;
import dev.quarkos.capes.client.net.LabySource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabyCatalogTest {
    @AfterEach
    void clearFlag() {
        System.clearProperty(LabyCatalog.FLAG);
        LabySource.resetRates();
    }

    @Test
    void catalogIsUnavailableWhenTheFlagIsOff() throws Exception {
        System.clearProperty(LabyCatalog.FLAG);
        assertFalse(LabyCatalog.enabled());
        assertFalse(LabyCatalog.allow(false));
        assertEquals("", LabyCatalog.pageUrl("cape", 36, 0));
        assertEquals("", LabyCatalog.pageUrl("cloak", 36, 0));
        assertTrue(LabyCatalog.read("cape", capePage("aaaabbbb", "Migrator")).isEmpty());
        AtomicInteger calls = new AtomicInteger();
        assertTrue(LabyCatalog.list(true, "cape", 2, url -> {
            calls.incrementAndGet();
            return hit(capePage("aaaabbbb", "Migrator"));
        }).isEmpty());
        assertEquals(0, calls.get());
        System.setProperty(LabyCatalog.FLAG, "TRUE");
        assertFalse(LabyCatalog.enabled());
        assertEquals("", LabyCatalog.pageUrl("cloak", 12, 0));
        ClientSettings settings = new ClientSettings();
        Path file = Files.createTempFile("quarkos-capes", ".json");
        settings.save(file);
        String stored = Files.readString(file);
        assertFalse(stored.contains("CAPES_DEV_CATALOG"));
        assertFalse(stored.contains("catalog"));
        assertTrue(settings.localOnly);
        assertFalse(settings.enableLabyMod);
    }

    @Test
    void localOnlyPlansNoCatalogRequest() throws Exception {
        System.setProperty(LabyCatalog.FLAG, "true");
        assertTrue(LabyCatalog.enabled());
        assertFalse(LabyCatalog.allow(true));
        AtomicInteger calls = new AtomicInteger();
        assertTrue(LabyCatalog.page(LabyCatalog.allow(true), "cloak", 12, 0, url -> {
            calls.incrementAndGet();
            return hit(cloakPage());
        }).isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void openCatalogReadsPagesAndStopsWhenAHashRepeats() throws Exception {
        System.setProperty(LabyCatalog.FLAG, "true");
        LabySource.resetRates();
        assertTrue(LabyCatalog.pageUrl("cape", 99, 0).startsWith(LabyCatalog.SEARCH + "cape?order=most_used&size=36&offset=0"));
        List<LabyCatalog.Item> read = LabyCatalog.read("cape", capePage("5b37a01fde6a3e075f3bc5694c18e667", "Migrator"));
        assertEquals("migrator", read.get(0).slug());
        assertEquals("labymod-blue-default", LabyCatalog.read("cloak", cloakPage()).get(0).slug());
        assertEquals("minecon-2016-black", LabyCatalog.read("cloak", cloakPage()).get(1).slug());
        List<String> urls = new ArrayList<>();
        List<LabyCatalog.Item> items = LabyCatalog.list(true, "cape", 2, url -> {
            urls.add(url);
            if (url.endsWith("offset=0")) {
                return hit(two("aaaabbbb11111111", "Migrator", "bbbbcccc22222222", "Pan"));
            }
            return hit(capePage("aaaabbbb11111111", "Migrator"));
        });
        assertEquals(2, urls.size());
        assertEquals(List.of("migrator", "pan"), items.stream().map(LabyCatalog.Item::slug).toList());
    }

    private static LabySource.Hit hit(String json) {
        return new LabySource.Hit(200, json.getBytes(), "application/json", 300, 299, 0);
    }

    private static String capePage(String hash, String name) {
        return "{\"results\":[{\"image_hash\":\"" + hash + "\",\"use_count\":3,\"tags\":\"Red\",\"top_tags\":[\"Red\"],\"name\":\"" + name + "\"}]}";
    }

    private static String two(String first, String firstName, String second, String secondName) {
        return "{\"results\":["
                + "{\"image_hash\":\"" + first + "\",\"use_count\":3,\"name\":\"" + firstName + "\"},"
                + "{\"image_hash\":\"" + second + "\",\"use_count\":2,\"name\":\"" + secondName + "\"}]}";
    }

    private static String cloakPage() {
        return "{\"results\":["
                + "{\"image_hash\":\"a0c8e54d8f03aaa9f1ba58190b75ef4a\",\"use_count\":4,\"tags\":\"Labymod Blue Default Labywolf Logo\",\"top_tags\":[\"Labymod\",\"Blue\",\"Default\"]},"
                + "{\"image_hash\":\"5064684a769e82814e8f62091335b2d8\",\"use_count\":2,\"tags\":\"Black Minecon 2016\",\"top_tags\":[\"Minecon\",\"Minecon 2016\",\"Black\"]}"
                + "]}";
    }
}
