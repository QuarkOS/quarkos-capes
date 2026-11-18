package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.net.CapeHttp;
import dev.quarkos.capes.client.net.ServiceResponse;
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
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import static dev.quarkos.capes.client.screen.ui.CapeTheme.text;

/** Grid, search, and details shared by the website catalog and the community gallery. */
abstract class CapeBrowserScreen<T> extends CapeUiScreen {
    protected record Results<T>(List<T> items, String next, String status, Integer total) { }
    protected record Filter(Component label, Runnable cycle) { }
    protected record Action(Component label, Face face, boolean enabled, Runnable press) { }
    private record PageStart(String cursor, int offset) { }

    private final Screen parent;
    private final CapeSelection selection = new CapeSelection();
    private final ArrayDeque<PageStart> history = new ArrayDeque<>();
    private final Map<String, Art> art = new HashMap<>();
    private final Set<String> requested = new HashSet<>();
    private record Art(OwnerCape cape, CapeTextures.Image image) { }

    private CapeGridLayout grid;
    private Rect body, details, statusLine;
    private EditBox search;
    private int titleWidth, emptyY, page, offset, generation;
    private boolean compact, initialized, returnToLastPage, openedOnline;
    private CapeHttp.ServiceIssue displayedIssue;
    private Integer total;
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

    protected boolean localOnly() { return ClientSettings.get().localOnly; }
    protected CapeHttp.ServiceIssue serviceIssue() { return null; }
    protected final boolean offline() { return localOnly() || serviceIssue() != null; }
    protected Action headerAction() { return null; }
    protected Component creditNotice(T item) { return null; }
    protected void openedOnline() { }
    protected String namespace() { return "gallery"; }
    public final List<T> items() { return items; }
    public final T selected() { return selected; }
    public final String status() { return status; }
    public final boolean isLoading() { return loading; }
    public final boolean previewReady() { return draft != null; }
    public final String unavailableUrl() { return displayedIssue == null ? "" : displayedIssue.url(); }
    public final Component emptyHeading() {
        return text(displayedIssue != null ? "site.unreachable"
                : localOnly() ? "site.offline_title" : loading ? "status.loading" : "ui.no_capes");
    }
    protected final OwnerCape draft() { return draft; }

    public final Component pageLabel() {
        if (items.isEmpty() || grid.capacity() == 0) return null;
        int first = offset + page * grid.capacity() + 1;
        int last = offset + Math.min(items.size(), (page + 1) * grid.capacity());
        if (total == null && next != null) {
            return Component.translatable("text.quarkos_capes.ui.range_open", first, last);
        }
        int count = total == null ? offset + items.size() : total;
        return Component.translatable("text.quarkos_capes.ui.range", first, last, count);
    }

    @Override
    protected final void init() {
        if (initialized) {
            rebuild();
            return;
        }
        initialized = true;
        loadPage();
    }

    @Override
    public void tick() {
        if (!Objects.equals(displayedIssue, serviceIssue())) submitSearch();
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
        offset = 0;
        returnToLastPage = false;
        loadPage();
    }

    protected final void setStatus(String value) {
        status = value;
    }

