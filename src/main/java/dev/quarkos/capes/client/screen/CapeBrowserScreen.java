package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.render.CapeTextures;
import dev.quarkos.capes.client.screen.ui.CapeGridLayout;
import dev.quarkos.capes.client.screen.ui.CapeGridLayout.Rect;
import dev.quarkos.capes.client.screen.ui.CapeSelection;
import dev.quarkos.capes.client.screen.ui.CapeTheme;
import dev.quarkos.capes.client.screen.ui.CapeTheme.Face;
import dev.quarkos.capes.client.screen.ui.CapeUiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static dev.quarkos.capes.client.screen.ui.CapeTheme.text;

/** Grid, search, and details shared by the website catalog and the community gallery. */
abstract class CapeBrowserScreen<T> extends CapeUiScreen {
    protected record Results<T>(List<T> items, String next, String status) { }
    protected record Filter(Component label, Runnable cycle) { }
    protected record Action(Component label, Face face, boolean enabled, Runnable press) { }

    private final Screen parent;
    private final CapeSelection selection = new CapeSelection();
    private final ArrayDeque<String> history = new ArrayDeque<>();
    private final Map<String, Art> art = new HashMap<>();
    private final Set<String> requested = new HashSet<>();
    private record Art(OwnerCape cape, CapeTextures.Image image) { }

    private CapeGridLayout grid;
    private Rect body, details;
    private EditBox search;
    private int titleWidth, statusY, emptyY, page, generation;
    private boolean initialized, returnToLastPage;
    private String input = "", query = "", cursor = "", next;
    private String status = "";
    private List<T> items = List.of();
    private T selected;
    private OwnerCape draft;
    private boolean loading, loadingCape;

    protected CapeBrowserScreen(Screen parent, Component title) {
        super(title);
        this.parent = parent;
    }

    protected abstract String key(T item);
    protected abstract Component title(T item);
    protected abstract Component creator(T item);
    protected abstract List<Filter> filters();
    protected abstract List<Action> actions();
    protected abstract Component wearing();
    protected abstract boolean equipped(T item);
    protected abstract void request(String query, String cursor, Consumer<Results<T>> ready, Consumer<String> error);
    protected abstract void requestPreview(T item, Consumer<OwnerCape> ready, Consumer<String> error);

    protected void requestThumbnail(T item, Consumer<OwnerCape> ready, Consumer<String> error) {
        requestPreview(item, ready, error);
    }

    protected boolean offline() { return ClientSettings.get().localOnly; }
    protected Action headerAction() { return null; }
    protected Component creditNotice(T item) { return null; }
    protected void openedOnline() { }
    protected String namespace() { return "gallery"; }
    public final List<T> items() { return items; }
    public final T selected() { return selected; }
    public final String status() { return status; }
    public final boolean isLoading() { return loading; }
    public final boolean previewReady() { return draft != null; }
    protected final OwnerCape draft() { return draft; }

    @Override
    protected final void init() {
        if (initialized) {
            rebuild();
            return;
        }
        initialized = true;
        loadPage();
        if (!offline()) openedOnline();
    }

    protected final void searchFor(String value) {
        input = value;
        if (minecraft == null || minecraft.screen != this) {
            query = input.trim();
            return;
        }
        submitSearch();
    }

    private void submitSearch() {
        query = input.trim();
        history.clear();
        cursor = "";
        page = 0;
        returnToLastPage = false;
        loadPage();
    }

    protected final void setStatus(String value) {
        status = value;
    }

