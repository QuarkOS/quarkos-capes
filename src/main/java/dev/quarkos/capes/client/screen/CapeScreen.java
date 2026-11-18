package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.LocalFiles;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.net.CapeHttp;
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
    private static final int SHELF_INSET = 6;
    private static final int SHELF_HEADER = 19;
    private static final int PREVIEW_CHROME = 42;
    private static final int FOOTER_GAP = 6;
    private static final int FOOTER_HEIGHT = 28;

    private final Screen parent;
    private final CapeSelection selection = new CapeSelection();
    private final Map<String, Button> cards = new LinkedHashMap<>();
    private final Map<CapeStyles.Chip, CapeTheme.FlatButton> chips = new LinkedHashMap<>();
    private CapeGridLayout grid;
    private Rect shelf, sidebar, shapeBar, footer, statusPill, actions;
    private int titleWidth, page, footerNoteY;
    private OwnerCape observedCape;
    private String equipped;
    private String note = "";

    public CapeScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
    }

    @Override
    public void added() {
        super.added();
        CapeHttp.checkService();
    }

    public String status() { return note; }
    public boolean online() { return !ClientSettings.get().localOnly && CapeHttp.serviceIssue() == null; }
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
        Component animationOff = text("ui.animation_off");
        int switchWidth = 33 + Math.max(font.width(animationOff), font.width(text("ui.animation_on")));
        layout(switchWidth);

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
        addStyle(CapeStyles.Chip.ANIMATED, animationOff,
                new Rect(switchX, shapeBar.y(), Math.min(switchWidth, frame.right() - switchX), CapeTheme.CONTROL), Face.SWITCH);

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

    private void layout(int switchWidth) {
        int samples = CapeThumbs.SAMPLES.size();
        int sampleWidth = samples * CapeGridLayout.CARD_WIDTH + (samples - 1) * CapeGridLayout.GAP + 2 * SHELF_INSET;
        // Keep the bundled samples visible together when the frame is narrow.
        int sideLimit = Math.max(0, frame.width() - sampleWidth - CapeGridLayout.PANEL_GAP);
        int sideWidth = Math.min(sideLimit, CapeGridLayout.previewWidth(frame));
        int top = frame.y() + (frame.height() < 300 ? 60 : 64);
        footer = new Rect(frame.x(), frame.bottom() - FOOTER_HEIGHT, frame.width(), FOOTER_HEIGHT);
        boolean noteAboveFooter = height - footer.bottom() < font.lineHeight + 3;
        int gap = noteAboveFooter ? font.lineHeight + 4 : FOOTER_GAP;
        footerNoteY = noteAboveFooter ? footer.y() - font.lineHeight - 2 : footer.bottom() + 3;
        shelf = CapeGridLayout.gridPanel(frame, top, footer.y() - gap, sideWidth);
        int sideHeight = PREVIEW_CHROME + CapeGridLayout.previewHeight(sideWidth - 14, shelf.height() - PREVIEW_CHROME);
        sidebar = new Rect(frame.right() - sideWidth, top, sideWidth, sideHeight);
        grid = CapeGridLayout.fit(new Rect(shelf.x() + SHELF_INSET, shelf.y() + SHELF_HEADER,
                Math.max(0, shelf.width() - 2 * SHELF_INSET), Math.max(0, shelf.height() - SHELF_HEADER - SHELF_INSET)));
        int shapeWidth = Math.min(174, frame.width() - Math.min(switchWidth, frame.width() * 2 / 5) - 12);
        shapeBar = new Rect(frame.x(), top - CapeTheme.CONTROL - 6, shapeWidth, CapeTheme.CONTROL);

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
            boolean selected = equipped != null && state.on(chip);
            button.selected(selected);
            button.active = equipped != null;
            if (chip == CapeStyles.Chip.ANIMATED) button.setMessage(text(selected ? "ui.animation_on" : "ui.animation_off"));
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
        CapeTheme.connectionStatus(graphics, font, statusPill, online());
        if (!note.isBlank()) {
            int noteX = statusPill.right() + 8;
            int noteWidth = actions.x() - noteX - 4;
            if (font.width(note) <= noteWidth) {
                CapeTheme.line(graphics, font, note, noteX, footer.centerY() - font.lineHeight / 2, noteWidth, CapeTheme.MUTED);
            } else {
                CapeTheme.line(graphics, font, note, footer.x() + 4, footerNoteY, footer.width() - 8, CapeTheme.MUTED);
            }
        }
    }

    @Override
    public void removed() {
        LocalFiles.autosave(true);
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