    protected final void rebuild() {
        boolean focused = search != null && search.isFocused();
        resetWidgets();

        boolean offline = displayedIssue != null || localOnly();
        int sideWidth = offline ? 0 : CapeGridLayout.previewWidth(frame);
        int footerY = frame.bottom() - CapeTheme.CONTROL;
        List<Filter> filters = filters();
        boolean regularSingleRow = frame.width() >= 512 || filters.isEmpty();
        int regularTop = frame.y() + (regularSingleRow ? 54 : 78);
        Rect regularBody = CapeGridLayout.gridPanel(frame, regularTop, footerY - 15, sideWidth);
        compact = sideWidth > 0 && CapeGridLayout.compactBrowser(frame, regularBody.inset(6));
        Action headerAction = headerAction();
        int backWidth = Math.min(52, frame.width() / 4);
        int headerActionWidth = headerAction == null ? 0 : Math.min(105, frame.width() / 3);
        int headerLeft = frame.right() - backWidth - (headerAction == null ? 0 : headerActionWidth + 5);
        button(text("site.back"), new Rect(frame.right() - backWidth, frame.y(), backWidth, CapeTheme.CONTROL), Face.BUTTON, this::onClose);
        if (headerAction != null) {
            addAction(headerAction, new Rect(headerLeft, frame.y(), headerActionWidth, CapeTheme.CONTROL));
        }
        if (displayedIssue != null) {
            titleWidth = headerLeft - frame.x() - 7;
            body = new Rect(frame.x(), frame.y() + 26, frame.width(), frame.height() - 26);
            details = new Rect(0, 0, 0, 0);
            statusLine = new Rect(0, 0, 0, 0);
            search = null;
            emptyY = body.centerY() - 28;
            emptyAction("site.retry_action", wrongService() ? 39 : 28, this::retryService);
            return;
        }
        // Move paging into the header when two card rows need the full body height.
        Rect headerPager = compact ? new Rect(headerLeft - 180, frame.y(), 174, CapeTheme.CONTROL) : null;
        titleWidth = (compact ? headerPager.x() : headerLeft) - frame.x() - 7;

        int controlsY = frame.y() + (compact ? 24 : 27);
        boolean singleRow = compact || regularSingleRow;
        int filterLimit = compact ? frame.width() - 120 : frame.width() * 3 / 5;
        int filterWidth = singleRow ? Math.min(filterLimit, filters.size() * 112) : frame.width();
        int searchWidth = frame.width() - (singleRow && filterWidth > 0 ? filterWidth + 8 : 0);
        int submitWidth = Math.min(66, Math.max(compact ? 48 : 0, searchWidth / 3));
        search = addRenderableWidget(CapeTheme.field(font, text("site.search"),
                new Rect(frame.x(), controlsY, searchWidth - submitWidth - 4, CapeTheme.CONTROL)));
        search.setMaxLength(120);
        search.setValue(input);
        updateSuggestion();
        search.setResponder(value -> { input = value; updateSuggestion(); });
        if (focused && !offline) setInitialFocus(search);
        search.active = !offline;
        button(text("site.search"), new Rect(frame.x() + searchWidth - submitWidth, controlsY, submitWidth, CapeTheme.CONTROL),
                Face.BUTTON, this::submitSearch).active = !offline && !loading;
        Rect filterRow = new Rect(frame.right() - filterWidth, controlsY + (singleRow ? 0 : 24), filterWidth, CapeTheme.CONTROL);
        for (int i = 0; i < filters.size(); i++) {
            Filter filter = filters.get(i);
            button(filter.label(), filterRow.cell(i, filters.size(), 4), Face.CYCLE, () -> {
                filter.cycle().run();
                submitSearch();
            }).active = !offline;
        }

        body = compact ? CapeGridLayout.gridPanel(frame, controlsY + 24, frame.bottom(), sideWidth) : regularBody;
        details = new Rect(frame.right() - sideWidth, body.y(), sideWidth, frame.bottom() - body.y());
        statusLine = new Rect(body.x(), footerY - 12, body.width(), font.lineHeight);
        grid = CapeGridLayout.fit(body.inset(compact ? 4 : 6));
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

        List<Action> actions = offline ? List.of() : actions();
        int compactActionWidth = sideWidth == 0 && !actions.isEmpty() ? frame.width() * 52 / 100 : 0;
        int pagerWidth = Math.min(204, body.width() - (compactActionWidth > 0 ? compactActionWidth + 6 : 0));
        Rect pager = compact ? headerPager : new Rect(body.x(), footerY, pagerWidth, CapeTheme.CONTROL);
        button(text("site.previous"), pager.cell(0, 3, 4), Face.BUTTON, () -> turn(-1)).active = !loading && (page > 0 || !history.isEmpty());
        button(text("site.next"), pager.cell(1, 3, 4), Face.BUTTON, () -> turn(1)).active = !loading && (page + 1 < grid.pages(items.size()) || next != null);
        if (!offline) {
            button(text("site.retry_button"), pager.cell(2, 3, 4), Face.BUTTON, this::loadPage).active = !loading;
        }

        if (sideWidth > 0) {
            int inset = 7;
            int previewWidth = details.width() - 2 * inset;
            int chrome = 18 + 39 + (actions.isEmpty() ? 0 : 25) + (compact ? 10 : 0);
            int previewHeight = CapeGridLayout.previewHeight(previewWidth, details.height() - chrome);
            details = new Rect(details.x(), details.y(), details.width(), chrome + previewHeight);
            Rect viewport = new Rect(details.x() + inset, details.y() + 18, previewWidth, previewHeight);
            preview(viewport, () -> draft, () -> loadingCape);
            if (compact) statusLine = new Rect(viewport.x(), viewport.bottom() + 36, previewWidth, font.lineHeight);
            addActions(actions, new Rect(details.x() + inset, details.bottom() - CapeTheme.CONTROL - 6, previewWidth, CapeTheme.CONTROL));
        } else if (compactActionWidth > 0) {
            addActions(actions, new Rect(frame.right() - compactActionWidth, footerY, compactActionWidth, CapeTheme.CONTROL));
        }

        emptyY = body.centerY() - (offline ? 24 : 4);
        if (offline) emptyAction("site.go_online", 28, this::goOnline);
    }

    private void emptyAction(String label, int offset, Runnable action) {
        int w = Math.min(112, body.width() - 16);
        int y = Math.min(body.bottom() - CapeTheme.CONTROL - 4, emptyY + offset);
        button(text(label), new Rect(body.centerX() - w / 2, Math.max(body.y() + 3, y), w, CapeTheme.CONTROL),
                Face.PRIMARY, action);
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
    }

    private void retryService() {
        CapeHttp.retryService();
        submitSearch();
    }

