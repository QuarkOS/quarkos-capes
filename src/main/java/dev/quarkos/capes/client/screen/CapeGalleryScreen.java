package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.net.CapeHttp;
import dev.quarkos.capes.client.net.GalleryPages;
import dev.quarkos.capes.client.screen.ui.CapeTheme.Face;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static dev.quarkos.capes.client.screen.ui.CapeTheme.text;

/** Bundled and shared capes. Wearing a shared cape makes a local copy. */
public final class CapeGalleryScreen extends CapeBrowserScreen<CapeGalleryScreen.Row> {
    record Row(String sample, GalleryPages.Entry entry) { }

    private static final String[] STYLES = {"", "flat", "frames", "wave", "model"};
    private int style, source;
    private boolean saving, byName;
    private String copiedTitle;

    public CapeGalleryScreen(Screen parent) {
        super(parent, text("gallery.title"));
    }

    public void search(String query) { searchFor(query); }

    @Override protected boolean localOnly() { return false; }

    public List<GalleryPages.Entry> sharedEntries() {
        return items().stream().map(Row::entry).filter(java.util.Objects::nonNull).toList();
    }

    @Override protected String key(Row row) { return row.sample() == null ? "shared/" + row.entry().id() : "sample/" + row.sample(); }

    @Override
    protected Component title(Row row) {
        if (row.sample() != null) return text("sample." + row.sample());
        return Component.literal(row.entry().title() == null || row.entry().title().isBlank() ? row.entry().id() : row.entry().title());
    }

    @Override
    protected Component creator(Row row) {
        if (row.sample() != null) return text("ui.bundled");
        String author = row.entry().author();
        if (author == null || author.isBlank()) return text("ui.community_creator");
        try {
            String name = McBridge.playerName(UUID.fromString(author.trim()));
            return name.isBlank() ? Component.literal(author) : Component.literal(name);
        } catch (IllegalArgumentException ignored) {
            return Component.literal(author);
        }
    }

    @Override
    protected boolean equipped(Row row) {
        return row.sample() != null ? CapeBoard.sampleOn(row.sample()) : row.entry().id().equals(ClientSettings.get().equippedGalleryId);
    }

    @Override
    protected Component wearing() {
        Component capeName = OwnerCape.get().png.length == 0 ? text("site.nothing")
                : ClientSettings.get().equippedGalleryId == null ? text("ui.local_cape") : text("gallery.copy");
        if (copiedTitle != null && ClientSettings.get().equippedGalleryId != null) capeName = Component.literal(copiedTitle);
        for (Row row : items()) {
            if (equipped(row)) {
                capeName = title(row);
                break;
            }
        }
        return Component.translatable("text.quarkos_capes.site.wearing", capeName);
    }

    @Override
    protected List<Filter> filters() {
        String sourceKey = switch (source) {
            case 1 -> "ui.source_bundled";
            case 2 -> "ui.source_shared";
            default -> "site.all_sources";
        };
        Component kind = style == 0 ? text("site.all_kinds") : Component.translatable("text.quarkos_capes.ui.kind_style", text("style." + STYLES[style]));
        return List.of(new Filter(text(sourceKey), () -> source = (source + 1) % 3),
                new Filter(kind, () -> style = (style + 1) % STYLES.length),
                new Filter(text(byName ? "ui.sort_page_name" : "ui.sort_page_default"), () -> byName = !byName));
    }

    @Override
    protected List<Action> actions() {
        Row row = selected();
        boolean shared = row != null && row.entry() != null;
        return List.of(new Action(text("site.wear"), Face.PRIMARY, !saving && previewReady() && row != null && !equipped(row), this::wear),
                new Action(text("site.unwear"), Face.BUTTON, !saving && OwnerCape.get().png.length != 0, this::unwear),
                new Action(text("gallery.report"), Face.BUTTON, !saving && shared, this::report));
    }

