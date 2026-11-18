package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.net.CatalogClient;
import dev.quarkos.capes.client.net.SiteApi;
import dev.quarkos.capes.client.screen.ui.CapeTheme;
import dev.quarkos.capes.client.screen.ui.CapeTheme.Face;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** The website catalog and its account-wide wearing reference. */
public final class BrowseScreen extends CapeBrowserScreen<CatalogClient.Item> {
    public interface Catalog {
        boolean offline();
        void page(CatalogClient.Query query, Consumer<CatalogClient.Page> ready, Consumer<String> error);
        void detail(CatalogClient.Item item, Consumer<CatalogClient.Item> ready, Consumer<String> error);
        void image(CatalogClient.Item item, Consumer<byte[]> ready, Consumer<String> error);
        void wearing(Consumer<CatalogClient.Item> ready, Consumer<String> error);
        void wear(CatalogClient.Item item, Consumer<CatalogClient.Item> ready, Consumer<String> error);
        void unwear(Consumer<String> ready, Consumer<String> error);
    }

    private static final Catalog SITE = new Catalog() {
        @Override public boolean offline() { return ClientSettings.get().localOnly; }
        @Override public void page(CatalogClient.Query query, Consumer<CatalogClient.Page> ready, Consumer<String> error) { SiteApi.page(query, ready, error); }
        @Override public void detail(CatalogClient.Item item, Consumer<CatalogClient.Item> ready, Consumer<String> error) { SiteApi.detail(item, ready, error); }
        @Override public void image(CatalogClient.Item item, Consumer<byte[]> ready, Consumer<String> error) { SiteApi.image(item, ready, error); }
        @Override public void wearing(Consumer<CatalogClient.Item> ready, Consumer<String> error) { SiteApi.wearing(ready, error); }
        @Override public void wear(CatalogClient.Item item, Consumer<CatalogClient.Item> ready, Consumer<String> error) { SiteApi.wear(item, ready, error); }
        @Override public void unwear(Consumer<String> ready, Consumer<String> error) { SiteApi.unwear(ready, error); }
    };

    private static final String[] SOURCES = {"all_sources", "ours", "labymod"};
    private static final String[] KINDS = {"all_kinds", "cape", "cloak"};
    private static final String[] SORTS = {"popular", "newest", "name"};

    private final Catalog catalog;
    private CatalogClient.Item wearing;
    private int source, kind, sort, wearingRequest;
    private boolean writing;

    public BrowseScreen(Screen parent) { this(parent, SITE); }

    public BrowseScreen(Screen parent, String source) {
        this(parent);
        this.source = sourceIndex(source);
    }

    public BrowseScreen(Screen parent, Catalog catalog) {
        super(parent, text("browse_title"));
        this.catalog = catalog;
    }

    public static Component text(String key) { return CapeTheme.text("site." + key); }

    private static int sourceIndex(String value) {
        return "ours".equals(value) ? 1 : "labymod".equals(value) ? 2 : 0;
    }

    public void search(String query, String sourceValue) {
        source = sourceIndex(sourceValue);
        searchFor(query);
    }

    public boolean isWriting() { return writing; }
    public boolean isWearing(CatalogClient.Item item) { return wearing != null && key(wearing).equals(key(item)); }

    public void wearSelected() {
        CatalogClient.Item item = selected();
        if (offline() || item == null || !previewReady() || isWearing(item) || writing) return;
        startWrite();
        catalog.wear(item, worn -> {
            wearing = worn;
            finishWrite(SiteApi.message("worn"));
        }, this::finishWrite);
    }

    public void unwear() {
        if (offline() || wearing == null || writing) return;
        startWrite();
        catalog.unwear(message -> {
            wearing = null;
            finishWrite(message);
        }, this::finishWrite);
    }

    private void startWrite() {
        wearingRequest++;
        writing = true;
        setStatus(SiteApi.message("working"));
        rebuild();
    }

    private void finishWrite(String message) {
        writing = false;
        if (minecraft.screen == this) {
            setStatus(message);
            rebuild();
        }
    }

    @Override protected String namespace() { return "browse"; }
    @Override protected boolean offline() { return catalog.offline(); }
    @Override protected boolean equipped(CatalogClient.Item item) { return isWearing(item); }
    @Override protected String key(CatalogClient.Item item) { return item.source() + "/" + item.id(); }
    @Override protected Component title(CatalogClient.Item item) { return Component.literal(item.title()); }
    @Override protected Component creditNotice(CatalogClient.Item item) { return "labymod".equals(item.source()) ? text("non_affiliation") : null; }

    @Override
    protected Component creator(CatalogClient.Item item) {
        if (!item.author().isBlank()) return Component.literal(item.author());
        return "labymod".equals(item.source()) ? text("credit") : CapeTheme.text("ui.community_creator");
    }

    @Override
    protected List<Filter> filters() {
        return List.of(new Filter(text(SOURCES[source]), () -> source = (source + 1) % SOURCES.length),
                new Filter(text(KINDS[kind]), () -> kind = (kind + 1) % KINDS.length),
                new Filter(text(SORTS[sort]), () -> sort = (sort + 1) % SORTS.length));
    }

    @Override
    protected List<Action> actions() {
        CatalogClient.Item item = selected();
        return List.of(new Action(text("wear"), Face.PRIMARY, item != null && previewReady() && !isWearing(item) && !writing, this::wearSelected),
                new Action(text("unwear"), Face.BUTTON, wearing != null && !writing, this::unwear));
    }

    @Override
    protected Action headerAction() {
        return new Action(text("link_short"), Face.BUTTON, true, () -> minecraft.setScreen(new LinkScreen(this)));
    }

    @Override
    protected Component wearing() {
        return Component.translatable("text.quarkos_capes.site.wearing", wearing == null ? text("nothing") : title(wearing));
    }

    @Override
    protected void openedOnline() {
        int request = ++wearingRequest;
        catalog.wearing(item -> {
            if (minecraft.screen != this || request != wearingRequest || offline()) return;
            wearing = item;
            rebuild();
        }, error -> {
            if (minecraft.screen == this && request == wearingRequest && status().isBlank()) setStatus(error);
        });
    }

    @Override
    protected void request(String query, String cursor, Consumer<Results<CatalogClient.Item>> ready, Consumer<String> error) {
        var filters = new CatalogClient.Query(source == 0 ? "" : SOURCES[source], kind == 0 ? "" : KINDS[kind], query,
                SORTS[sort], "", "", cursor, 20);
        catalog.page(filters, result -> {
            Map<String, String> available = result.sources();
            String note = "";
            if (available.containsValue("unavailable")) note = SiteApi.message("source_unavailable");
            else if (available.containsValue("disabled")) note = SiteApi.message("source_disabled");
            else if (result.items().isEmpty() && result.nextCursor() != null) note = SiteApi.message("empty_page");
            ready.accept(new Results<>(result.items(), result.nextCursor(), note, result.total()));
        }, error);
    }

    @Override
    protected void requestThumbnail(CatalogClient.Item item, Consumer<OwnerCape> ready, Consumer<String> error) {
        catalog.image(item, png -> ready.accept(SiteApi.owner(item, png)), error);
    }

    @Override
    protected void requestPreview(CatalogClient.Item item, Consumer<OwnerCape> ready, Consumer<String> error) {
        catalog.detail(item, detail -> requestThumbnail(detail, ready, error), error);
    }

    @Override
    public void removed() {
        wearingRequest++;
        super.removed();
    }
}
