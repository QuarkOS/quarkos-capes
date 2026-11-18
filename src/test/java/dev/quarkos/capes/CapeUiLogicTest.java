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
        assertGrid(1920, 1080, 4, 3, 2, true);
        assertGrid(1920, 1320, 4, 3, 2, true);
        assertGrid(960, 720, 3, 3, 1, false);
    }

    private static void assertGrid(int pixelsWide, int pixelsHigh, int scale, int columns, int rows, boolean preview) {
        Rect frame = CapeGridLayout.frame(Math.ceilDiv(pixelsWide, scale), Math.ceilDiv(pixelsHigh, scale));
        int side = CapeGridLayout.previewWidth(frame);
        assertEquals(preview, side > 0);
        int top = frame.y() + (frame.width() >= 512 ? 54 : 78);
        Rect regularGrid = CapeGridLayout.gridPanel(frame, top, frame.bottom() - 35, side).inset(6);
        Rect area = preview && CapeGridLayout.compactBrowser(frame, regularGrid)
                ? CapeGridLayout.gridPanel(frame, frame.y() + 48, frame.bottom(), side).inset(4)
                : regularGrid;
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

    @Test void wardrobeGridAndPreviewUseTheSharedFrame() {
        Rect frame = CapeGridLayout.frame(960, 540);
        int previewWidth = CapeGridLayout.previewWidth(frame);
        assertEquals(0.35, (double) previewWidth / frame.width(), 0.01);
        int shelfTop = frame.y() + 64;
        int shelfBottom = frame.bottom() - 34;
        Rect shelf = CapeGridLayout.gridPanel(frame, shelfTop, shelfBottom, previewWidth);
        assertEquals(frame.x(), shelf.x());
        assertEquals(shelfBottom, shelf.bottom());
        assertEquals(10, frame.right() - previewWidth - shelf.right());
        var grid = CapeGridLayout.fit(new Rect(shelf.x() + 6, shelf.y() + 19, shelf.width() - 12, shelf.height() - 25));
        assertEquals(6, grid.columns());
        assertEquals(3, grid.rows());
    }

    @Test void aTallPreviewLeavesRoomForTheModelToTurn() {
        assertEquals(172, CapeGridLayout.previewHeight(100, 400));
        assertEquals(100, CapeGridLayout.previewHeight(100, 100));
    }

    @Test void previewScaleUsesViewportHeightAndCapsTheTurningWidth() {
        float modelHeight = 30f / 16f;
        assertEquals(160f, CapeGridLayout.previewScale(160, 200) * modelHeight, 0.01f);
        assertEquals(80f, CapeGridLayout.previewScale(80, 100) * modelHeight, 0.01f);
        float narrowScale = CapeGridLayout.previewScale(54, 200);
        assertEquals(54f, narrowScale * 1.35f, 0.01f);
        assertTrue(narrowScale * modelHeight < 160f);
        assertEquals(0f, CapeGridLayout.previewScale(0, 200));
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
