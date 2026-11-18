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

    private final Screen parent;
    private final CapeSelection selection = new CapeSelection();
    private final Map<String, Button> cards = new LinkedHashMap<>();
    private final Map<CapeStyles.Chip, CapeTheme.FlatButton> chips = new LinkedHashMap<>();
    private CapeGridLayout grid;
    private Rect shelf, sidebar, styleBar;
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

        int navWidth = Math.min(260, frame.width() * 3 / 5);
        Rect nav = new Rect(frame.right() - navWidth, frame.y(), navWidth, CapeTheme.CONTROL);
        titleWidth = nav.x() - frame.x() - 8;
        button(text("tab.gallery"), nav.cell(0, 4, 4), Face.TAB, () -> minecraft.setScreen(new CapeGalleryScreen(this)));
        button(text("site.browse"), nav.cell(1, 4, 4), Face.TAB, () -> minecraft.setScreen(new BrowseScreen(this)));
        button(text("ui.settings"), nav.cell(2, 4, 4), Face.TAB, () -> minecraft.setScreen(CapeScreens.settings(this)));
        button(text("button.done"), nav.cell(3, 4, 4), Face.BUTTON, this::onClose);

        boolean compact = frame.height() < 190;
        styleBar = new Rect(frame.x(), frame.y() + (compact ? 37 : 41), Math.min(390, frame.width()), CapeTheme.CONTROL);
        CapeStyles.Chip[] styles = CapeStyles.Chip.values();
        for (int i = 0; i < styles.length; i++) {
            CapeStyles.Chip chip = styles[i];
            String key = "ui.mode." + chip.name().toLowerCase(Locale.ROOT);
            chips.put(chip, button(text(key), styleBar.cell(i, styles.length, 4), Face.CHIP, () -> {
                CapeBoard.toggleStyle(chip);
                updateStyles();
            }));
        }

        OwnerCape cape = OwnerCape.get();
        observedCape = cape;
        equipped = null;
        if (cape.png.length != 0) {
            equipped = CapeThumbs.SAMPLES.stream().filter(CapeBoard::sampleOn).findFirst().orElse(CURRENT);
        }
        selection.equip(equipped);
        List<String> ids = new ArrayList<>(CapeThumbs.SAMPLES);
        if (CURRENT.equals(equipped)) ids.addFirst(CURRENT);

        int bottom = frame.bottom() - 40;
        int sideWidth = CapeGridLayout.previewWidth(frame);
        int top = frame.y() + (compact ? 61 : 68);
        int shelfWidth = frame.width() - (sideWidth == 0 ? 0 : sideWidth + 10);
        int bodyHeight = Math.max(0, Math.min(bottom - top, sideWidth == 0 ? 218 : sideWidth + 86));
        shelf = new Rect(frame.x(), top, shelfWidth, bodyHeight);
        sidebar = new Rect(frame.right() - sideWidth, top, sideWidth, bodyHeight);

        Rect cells = new Rect(shelf.x() + 7, shelf.y() + 19, shelf.width() - 14, Math.max(0, shelf.height() - 26));
        int preferred = Math.min(sideWidth == 0 ? 132 : sideWidth + 60, cells.height());
        grid = CapeGridLayout.fit(cells, 70, preferred, ids.size());
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

        if (sideWidth > 0) {
            preview(new Rect(sidebar.x() + 7, sidebar.y() + 20, sidebar.width() - 14, sidebar.height() - 49), OwnerCape::get);
        }

        int actionsWidth = Math.min(348, frame.width());
        Rect actions = new Rect(frame.x(), frame.bottom() - CapeTheme.CONTROL, actionsWidth, CapeTheme.CONTROL);
        button(text("button.save"), actions.cell(0, 4, 4), Face.BUTTON, () -> CapeBoard.save(this::note));
        button(text("button.publish"), actions.cell(1, 4, 4), Face.BUTTON, () -> CapeBoard.publish(this::note));
        button(text("button.unpublish"), actions.cell(2, 4, 4), Face.BUTTON, () -> CapeBoard.unpublish(this::note));
        button(text("ui.unequip"), actions.cell(3, 4, 4), Face.BUTTON, this::unequip).active = equipped != null;
        updateStyles();
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
        CapeTheme.label(graphics, font, text("ui.render_style"), styleBar.x(), styleBar.y() - 12, styleBar.width(), CapeTheme.MUTED);
        CapeTheme.panel(graphics, shelf);
        int headingWidth = shelf.width() - (grid.pages(CapeThumbs.SAMPLES.size() + (CURRENT.equals(equipped) ? 1 : 0)) > 1 ? 102 : 14);
        CapeTheme.label(graphics, font, text("tab.my_cape"), shelf.x() + 7, shelf.y() + 6, headingWidth, CapeTheme.TEXT);
        if (previewBounds() != null) {
            CapeTheme.panel(graphics, sidebar);
            CapeTheme.label(graphics, font, text("ui.preview"), sidebar.x() + 7, sidebar.y() + 7, sidebar.width() - 14, CapeTheme.TEXT);
            CapeTheme.label(graphics, font, Component.translatable("text.quarkos_capes.site.wearing", capeName(equipped)),
                    sidebar.x() + 7, sidebar.bottom() - 24, sidebar.width() - 14, CapeTheme.TEXT);
            if (minecraft.player != null) CapeTheme.label(graphics, font, text("ui.rotate"), sidebar.x() + 7,
                    sidebar.bottom() - 13, sidebar.width() - 14, CapeTheme.MUTED);
        }
        Component state = ClientSettings.get().localOnly ? text("site.offline_title") : text("ui.online");
        String status = note.isBlank() ? state.getString() : note;
        CapeTheme.line(graphics, font, status, frame.x(), frame.bottom() - 33, frame.width(), CapeTheme.MUTED);
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