    protected final void rebuild() {
        boolean focused = search != null && search.isFocused();
        resetWidgets();

        Action headerAction = headerAction();
        int backWidth = Math.min(52, frame.width() / 4);
        int headerActionWidth = headerAction == null ? 0 : Math.min(105, frame.width() / 3);
        button(text("site.back"), new Rect(frame.right() - backWidth, frame.y(), backWidth, CapeTheme.CONTROL), Face.BUTTON, this::onClose);
        if (headerAction != null) {
            addAction(headerAction, new Rect(frame.right() - backWidth - headerActionWidth - 5, frame.y(), headerActionWidth, CapeTheme.CONTROL));
        }
        titleWidth = frame.width() - backWidth - headerActionWidth - 12;

        int controlsY = frame.y() + 27;
        List<Filter> filters = filters();
        boolean singleRow = frame.width() >= 512 || filters.isEmpty();
        int filterWidth = singleRow ? Math.min(frame.width() * 3 / 5, filters.size() * 112) : frame.width();
        int searchWidth = frame.width() - (singleRow && filterWidth > 0 ? filterWidth + 8 : 0);
        int submitWidth = Math.min(66, searchWidth / 3);
        search = addRenderableWidget(CapeTheme.field(font, text("site.search"),
                new Rect(frame.x(), controlsY, searchWidth - submitWidth - 4, CapeTheme.CONTROL)));
        search.setMaxLength(120);
        search.setValue(input);
        updateSuggestion();
        search.setResponder(value -> { input = value; updateSuggestion(); });
        if (focused && !offline()) setInitialFocus(search);
        search.active = !offline();
        button(text("site.search"), new Rect(frame.x() + searchWidth - submitWidth, controlsY, submitWidth, CapeTheme.CONTROL),
                Face.BUTTON, this::submitSearch).active = !offline() && !loading;
        Rect filterRow = new Rect(frame.right() - filterWidth, controlsY + (singleRow ? 0 : 24), filterWidth, CapeTheme.CONTROL);
        for (int i = 0; i < filters.size(); i++) {
            Filter filter = filters.get(i);
            button(filter.label(), filterRow.cell(i, filters.size(), 4), Face.CYCLE, () -> {
                filter.cycle().run();
                submitSearch();
            }).active = !offline();
        }

        int top = controlsY + (singleRow ? 27 : 51);
        int footerY = frame.bottom() - CapeTheme.CONTROL;
        statusY = footerY - 12;
        int sideWidth = offline() ? 0 : CapeGridLayout.previewWidth(frame);
        int bodyWidth = frame.width() - (sideWidth == 0 ? 0 : sideWidth + 10);
        body = new Rect(frame.x(), top, bodyWidth, Math.max(0, statusY - top - 3));
        details = new Rect(frame.right() - sideWidth, top, sideWidth, frame.bottom() - top);
        int cardHeight = frame.height() < 205 ? 56 : frame.height() < 300 ? 60 : 95;
        grid = CapeGridLayout.fit(body.inset(6), 75, cardHeight, 6);
        if (returnToLastPage && !loading) {
            page = grid.pages(items.size()) - 1;
            returnToLastPage = false;
        }
        page = grid.clampPage(page, items.size());
        int start = page * grid.capacity();
        for (int i = start; i < Math.min(start + grid.capacity(), items.size()); i++) {
            T item = items.get(i);
            String id = key(item);
            addRenderableWidget(CapeTheme.card(title(item), creator(item), grid.card(i - start),
                    (graphics, area) -> {
                        Art image = art.get(id);
                        if (image != null) CapeThumbs.draw(graphics, area, image.image(), image.cape());
                    }, () -> selection.selected(id), () -> equipped(item), () -> select(item)));
            loadThumbnail(item);
        }

        List<Action> actions = offline() ? List.of() : actions();
        int compactActionWidth = sideWidth == 0 && !actions.isEmpty() ? frame.width() * 52 / 100 : 0;
        Rect pager = new Rect(body.x(), footerY, Math.min(204, body.width() - (compactActionWidth > 0 ? compactActionWidth + 6 : 0)), CapeTheme.CONTROL);
        button(text("site.previous"), pager.cell(0, 3, 4), Face.BUTTON, () -> turn(-1)).active = !loading && (page > 0 || !history.isEmpty());
        button(text("site.next"), pager.cell(1, 3, 4), Face.BUTTON, () -> turn(1)).active = !loading && (page + 1 < grid.pages(items.size()) || next != null);
        button(text("site.retry_button"), pager.cell(2, 3, 4), Face.BUTTON, this::loadPage).active = !loading && !offline();

        if (sideWidth > 0) {
            int inset = 7;
            int metadata = 39;
            preview(new Rect(details.x() + inset, details.y() + 18, details.width() - 2 * inset,
                    details.height() - metadata - 18 - (actions.isEmpty() ? 0 : 25)), () -> draft, () -> loadingCape);
            addActions(actions, new Rect(details.x() + inset, footerY - 6, details.width() - 2 * inset, CapeTheme.CONTROL));
        } else if (compactActionWidth > 0) {
            addActions(actions, new Rect(frame.right() - compactActionWidth, footerY, compactActionWidth, CapeTheme.CONTROL));
        }

        emptyY = body.centerY() - (offline() ? 24 : 4);
        if (offline()) {
            int w = Math.min(112, body.width() - 16);
            int y = Math.min(body.bottom() - CapeTheme.CONTROL - 4, emptyY + 28);
            button(text("site.go_online"), new Rect(body.centerX() - w / 2, Math.max(body.y() + 3, y), w, CapeTheme.CONTROL),
                    Face.PRIMARY, this::goOnline);
        }
    }

