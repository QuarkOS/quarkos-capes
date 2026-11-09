package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.net.CapeHttp;
import dev.quarkos.capes.client.net.GalleryPages;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Forge gallery: the same local samples and shared service used by the Fabric list. */
public final class CapeGalleryScreen extends Screen {
    private final Screen parent;
    private EditBox search;
    private String query = "";
    private String note = "";
    private List<GalleryPages.Entry> entries = List.of();
    private String next;
    private String cursor = "";
    private final List<String> cursors = new ArrayList<>();
    private int page;
    private int generation;
    private String selected;
    private boolean busy;
    private record Row(String name, String author, String id, String sample) { }
    private List<Row> rows = List.of();

    public CapeGalleryScreen(Screen parent) {
        super(Component.translatable("text.quarkos_capes.gallery.title"));
        this.parent = parent;
    }

    private static Component text(String key) { return Component.translatable("text.quarkos_capes." + key); }

    @Override protected void init() {
        clearWidgets();
        int tabWidth = Math.max(1, (width - 16) / CapeBoard.TABS.length);
        for (int i = 0; i < CapeBoard.TABS.length; i++) {
            int tab = i;
            Button button = addRenderableWidget(Button.builder(Component.translatable(CapeBoard.TABS[i]), b -> {
                if (tab != 4) minecraft.setScreen(CapeScreens.openTab(parent, tab));
            }).bounds(8 + i * tabWidth, 26, tabWidth - 2, 20).build());
            button.active = i != 4;
        }
        search = addRenderableWidget(new EditBox(font, 8, 52, Math.max(80, width - 104), 20, text("field.search")));
        search.setMaxLength(120);
        search.setValue(query);
        search.setSuggestion(query.isEmpty() ? text("search.placeholder").getString() : null);
        search.setResponder(value -> { query = value; search.setSuggestion(value.isEmpty() ? text("search.placeholder").getString() : null); });
        addRenderableWidget(Button.builder(text("gallery.search"), b -> { page = 0; cursor = ""; cursors.clear(); load(); })
                .bounds(width - 90, 52, 82, 20).build()).active = !busy;
        List<Row> visible = new ArrayList<>();
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (cursor.isEmpty()) for (String id : List.of("bands", "slide", "cloak")) {
            String name = text("sample." + id).getString();
            if (name.toLowerCase(Locale.ROOT).contains(needle)) visible.add(new Row(name, "", null, id));
        }
        for (GalleryPages.Entry entry : entries) visible.add(new Row(entry.title(), entry.author(), entry.id(), null));
        rows = ClientSettings.get().localOnly ? List.of() : List.copyOf(visible);
        int capacity = capacity();
        page = Math.clamp(page, 0, Math.max(0, (rows.size() - 1) / capacity));
        int end = Math.min(rows.size(), (page + 1) * capacity);
        for (int i = page * capacity; i < end; i++) {
            Row row = rows.get(i);
            int y = 82 + (i % capacity) * 32;
            Button equip = addRenderableWidget(Button.builder(text("gallery.equip"), b -> equip(row))
                    .bounds(width - 72, y, 64, 20).build());
            equip.setTooltip(Tooltip.create(text("tooltip.equip")));
            equip.active = !busy;
        }
        if (ClientSettings.get().localOnly) addRenderableWidget(Button.builder(BrowseScreen.text("go_online"), b -> {
            CapeBoard.localOnly(false); CapeBoard.save(value -> note = value); load();
        }).bounds(Math.max(8, width/2-60), emptyY()+28, 120, 20).build());
        int y = height - 54;
        addRenderableWidget(Button.builder(text("gallery.prev"), b -> {
            if (page > 0) { page--; init(); }
            else if (!cursors.isEmpty()) { cursor = cursors.removeLast(); page = 0; load(); }
        }).bounds(8, y, 76, 20).build()).active = !busy && (page > 0 || !cursors.isEmpty());
        addRenderableWidget(Button.builder(text("gallery.next"), b -> {
            if ((page + 1) * capacity < rows.size()) { page++; init(); }
            else if (next != null) { cursors.add(cursor); cursor = next; page = 0; load(); }
        }).bounds(88, y, 76, 20).build()).active = !busy && ((page + 1) * capacity < rows.size() || next != null);
        addRenderableWidget(Button.builder(text("gallery.report"), b -> CapeBoard.report(selected, value -> { note = value; }))
                .bounds(168, y, Math.min(76, width - 176), 20).build()).active = !busy && selected != null;
        int buttonWidth = Math.max(40, (width - 24) / 3);
        addRenderableWidget(Button.builder(text("button.publish"), b -> CapeBoard.publish(value -> note = value)).bounds(8, height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(text("button.unpublish"), b -> CapeBoard.unpublish(value -> note = value)).bounds(12 + buttonWidth, height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(text("button.done"), b -> onClose()).bounds(16 + buttonWidth * 2, height - 28, buttonWidth, 20).build());
    }

    private int emptyY() { return Math.max(82, (82 + height - 80) / 2 - 20); }

    private int capacity() { return Math.max(1, (height - 156) / 32); }

    private void load() {
        int request = ++generation;
        if (ClientSettings.get().localOnly) { busy = false; entries = List.of(); next = null; init(); return; }
        busy = true;
        note = text(ClientSettings.get().localOnly ? "gallery.quiet" : "status.loading").getString();
        init();
        String submitted = query;
        CapeHttp.gallery(submitted, "", 24, cursor, result -> {
            if (minecraft.screen != this || request != generation) return;
            busy = false; entries = result.entries(); next = result.nextCursor();
            note = text(entries.isEmpty() ? "status.empty" : "status.loaded").getString();
            init();
        }, error -> {
            if (minecraft.screen != this || request != generation) return;
            busy = false; entries = List.of(); next = null; note = error; init();
        });
    }

    private void equip(Row row) {
        if (row.sample != null) {
            CapeBoard.equipSample(row.sample);
            selected = null;
            CapeBoard.save(value -> note = value);
            init();
            return;
        }
        busy = true; note = text("status.loading").getString(); init();
        CapeHttp.open(row.id, opened -> {
            if (minecraft.screen != this) return;
            CapeHttp.equip(opened.detail(), opened.png());
            selected = row.id; busy = false;
            CapeBoard.save(value -> note = value); init();
        }, error -> { if (minecraft.screen == this) { busy = false; note = error; init(); } });
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        graphics.fill(0, 0, width, height, 0xE810141C);
        super.render(graphics, mouseX, mouseY, partial);
        if (ClientSettings.get().localOnly) {
            graphics.drawCenteredString(font, BrowseScreen.text("offline_title"), width/2, emptyY(), 0xFFFFFFFF);
            graphics.drawCenteredString(font, Component.literal(font.plainSubstrByWidth(text("gallery.quiet").getString(), width-16)), width/2, emptyY()+14, 0xFFB6C8DD);
        }
        graphics.drawCenteredString(font, getTitle(), width / 2, 8, 0xFFFFFFFF);
        int capacity = capacity();
        for (int i = page * capacity; i < Math.min(rows.size(), (page + 1) * capacity); i++) {
            Row row = rows.get(i);
            int y = 82 + (i % capacity) * 32;
            int x = row.sample != null ? 32 : 8;
            if (row.sample != null) {
                var texture = switch (row.sample) {
                    case "slide" -> dev.quarkos.capes.client.render.CapeTextures.slideTexture();
                    case "cloak" -> dev.quarkos.capes.client.render.CapeTextures.cloakTexture();
                    default -> dev.quarkos.capes.client.render.CapeTextures.bandsTexture();
                };
                McBridge.capeThumb(graphics, texture, 8, y, 16, 26, "slide".equals(row.sample) ? 256 : 64, 32, 64, 32);
            }
            graphics.drawString(font, font.plainSubstrByWidth(row.name == null ? row.id : row.name, width - x - 80), x, y + 2, 0xFFFFFFFF);
            String author = row.sample != null ? text("gallery.local").getString() : row.author;
            graphics.drawString(font, font.plainSubstrByWidth(author == null ? "" : author, width - x - 80), x, y + 13, 0xFFAAAAAA);
        }
        String status = note;
        graphics.drawString(font, font.plainSubstrByWidth(status, width - 16), 8, height - 68, 0xFFE8B84A);
    }

    @Override public void onClose() { generation++; minecraft.setScreen(CapeScreens.refreshed(parent)); }
}
