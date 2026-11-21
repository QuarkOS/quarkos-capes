package dev.quarkos.capes;

import dev.quarkos.capes.client.net.CatalogClient;
import dev.quarkos.capes.client.net.LabyGallery;
import dev.quarkos.capes.client.screen.ui.CapeText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LabyGalleryTest {
    @Test
    void pageUrlListsOnlyCloaksOnePageAtATime() {
        assertEquals("https://laby.net/api/v3/search/textures/cloak?order=most_used&size=20&offset=0",
                LabyGallery.pageUrl("", "", "popular", "", null));
        assertEquals("https://laby.net/api/v3/search/textures/cloak?order=latest&size=20&offset=40&input=red%20fire",
                LabyGallery.pageUrl("cloak", " red fire ", "newest", "", "40"));
        // laby.net's cape list is official Minecraft capes: never requested.
        assertEquals("", LabyGallery.pageUrl("cape", "", "popular", "", null));
        assertEquals("", LabyGallery.pageUrl("", "", "popular", "frames", null));
        assertEquals("", LabyGallery.pageUrl("", "", "popular", "", "x"));
        assertFalse(LabyGallery.pageUrl("", "", "popular", "", null).contains("/textures/cape"));
    }

    @Test
    void parseMapsCloaksHidesMojangCopiesAndPages() {
        StringBuilder json = new StringBuilder("{\"results\":[");
        json.append("{\"image_hash\":\"a0c8e54d8f03aaa9f1ba58190b75ef4a\",\"use_count\":5,\"tags\":\"Labymod Blue Default\",\"top_tags\":[\"Labymod\",\"Blue\",\"Default\"]},");
        json.append("{\"image_hash\":\"5064684a769e82814e8f62091335b2d8\",\"use_count\":4,\"tags\":\"Black Minecon 2016\",\"top_tags\":[\"Minecon\"]},");
        json.append("{\"image_hash\":\"not-hex!\",\"tags\":\"x\"}");
        for (int i = 3; i < 20; i++) json.append(",{\"image_hash\":\"").append(String.format("%032x", i)).append("\",\"tags\":\"T").append(i).append("\"}");
        json.append("]}");
        CatalogClient.Page page = LabyGallery.parse(json.toString(), "20", "popular");
        assertEquals("40", page.nextCursor());
        assertEquals("available", page.sources().get("labymod"));
        CatalogClient.Item first = page.items().get(0);
        assertEquals("labymod", first.source());
        assertEquals("cloak:a0c8e54d8f03aaa9f1ba58190b75ef4a", first.id());
        assertEquals("Labymod Blue Default", first.title());
        assertEquals("https://texture.laby.net/a0c8e54d8f03aaa9f1ba58190b75ef4a.png", first.textureUrl());
        assertEquals("https://laby.net/cloaks/a0c8e54d8f03aaa9f1ba58190b75ef4a", LabyGallery.creditUrl(first));
        assertTrue(page.items().stream().noneMatch(item -> item.title().toLowerCase().contains("minecon")));
        assertEquals(18, page.items().size());
        assertNull(LabyGallery.parse("{\"results\":[]}", "0", "popular").nextCursor());
        assertTrue(LabyGallery.parse("not json", "0", "popular").items().isEmpty());
    }

    @Test
    void legalTextWrapsByWidth() {
        List<String> lines = CapeText.wrap("aa bb cc\n\ndddddddddd", 5, String::length);
        assertEquals(List.of("aa bb", "cc", "", "ddddd", "ddddd"), lines);
    }
}