    private void addAction(Action action, Rect rect) {
        button(action.label(), rect, action.face(), action.press()).active = action.enabled();
    }

    private void addActions(List<Action> actions, Rect rect) {
        for (int i = 0; i < actions.size(); i++) addAction(actions.get(i), rect.cell(i, actions.size(), 4));
    }

    private void updateSuggestion() {
        search.setSuggestion(input.isEmpty() ? CapeTheme.ellipsis(font, text("search.placeholder").getString(), search.getWidth() - 2) : null);
    }

    private void goOnline() {
        CapeBoard.localOnly(false);
        CapeBoard.save(ignored -> { });
        submitSearch();
        openedOnline();
    }

    private void loadPage() {
        int ticket = ++generation;
        clearArt();
        selection.select(null);
        selected = null;
        draft = null;
        loadingCape = false;
        items = List.of();
        next = null;
        status = "";
        loading = !offline();
        rebuild();
        if (!loading) return;
        request(query, cursor, result -> {
            if (!current(ticket)) return;
            items = result.items();
            next = result.next() == null || result.next().isBlank() || result.next().equals(cursor) ? null : result.next();
            status = result.status();
            loading = false;
            rebuild();
        }, error -> {
            if (!current(ticket)) return;
            loading = false;
            status = error;
            rebuild();
        });
    }

    private void turn(int direction) {
        if (loading) return;
        int wanted = page + direction;
        if (wanted >= 0 && wanted < grid.pages(items.size())) {
            page = wanted;
            rebuild();
        } else if (direction > 0 && next != null) {
            history.addLast(cursor);
            cursor = next;
            page = 0;
            loadPage();
        } else if (direction < 0 && !history.isEmpty()) {
            cursor = history.removeLast();
            returnToLastPage = true;
            loadPage();
        }
    }

    private boolean current(int ticket) {
        return minecraft.screen == this && ticket == generation && !offline();
    }

    private void loadThumbnail(T item) {
        String id = key(item);
        if (!requested.add(id)) return;
        int ticket = generation;
        requestThumbnail(item, cape -> {
            if (!current(ticket) || cape.png.length == 0) return;
            var image = CapeTextures.upload(textureKey(id), cape.png, false, false);
            if (image != null) art.put(id, new Art(cape, image));
        }, ignored -> { });
    }

    protected final void select(T item) {
        if (offline()) return;
        String id = key(item);
        if (selection.selected(id) && (draft != null || loadingCape)) return;
        long request = selection.select(id);
        selected = item;
        draft = null;
        status = "";
        loadingCape = true;
        rebuild();
        requestPreview(item, cape -> {
            if (minecraft.screen != this || !selection.current(request)) return;
            draft = cape;
            loadingCape = false;
            rebuild();
        }, error -> {
            if (minecraft.screen != this || !selection.current(request)) return;
            status = error;
            loadingCape = false;
            rebuild();
        });
    }

    public final void selectFirst() {
        if (!items.isEmpty()) select(items.getFirst());
    }

