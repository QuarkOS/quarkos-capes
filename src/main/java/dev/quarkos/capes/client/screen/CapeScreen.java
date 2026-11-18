package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.render.CapeTextures;
import dev.quarkos.capes.client.screen.ui.CapeGridLayout;
import dev.quarkos.capes.client.screen.ui.CapeGridLayout.Rect;
import dev.quarkos.capes.client.screen.ui.CapeSelection;
import dev.quarkos.capes.client.screen.ui.CapeStyles;
import dev.quarkos.capes.client.screen.ui.CapeTheme;
import dev.quarkos.capes.client.screen.ui.CapeTheme.Face;
import dev.quarkos.capes.client.screen.ui.CapeUiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static dev.quarkos.capes.client.screen.ui.CapeTheme.text;

public final class CapeScreen extends CapeUiScreen {
    private static final String CURRENT = "current";
    private static final String CURRENT_TEXTURE = "wardrobe/current";
    private static final CapeStyles.Chip[] SHAPES = {CapeStyles.Chip.FLAT, CapeStyles.Chip.WAVY, CapeStyles.Chip.MODEL};
    private static final int PANEL_GAP = 10;
    private static final int SHELF_INSET = 7;
    private static final int SHELF_HEADER = 19;
    private static final int MAX_PREVIEW_WIDTH = 196;
    private static final int PREVIEW_CHROME = 42;
    private static final int FOOTER_GAP = 6;
    private static final int FOOTER_HEIGHT = 28;

    private final Screen parent;
    private final CapeSelection selection = new CapeSelection();
    private final Map<String, Button> cards = new LinkedHashMap<>();
    private final Map<CapeStyles.Chip, CapeTheme.FlatButton> chips = new LinkedHashMap<>();
    private CapeGridLayout grid;
    private Rect shelf, sidebar, shapeBar, footer, statusPill, actions;
    private int titleWidth, page;
    private OwnerCape observedCape;
    private String equipped;
    private String note = "";