    @Override
    protected void request(String query, String cursor, Consumer<Results<Row>> ready, Consumer<String> error) {
        Map<String, Row> samples = new LinkedHashMap<>();
        if (cursor.isEmpty() && source != 2) {
            String needle = query.toLowerCase(Locale.ROOT);
            for (String id : CapeThumbs.SAMPLES) {
                var row = new Row(id, null);
                String name = title(row).getString();
                if ((style == 0 || STYLES[style].equals(CapeThumbs.sample(id).style))
                        && (id.contains(needle) || name.toLowerCase(Locale.ROOT).contains(needle))) samples.put(name.trim(), row);
            }
        }
        if (source == 1 || ClientSettings.get().localOnly) {
            ready.accept(results(List.copyOf(samples.values()), null, ""));
            return;
        }
        if (!samples.isEmpty()) ready.accept(results(List.copyOf(samples.values()), null, ""));
        CapeHttp.gallery(query, STYLES[style], 20, cursor, result -> {
            List<Row> rows = merge(query, samples, result.entries());
            String status = query.isBlank() ? "" : Component.translatable("text.quarkos_capes.gallery.search_result", query, result.entries().size()).getString();
            ready.accept(results(rows, result.nextCursor(), status));
        }, note -> {
            if (samples.isEmpty()) error.accept(note);
            else if (CapeHttp.serviceIssue() == null) ready.accept(results(List.copyOf(samples.values()), null, note));
        });
    }

    private Results<Row> results(List<Row> rows, String next, String status) {
        if (byName) {
            rows = rows.stream().sorted(Comparator.comparing(row -> title(row).getString(), String.CASE_INSENSITIVE_ORDER)).toList();
        }
        return new Results<>(rows, next, status, null);
    }

    private List<Row> merge(String query, Map<String, Row> samples, List<GalleryPages.Entry> entries) {
        List<GalleryRows.Local> local = samples.keySet().stream().map(name -> new GalleryRows.Local(name, "")).toList();
        Map<String, Row> sharedById = new LinkedHashMap<>();
        List<GalleryRows.Shared> shared = new ArrayList<>();
        for (GalleryPages.Entry entry : entries) {
            Row row = new Row(null, entry);
            String name = title(row).getString().trim();
            shared.add(new GalleryRows.Shared(entry.id(), name, entry.author()));
            sharedById.putIfAbsent(entry.id(), row);
        }
        List<Row> rows = new ArrayList<>();
        for (GalleryRows.Row row : GalleryRows.visible(query, local, shared)) {
            Row item = row.id() == null ? samples.get(row.name()) : sharedById.get(row.id());
            if (item != null) rows.add(item);
        }
        return rows;
    }

    @Override
    protected void requestPreview(Row row, Consumer<OwnerCape> ready, Consumer<String> error) {
        if (row.sample() != null) ready.accept(CapeThumbs.sample(row.sample()));
        else CapeHttp.open(row.entry().id(), opened -> ready.accept(CapeHttp.owner(opened.detail(), opened.png())), error);
    }

    @Override
    protected void requestThumbnail(Row row, Consumer<OwnerCape> ready, Consumer<String> error) {
        if (row.sample() != null) ready.accept(CapeThumbs.sample(row.sample()));
        else CapeHttp.openThumbnail(row.entry().id(), opened -> ready.accept(CapeHttp.owner(opened.detail(), opened.png())), error);
    }

    private void wear() {
        Row row = selected();
        if (row == null || saving || !previewReady() || equipped(row) || offline()) return;
        if (row.sample() != null) {
            CapeBoard.equipSample(row.sample());
            copiedTitle = null;
        } else {
            OwnerCape.set(draft().copy());
            ClientSettings.get().equippedGalleryId = row.entry().id();
            copiedTitle = title(row).getString();
        }
        save();
    }

    private void unwear() {
        if (saving || offline()) return;
        CapeBoard.unequipLocal();
        copiedTitle = null;
        save();
    }

    private void save() {
        saving = true;
        CapeBoard.save(message -> {
            setStatus(message);
            if (!message.equals(text("status.saving").getString())) saving = false;
            if (minecraft.screen == this) rebuild();
        });
    }

    private void report() {
        Row row = selected();
        if (row != null && row.entry() != null) CapeBoard.report(row.entry().id(), this::setStatus);
    }
}