    private String textureKey(String id) {
        return namespace() + "/card/" + id;
    }

    private void clearArt() {
        art.keySet().forEach(id -> CapeTextures.clear(textureKey(id)));
        art.clear();
        requested.clear();
    }

    //? if <1.21 {
    /*@Override public boolean mouseScrolled(double x, double y, double vertical) {
        return scrollGrid(x, y, vertical) || super.mouseScrolled(x, y, vertical);
    }
    *///?} else {
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return scrollGrid(x, y, vertical) || super.mouseScrolled(x, y, horizontal, vertical);
    }
    //?}

    private boolean scrollGrid(double x, double y, double vertical) {
        if (!body.contains(x, y) || vertical == 0) return false;
        turn(vertical > 0 ? -1 : 1);
        return true;
    }

    //? if >=1.21.9 {
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return submitKey(event.key()) || super.keyPressed(event);
    }
    //?} else {
    /*@Override public boolean keyPressed(int key, int scan, int modifiers) {
        return submitKey(key) || super.keyPressed(key, scan, modifiers);
    }
    *///?}

    private boolean submitKey(int key) {
        if (search == null || !search.isFocused() || offline() || (key != 257 && key != 335)) return false;
        submitSearch();
        return true;
    }

    @Override
    protected final void renderPanels(GuiGraphics graphics) {
        CapeTheme.title(graphics, font, getTitle(), frame.x(), frame.y() + 6, titleWidth);
        CapeTheme.panel(graphics, body);
        if (items.isEmpty()) drawEmpty(graphics);
        if (details.width() > 0) {
            CapeTheme.panel(graphics, details);
            Component heading = selected == null ? text("ui.preview") : title(selected);
            CapeTheme.label(graphics, font, heading, details.x() + 7, details.y() + 6, details.width() - 14, CapeTheme.TEXT);
            if (!offline()) {
                int y = details.bottom() - 49;
                if (previewBounds() != null && draft != null && minecraft.player != null) {
                    CapeTheme.label(graphics, font, text("ui.rotate"), details.x() + 7, y - 12, details.width() - 14, CapeTheme.MUTED);
                }
                if (selected != null) CapeTheme.label(graphics, font, creator(selected), details.x() + 7, y, details.width() - 14, CapeTheme.MUTED);
                CapeTheme.label(graphics, font, wearing(), details.x() + 7, y + 11, details.width() - 14, CapeTheme.TEXT);
            }
        }

        if (!status.isBlank()) CapeTheme.line(graphics, font, status, body.x(), statusY, body.width(), CapeTheme.MUTED);
        else if (selected != null && creditNotice(selected) != null) CapeTheme.label(graphics, font, creditNotice(selected), body.x(), statusY, body.width(), CapeTheme.MUTED);
        else if (details.width() == 0 && !offline()) CapeTheme.label(graphics, font, wearing(), body.x(), statusY, body.width(), CapeTheme.MUTED);
        else if (grid.pages(items.size()) > 1 || next != null || !history.isEmpty()) {
            int first = items.isEmpty() ? 0 : page * grid.capacity() + 1;
            int last = Math.min(items.size(), (page + 1) * grid.capacity());
            CapeTheme.label(graphics, font, Component.translatable("text.quarkos_capes.ui.range", first, last, items.size()),
                    body.x(), statusY, body.width(), CapeTheme.MUTED);
        }
    }

    private void drawEmpty(GuiGraphics graphics) {
        Component heading = text(offline() ? "site.offline_title" : loading ? "status.loading" : "ui.no_capes");
        int y = offline() ? Math.max(body.y() + 3, emptyY) : emptyY;
        CapeTheme.centered(graphics, font, heading, body.centerX(), y, body.width() - 12, CapeTheme.TEXT);
        if (offline() && body.height() >= 63) {
            CapeTheme.centered(graphics, font, text("site.offline_help"), body.centerX(), y + 12, body.width() - 12, CapeTheme.MUTED);
        }
    }

    @Override
    public void removed() {
        generation++;
        selection.select(null);
        clearArt();
        initialized = false;
        super.removed();
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