    public CapeScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
    }

    public String status() { return note; }
    public Button sampleCard(String id) { return cards.get(id); }
    public Button styleChip(CapeStyles.Chip chip) { return chips.get(chip); }

    @Override
    protected void init() {
        resetWidgets();
        cards.clear();
        chips.clear();

        OwnerCape cape = OwnerCape.get();
        observedCape = cape;
        equipped = null;
        if (cape.png.length != 0) {
            equipped = CapeThumbs.SAMPLES.stream().filter(CapeBoard::sampleOn).findFirst().orElse(CURRENT);
        }
        selection.equip(equipped);
        List<String> ids = new ArrayList<>(CapeThumbs.SAMPLES);
        if (CURRENT.equals(equipped)) ids.addFirst(CURRENT);
        layout(ids.size());

        int navWidth = Math.min(260, frame.width() * 3 / 5);
        Rect nav = new Rect(frame.right() - navWidth, frame.y(), navWidth, CapeTheme.CONTROL);
        titleWidth = nav.x() - frame.x() - 8;
        button(text("tab.gallery"), nav.cell(0, 4, 4), Face.TAB, () -> minecraft.setScreen(new CapeGalleryScreen(this)));
        button(text("site.browse"), nav.cell(1, 4, 4), Face.TAB, () -> minecraft.setScreen(new BrowseScreen(this)));
        button(text("ui.settings"), nav.cell(2, 4, 4), Face.TAB, () -> minecraft.setScreen(CapeScreens.settings(this)));
        button(text("button.done"), nav.cell(3, 4, 4), Face.BUTTON, this::onClose);

        Rect segments = shapeBar.inset(1);
        for (int i = 0; i < SHAPES.length; i++) {
            CapeStyles.Chip chip = SHAPES[i];
            addStyle(chip, text("ui.mode." + chip.name().toLowerCase(Locale.ROOT)),
                    segments.cell(i, SHAPES.length, 1), Face.SEGMENT);
        }
        int switchX = shapeBar.right() + 12;
        addStyle(CapeStyles.Chip.ANIMATED, text("ui.animation"),
                new Rect(switchX, shapeBar.y(), Math.min(100, frame.right() - switchX), CapeTheme.CONTROL), Face.SWITCH);

        page = grid.clampPage(page, ids.size());
        boolean hasPages = grid.pages(ids.size()) > 1;
        if (hasPages) {
            int y = shelf.y() + 3;
            button(text("site.previous"), new Rect(shelf.right() - 91, y, 48, 14), Face.BUTTON, () -> changePage(-1)).active = page > 0;
            button(text("site.next"), new Rect(shelf.right() - 40, y, 36, 14), Face.BUTTON, () -> changePage(1)).active = page + 1 < grid.pages(ids.size());
        }
        int start = page * grid.capacity();
        for (int i = start; i < Math.min(ids.size(), start + grid.capacity()); i++) {
            String id = ids.get(i);
            boolean local = CURRENT.equals(id);
            OwnerCape art = local ? cape : CapeThumbs.sample(id);
            String key = local ? CURRENT_TEXTURE : "sample/" + id;
            var image = CapeTextures.get(key);
            if (image == null) image = CapeTextures.upload(key, art.png, false, false);
            var texture = image;
            Button card = addRenderableWidget(CapeTheme.card(capeName(id), null, grid.card(i - start),
                    (graphics, area) -> CapeThumbs.draw(graphics, area, texture, selection.equipped(id) ? OwnerCape.get() : art),
                    () -> false, () -> selection.equipped(id), () -> equip(id)));
            cards.put(id, card);
        }

        if (sidebar.width() > 0) {
            preview(new Rect(sidebar.x() + 7, sidebar.y() + 18, sidebar.width() - 14, sidebar.height() - PREVIEW_CHROME), OwnerCape::get);
        }

        button(text("button.save"), actions.cell(0, 4, 4), Face.BUTTON, () -> CapeBoard.save(this::note));
        button(text("button.publish"), actions.cell(1, 4, 4), Face.BUTTON, () -> CapeBoard.publish(this::note));
        button(text("button.unpublish"), actions.cell(2, 4, 4), Face.BUTTON, () -> CapeBoard.unpublish(this::note));
        button(text("ui.unequip"), actions.cell(3, 4, 4), Face.BUTTON, this::unequip).active = equipped != null;
        updateStyles();
    }

    private void layout(int count) {
        int samples = CapeThumbs.SAMPLES.size();
        int sampleWidth = samples * (CapeGridLayout.CARD_WIDTH + CapeGridLayout.GAP) - CapeGridLayout.GAP + 2 * SHELF_INSET;
        int sideWidth = Math.min(MAX_PREVIEW_WIDTH, CapeGridLayout.previewWidth(frame));
        // Keep a row of bundled capes accessible when the preview is visible.
        sideWidth = Math.min(sideWidth, Math.max(0, frame.width() - sampleWidth - PANEL_GAP));
        int sideSpan = sideWidth == 0 ? 0 : sideWidth + PANEL_GAP;
        int span = Math.min(frame.width(), sampleWidth + CapeGridLayout.CARD_WIDTH + CapeGridLayout.GAP + sideSpan);
        int shelfWidth = span - sideSpan;

        int bodyOffset = frame.height() < 245 ? 56 : 64;
        int chrome = SHELF_HEADER + SHELF_INSET;
        int panelLimit = Math.max(0, frame.height() - bodyOffset - FOOTER_GAP - FOOTER_HEIGHT - font.lineHeight - 3);
        Rect available = new Rect(0, 0, Math.max(0, shelfWidth - 2 * SHELF_INSET), Math.max(0, panelLimit - chrome));
        int cardsHeight = CapeGridLayout.fit(available).visibleHeight(count);
        int panelHeight = Math.min(panelLimit, Math.max(chrome + cardsHeight, sideWidth == 0 ? 0 : 152));

        int contentHeight = bodyOffset + panelHeight + FOOTER_GAP + FOOTER_HEIGHT;
        frame = new Rect((width - span) / 2, (height - contentHeight - font.lineHeight - 3) / 2, span, contentHeight);
        int top = frame.y() + bodyOffset;
        shelf = new Rect(frame.x(), top, shelfWidth, panelHeight);
        int sideHeight = PREVIEW_CHROME + CapeGridLayout.previewHeight(sideWidth - 14, panelHeight - PREVIEW_CHROME);
        sidebar = new Rect(frame.right() - sideWidth, top, sideWidth, sideHeight);
        int cardTop = top + SHELF_HEADER + Math.max(0, panelHeight - chrome - cardsHeight) / 2;
        grid = CapeGridLayout.fit(new Rect(shelf.x() + SHELF_INSET, cardTop, available.width(),
                Math.max(0, shelf.bottom() - SHELF_INSET - cardTop)));
        shapeBar = new Rect(frame.x(), top - CapeTheme.CONTROL - 6, Math.min(174, frame.width() * 3 / 5), CapeTheme.CONTROL);

        footer = new Rect(frame.x(), shelf.bottom() + FOOTER_GAP, frame.width(), FOOTER_HEIGHT);
        int statusWidth = Math.min(72, 18 + Math.max(font.width(text("ui.online")), font.width(text("ui.offline"))));
        statusPill = new Rect(footer.x() + 4, footer.centerY() - 7, statusWidth, 14);
        int actionWidth = Math.min(footer.width() - statusWidth - 12, Math.min(348, Math.max(232, footer.width() * 3 / 5)));
        actions = new Rect(footer.right() - 4 - actionWidth, footer.y() + 4, actionWidth, CapeTheme.CONTROL);
    }

    private void addStyle(CapeStyles.Chip chip, Component label, Rect area, Face face) {
        chips.put(chip, button(label, area, face, () -> {
            CapeBoard.toggleStyle(chip);
            updateStyles();
        }));
    }

    private void changePage(int delta) {
        page += delta;
        init();
    }

    private Component capeName(String id) {
        if (id == null) return text("site.nothing");
        if (CURRENT.equals(id)) return text(ClientSettings.get().equippedGalleryId == null ? "ui.local_cape" : "gallery.copy");
        return text("sample." + id);
    }

    private void equip(String id) {
        if (selection.equipped(id)) return;
        CapeBoard.equipSample(id);
        note = "";
        clearCurrentTexture();
        init();
    }

    private void unequip() {
        CapeBoard.unequipLocal();
        clearCurrentTexture();
        note = text("site.unworn").getString();
        init();
    }

    private void updateStyles() {
        CapeStyles state = CapeBoard.styleChips();
        chips.forEach((chip, button) -> {
            button.selected(equipped != null && state.on(chip));
            button.active = equipped != null;
        });
    }

    private void note(String value) { note = value; }

    @Override
    public void tick() {
        if (OwnerCape.get() != observedCape) {
            clearCurrentTexture();
            init();
        }
    }

    @Override
    protected void renderPanels(GuiGraphics graphics) {
        CapeTheme.title(graphics, font, getTitle(), frame.x(), frame.y() + 6, titleWidth);
        CapeTheme.label(graphics, font, text("ui.shape"), shapeBar.x(), shapeBar.y() - 11, shapeBar.width(), CapeTheme.MUTED);
        CapeTheme.panel(graphics, shapeBar);
        CapeTheme.panel(graphics, shelf);
        int headingWidth = shelf.width() - (grid.pages(CapeThumbs.SAMPLES.size() + (CURRENT.equals(equipped) ? 1 : 0)) > 1 ? 102 : 14);
        CapeTheme.label(graphics, font, text("tab.my_cape"), shelf.x() + 7, shelf.y() + 6, headingWidth, CapeTheme.TEXT);
        if (previewBounds() != null) {
            CapeTheme.panel(graphics, sidebar);
            CapeTheme.label(graphics, font, text("ui.preview"), sidebar.x() + 7, sidebar.y() + 7, sidebar.width() - 14, CapeTheme.TEXT);
            CapeTheme.label(graphics, font, Component.translatable("text.quarkos_capes.site.wearing", capeName(equipped)),
                    sidebar.x() + 7, sidebar.bottom() - 21, sidebar.width() - 14, CapeTheme.TEXT);
            if (minecraft.player != null) CapeTheme.label(graphics, font, text("ui.rotate"), sidebar.x() + 7,
                    sidebar.bottom() - 11, sidebar.width() - 14, CapeTheme.MUTED);
        }
        CapeTheme.panel(graphics, footer);
        CapeTheme.connectionStatus(graphics, font, statusPill, !ClientSettings.get().localOnly);
        if (!note.isBlank()) {
            int noteX = statusPill.right() + 8;
            int noteWidth = actions.x() - noteX - 4;
            if (font.width(note) <= noteWidth) {
                CapeTheme.line(graphics, font, note, noteX, footer.centerY() - font.lineHeight / 2, noteWidth, CapeTheme.MUTED);
            } else {
                CapeTheme.line(graphics, font, note, footer.x() + 4, footer.bottom() + 3, footer.width() - 8, CapeTheme.MUTED);
            }
        }
    }

    @Override
    public void removed() {
        clearCurrentTexture();
        super.removed();
    }

    private static void clearCurrentTexture() {
        if (CapeTextures.get(CURRENT_TEXTURE) != null) CapeTextures.clear(CURRENT_TEXTURE);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
