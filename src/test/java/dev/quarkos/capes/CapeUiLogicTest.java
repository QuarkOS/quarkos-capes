package dev.quarkos.capes;

import dev.quarkos.capes.client.screen.ui.CapeGridLayout;
import dev.quarkos.capes.client.screen.ui.CapeGridLayout.Rect;
import dev.quarkos.capes.client.screen.ui.CapeSelection;
import dev.quarkos.capes.client.screen.ui.CapeStyles;
import dev.quarkos.capes.client.screen.ui.CapeStyles.Chip;
import dev.quarkos.capes.client.screen.ui.CapeText;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CapeUiLogicTest {
    @Test void galleryColumnsAndRowsFollowGuiDimensions() {
        assertGrid(1280, 720, 2, 4, 2, true);
        assertGrid(1280, 720, 3, 2, 1, true);
        assertGrid(1920, 1080, 2, 6, 4, true);
        assertGrid(1920, 1080, 4, 3, 1, true);
        assertGrid(960, 720, 3, 3, 1, false);
    }

    private static void assertGrid(int pixelsWide, int pixelsHigh, int scale, int columns, int rows, boolean preview) {
        Rect frame = CapeGridLayout.frame(Math.ceilDiv(pixelsWide, scale), Math.ceilDiv(pixelsHigh, scale));
        int side = CapeGridLayout.previewWidth(frame);
        assertEquals(preview, side > 0);
        int width = frame.width() - (side == 0 ? 0 : side + 10);
        int top = frame.y() + (frame.width() >= 512 ? 54 : 78);
        int bodyHeight = frame.bottom() - 35 - top;
        Rect area = new Rect(frame.x(), top, width, bodyHeight).inset(6);
        var grid = CapeGridLayout.fit(area);
        assertEquals(columns, grid.columns());
        assertEquals(rows, grid.rows());
        for (int i = 0; i < grid.capacity(); i++) {
            Rect card = grid.card(i);
            assertEquals(CapeGridLayout.CARD_WIDTH, card.width());
            assertEquals(CapeGridLayout.CARD_HEIGHT, card.height());
            assertTrue(area.contains(card.x(), card.y()));
            assertTrue(area.contains(card.right() - 1, card.bottom() - 1));
            if (i % columns != 0) assertEquals(CapeGridLayout.GAP, card.x() - grid.card(i - 1).right());
            if (i >= columns) assertEquals(CapeGridLayout.GAP, card.y() - grid.card(i - columns).bottom());
        }
        assertTrue(area.right() - grid.card(columns - 1).right() < CapeGridLayout.CARD_WIDTH + CapeGridLayout.GAP);
    }

    @Test void pagingClampsAfterFilteringOrResizing() {
        var grid = CapeGridLayout.fit(new Rect(10, 20, 260, 200));
        assertEquals(6, grid.capacity());
        assertEquals(1, grid.pages(0));
        assertEquals(1, grid.pages(6));
        assertEquals(2, grid.pages(7));
        assertEquals(3, grid.pages(13));
        assertEquals(2, grid.clampPage(9, 13));
        assertEquals(0, grid.clampPage(2, 1));
        assertEquals(0, grid.clampPage(-1, 13));
        var tooShort = CapeGridLayout.fit(new Rect(0, 0, CapeGridLayout.CARD_WIDTH, CapeGridLayout.CARD_HEIGHT - 1));
        assertEquals(0, tooShort.capacity());
        assertEquals(1, tooShort.pages(20));
        assertEquals(1, CapeGridLayout.fit(new Rect(0, 0, CapeGridLayout.CARD_WIDTH, CapeGridLayout.CARD_HEIGHT)).capacity());
    }

    @Test void wardrobeHeightFitsOccupiedRows() {
        var grid = CapeGridLayout.fit(new Rect(0, 0, 260, 400));
        int cardHeight = CapeGridLayout.CARD_HEIGHT;
        assertEquals(0, grid.visibleHeight(0));
        assertEquals(cardHeight, grid.visibleHeight(3));
        assertEquals(2 * cardHeight + CapeGridLayout.GAP, grid.visibleHeight(4));
        assertEquals(3 * cardHeight + 2 * CapeGridLayout.GAP, grid.visibleHeight(100));
    }

    @Test void aTallPreviewLeavesRoomForTheModelToTurn() {
        assertEquals(170, CapeGridLayout.previewHeight(100, 400));
        assertEquals(100, CapeGridLayout.previewHeight(100, 100));
    }

    @Test void truncationAccountsForGlyphWidthAndKeepsUnicodeIntact() {
        assertEquals("cape", CapeText.ellipsis("cape", 4, String::length));
        assertEquals("ca…", CapeText.ellipsis("cape", 3, String::length));
        assertEquals("", CapeText.ellipsis("cape", 0, String::length));
        assertEquals("", CapeText.ellipsis("ab", 1, value -> value.contains("…") ? 2 : value.length()));
        assertEquals("A😀…", CapeText.ellipsis("A😀BC", 3, value -> value.codePointCount(0, value.length())));
        assertEquals("A…", CapeText.ellipsis("A  B", 3, String::length));
        assertEquals("A B", CapeText.ellipsis("A\nB", 3, String::length));
    }

    @Test void flatAndFramesShareGeometryAndAnimationIsIndependent() {
        var flat = new CapeStyles("flat", false);
        assertTrue(flat.on(Chip.FLAT));
        assertFalse(flat.on(Chip.ANIMATED));
        var animated = flat.toggle(Chip.ANIMATED);
        assertEquals(new CapeStyles("frames", true), animated);
        assertTrue(animated.on(Chip.FLAT));
        assertTrue(animated.on(Chip.ANIMATED));
        assertEquals(flat, animated.toggle(Chip.ANIMATED));

        var oldFrames = new CapeStyles("frames", false);
        assertTrue(oldFrames.on(Chip.FLAT));
        assertFalse(oldFrames.on(Chip.ANIMATED));
        assertEquals(animated, oldFrames.toggle(Chip.ANIMATED));
    }

    @Test void selectingShapesKeepsPlaybackAndReselectingKeepsShape() {
        var wave = new CapeStyles("frames", true).toggle(Chip.WAVY);
        assertTrue(wave.on(Chip.WAVY));
        assertFalse(wave.on(Chip.FLAT));
        assertTrue(wave.on(Chip.ANIMATED));
        assertEquals(wave, wave.toggle(Chip.WAVY));
        var model = wave.toggle(Chip.MODEL);
        assertFalse(model.on(Chip.WAVY));
        assertTrue(model.on(Chip.MODEL));
        assertEquals(new CapeStyles("model", false), model.toggle(Chip.ANIMATED));
        assertEquals(model, model.toggle(Chip.MODEL));
        assertEquals(new CapeStyles("frames", true), model.toggle(Chip.FLAT));
        assertEquals(new CapeStyles("frames", true), wave.toggle(Chip.FLAT));
    }

    @Test void previewSelectionAndWearingHaveSeparateIdentities() {
        var selection = new CapeSelection();
        assertFalse(selection.selected(null));
        assertFalse(selection.equipped(null));
        selection.equip("ours/slide");
        long first = selection.select("labymod/slide");
        assertTrue(selection.selected("labymod/slide"));
        assertFalse(selection.equipped("labymod/slide"));
        assertTrue(selection.equipped("ours/slide"));
        assertTrue(selection.current(first));

        long last = selection.select("ours/cloak");
        assertFalse(selection.current(first));
        assertTrue(selection.current(last));
        selection.select(null);
        assertFalse(selection.current(last));
        assertTrue(selection.equipped("ours/slide"));
        selection.equip(null);
        assertFalse(selection.equipped("ours/slide"));
    }
}
