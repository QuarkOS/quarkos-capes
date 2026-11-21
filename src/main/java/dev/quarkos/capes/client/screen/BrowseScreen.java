package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.net.CapeHttp;
import dev.quarkos.capes.client.net.CatalogClient;
import dev.quarkos.capes.client.net.LabyGallery;
import dev.quarkos.capes.client.net.SiteApi;
import dev.quarkos.capes.client.render.CapeDraw;
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
        @Override public void page(CatalogClient.Query query, Consumer<CatalogClient.Page> ready, Consumer<String> error) {
            if (LabyGallery.SOURCE.equals(query.source())) LabyGallery.page(query, ready, error);
            else SiteApi.page(query, ready, error);
        }
        @Override public void detail(CatalogClient.Item item, Consumer<CatalogClient.Item> ready, Consumer<String> error) {
            if (LabyGallery.SOURCE.equals(item.source())) ready.accept(item);
            else SiteApi.detail(item, ready, error);
        }
        @Override public void image(CatalogClient.Item item, Consumer<byte[]> ready, Consumer<String> error) {
            if (LabyGallery.SOURCE.equals(item.source())) LabyGallery.image(item, ready, error);
            else SiteApi.image(item, ready, error);
        }
        @Override public void wearing(Consumer<CatalogClient.Item> ready, Consumer<String> error) { SiteApi.wearing(ready, error); }
        @Override public void wear(CatalogClient.Item item, Consumer<CatalogClient.Item> ready, Consumer<String> error) { SiteApi.wear(item, ready, error); }
        @Override public void unwear(Consumer<String> ready, Consumer<String> error) { SiteApi.unwear(ready, error); }
    };

    // LabyMod is read from laby.net by the client; Featured and Community come from the cape service.
    // The service's mixed source is not offered: it would proxy laby.net through a server.
    private static final List<String> SOURCES = List.of(LabyGallery.SOURCE, CatalogClient.FEATURED, "ours");
    private static final String[] KINDS = {"all_kinds", "cape", "cloak"};
    private static final String[] SORTS = {"popular", "newest", "name"};
    /** Catalog style filter: every cape, or only frame-animated capes (style=frames). */
    private static final String[] STYLES = {"", "frames"};

    private final Catalog catalog;
    private CatalogClient.Item wearing;
    private int source, kind, sort, style, wearingRequest;
    private boolean writing;

    public BrowseScreen(Screen parent) { this(parent, LabyGallery.SOURCE); }

    public BrowseScreen(Screen parent, String source) {
        this(parent, SITE);
        this.source = sourceIndex(source);
    }

    public BrowseScreen(Screen parent, Catalog catalog) {
        super(parent, text("browse_title"));
        this.catalog = catalog;
    }

    public static Component text(String key) { return CapeTheme.text("site." + key); }

    private static int sourceIndex(String value) {
        return value == null ? 0 : Math.max(0, SOURCES.indexOf(value));
    }

    public void search(String query, String sourceValue) {
        source = sourceIndex(sourceValue);
        searchFor(query);
    }

    public boolean isWriting() { return writing; }
    public boolean animatedOnly() { return style == 1; }

    /** Lists only capes whose frames animate; static capes are filtered out by the catalog. */
    public void showAnimatedOnly(boolean on) {
        style = on ? 1 : 0;
        searchFor("");
    }
    public boolean isWearing(CatalogClient.Item item) { return wearing != null && key(wearing).equals(key(item)); }

    public void wearSelected() {
        CatalogClient.Item item = selected();
        if (offline() || item == null || !previewReady() || isWearing(item) || writing || !shareable(item)) return;
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

    private void tryOnSelected() {
        CatalogClient.Item item = selected();
        OwnerCape cape = draft();
        if (offline() || item == null || !tryOnSource(item) || cape == null || writing) return;
        if (!CapeDraw.tryOn(minecraft, cape)) setStatus(SiteApi.message("error"));
    }

    /** LabyMod textures stay on this client: they are never sent to the cape service or shown to others. */
    private static boolean shareable(CatalogClient.Item item) { return !LabyGallery.SOURCE.equals(item.source()); }
    private static boolean tryOnSource(CatalogClient.Item item) {
        return CatalogClient.FEATURED.equals(item.source()) || LabyGallery.SOURCE.equals(item.source());
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
    @Override protected boolean localOnly() { return catalog.offline(); }
    @Override protected CapeHttp.ServiceIssue serviceIssue() {
        return catalog == SITE && !LabyGallery.SOURCE.equals(SOURCES.get(source)) ? CapeHttp.serviceIssue() : null;
    }
    @Override protected boolean equipped(CatalogClient.Item item) { return isWearing(item); }
    @Override protected String key(CatalogClient.Item item) { return item.source() + "/" + item.id(); }
    @Override protected Component title(CatalogClient.Item item) { return Component.literal(item.title()); }
    @Override protected Component creditNotice(CatalogClient.Item item) {
        return LabyGallery.SOURCE.equals(item.source())
                ? Component.translatable("text.quarkos_capes.site.labymod_credit", LabyGallery.creditUrl(item)) : null;
    }

    @Override
    protected Component creator(CatalogClient.Item item) {
        if (!item.author().isBlank()) return Component.literal(item.author());
        return LabyGallery.SOURCE.equals(item.source()) ? text("credit") : CapeTheme.text("ui.community_creator");
    }

    @Override
    protected List<Filter> filters() {
        return List.of(new Filter(text(SOURCES.get(source)),
                        () -> source = (source + 1) % SOURCES.size()),
                new Filter(text(KINDS[kind]), () -> kind = (kind + 1) % KINDS.length),
                new Filter(text(SORTS[sort]), () -> sort = (sort + 1) % SORTS.length),
                new Filter(text(style == 0 ? "all_styles" : "animated_only"), () -> style = (style + 1) % STYLES.length));
    }

    @Override
    protected List<Action> actions() {
        CatalogClient.Item item = selected();
        boolean ready = item != null && previewReady() && !writing;
        var unwear = new Action(text("unwear"), Face.BUTTON, wearing != null && !writing, this::unwear);
        if (item != null && LabyGallery.SOURCE.equals(item.source())) {
            // Local try-on only; Wear would publish the texture to the cape service.
            var tryOn = new Action(text("try_on"), Face.PRIMARY, ready && minecraft.player != null, this::tryOnSelected);
            return List.of(tryOn, unwear);
        }
        var wear = new Action(text("wear"), Face.PRIMARY, ready && !isWearing(item), this::wearSelected);
        if (item != null && CatalogClient.FEATURED.equals(item.source())) {
            var tryOn = new Action(text("try_on"), Face.BUTTON, ready && minecraft.player != null, this::tryOnSelected);
            return List.of(wear, tryOn, unwear);
        }
        return List.of(wear, unwear);
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
        if (catalog == SITE && minecraft.player == null) return;
        int request = ++wearingRequest;
        catalog.wearing(item -> {
            if (minecraft.screen != this || request != wearingRequest || offline()) return;
            wearing = item;
            rebuild();
        }, error -> {
            if (minecraft.screen == this && request == wearingRequest && !offline() && status().isBlank()
                    && !error.equals(SiteApi.message("signin"))) setStatus(error);
        });
    }

    @Override
    protected void request(String query, String cursor, Consumer<Results<CatalogClient.Item>> ready, Consumer<String> error) {
        var filters = new CatalogClient.Query(SOURCES.get(source), kind == 0 ? "" : KINDS[kind], query,
                SORTS[sort], STYLES[style], "", cursor, 20);
        catalog.page(filters, result -> {
            Map<String, String> available = result.sources();
            String note = "";
            if (LabyGallery.SOURCE.equals(filters.source()) && "cape".equals(filters.kind())) note = SiteApi.message("labymod_cloaks_only");
            else if (LabyGallery.SOURCE.equals(filters.source()) && !filters.style().isEmpty()) note = SiteApi.message("labymod_static_only");
            else if (available.containsValue("unavailable")) note = SiteApi.message("source_unavailable");
            else if ("disabled".equals(available.get(CatalogClient.FEATURED))) note = SiteApi.message("featured_empty");
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