    private void loadPage() {
        displayedIssue = serviceIssue();
        if (displayedIssue != null) openedOnline = false;
        int ticket = ++generation;
        clearArt();
        selection.select(null);
        selected = null;
        draft = null;
        loadingCape = false;
        items = List.of();
        next = null;
        total = null;
        status = "";
        loading = !offline();
        rebuild();
        if (!loading) return;
        request(query, cursor, result -> {
            if (!current(ticket)) return;
            items = result.items();
            next = result.next() == null || result.next().isBlank() || result.next().equals(cursor) ? null : result.next();
            total = result.total();
            status = result.status();
            loading = false;
            rebuild();
            if (!openedOnline) {
                openedOnline = true;
                openedOnline();
            }
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
            history.addLast(new PageStart(cursor, offset));
            offset += items.size();
            cursor = next;
            page = 0;
            loadPage();
        } else if (direction < 0 && !history.isEmpty()) {
            PageStart previous = history.removeLast();
            cursor = previous.cursor();
            offset = previous.offset();
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
        if (offline() || !body.contains(x, y) || vertical == 0) return false;
        turn(vertical > 0 ? -1 : 1);
        return true;
    }

    //? if >=1.21.9 {
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return submitKey(event.isConfirmation()) || super.keyPressed(event);
    }
    //?} else {
    /*@Override public boolean keyPressed(int key, int scan, int modifiers) {
        return submitKey(key == 257 || key == 335) || super.keyPressed(key, scan, modifiers);
    }
    *///?}

    private boolean submitKey(boolean confirm) {
        if (!confirm || search == null || !search.isFocused() || offline()) return false;
        submitSearch();
        return true;
    }

    @Override
    protected final void renderPanels(GuiGraphics graphics) {
        CapeTheme.title(graphics, font, getTitle(), frame.x(), frame.y() + 6, titleWidth);
        CapeTheme.panel(graphics, body);
        if (items.isEmpty()) drawEmpty(graphics);
        Component range = pageLabel();
        if (details.width() > 0) {
            CapeTheme.panel(graphics, details);
            Component heading = selected == null ? text("ui.preview") : title(selected);
            int x = details.x() + 7;
            int w = details.width() - 14;
            int headingWidth = w;
            if (compact && range != null) {
                int rangeWidth = Math.min(font.width(range), w / 2);
                CapeTheme.label(graphics, font, range, x + w - rangeWidth, details.y() + 6, rangeWidth, CapeTheme.MUTED);
                headingWidth -= rangeWidth + 6;
            }
            CapeTheme.label(graphics, font, heading, x, details.y() + 6, headingWidth, CapeTheme.TEXT);
            int y = details.bottom() - 49 - (compact ? 10 : 0);
            if (previewBounds() != null && draft != null && minecraft.player != null) {
                CapeTheme.label(graphics, font, text("ui.rotate"), x, y - 12, w, CapeTheme.MUTED);
            }
            if (selected != null) CapeTheme.label(graphics, font, creator(selected), x, y, w, CapeTheme.MUTED);
            CapeTheme.label(graphics, font, wearing(), x, y + 11, w, CapeTheme.TEXT);
        }

        int statusX = statusLine.x();
        if (!compact && range != null) {
            CapeTheme.label(graphics, font, range, statusX, statusLine.y(), statusLine.width(), CapeTheme.MUTED);
            statusX += font.width(range) + 8;
        }
        Component notice = status.isBlank() ? (selected == null ? null : creditNotice(selected)) : Component.literal(status);
        if (notice == null && details.width() == 0 && !offline()) notice = wearing();
        if (notice != null) {
            CapeTheme.label(graphics, font, notice, statusX, statusLine.y(), statusLine.right() - statusX, CapeTheme.MUTED);
        }
    }

    private void drawEmpty(GuiGraphics graphics) {
        boolean offline = displayedIssue != null || localOnly();
        int y = offline ? Math.max(body.y() + 3, emptyY) : emptyY;
        CapeTheme.centered(graphics, font, emptyHeading(), body.centerX(), y, body.width() - 12, CapeTheme.TEXT);
        if (offline && body.height() >= 63) {
            Component detail = displayedIssue == null ? text("site.offline_help") : Component.literal(displayedIssue.url());
            CapeTheme.centered(graphics, font, detail, body.centerX(), y + 12, body.width() - 12, CapeTheme.MUTED);
            if (wrongService()) {
                CapeTheme.centered(graphics, font, text("site.wrong_service"), body.centerX(), y + 23, body.width() - 12, CapeTheme.MUTED);
            }
        }
    }

    private boolean wrongService() {
        return displayedIssue != null && displayedIssue.reason() == ServiceResponse.OfflineReason.WRONG_SERVICE;
    }

    @Override
    public void removed() {
        generation++;
        selection.select(null);
        clearArt();
        initialized = false;
        openedOnline = false;
        super.removed();
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
