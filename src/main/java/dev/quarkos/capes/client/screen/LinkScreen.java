package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.net.SiteApi;
import dev.quarkos.capes.client.screen.ui.CapeGridLayout.Rect;
import dev.quarkos.capes.client.screen.ui.CapeTheme;
import dev.quarkos.capes.client.screen.ui.CapeTheme.Face;
import dev.quarkos.capes.client.screen.ui.CapeUiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

import static dev.quarkos.capes.client.screen.BrowseScreen.text;

public final class LinkScreen extends CapeUiScreen {
    private final Screen parent;
    private Rect panel;
    private EditBox code;
    private CapeTheme.FlatButton claim;
    private String value = "", note = "";
    private boolean busy;

    public LinkScreen(Screen parent) {
        super(text("link"));
        this.parent = parent;
    }

    public boolean isBusy() { return busy; }
    public String status() { return note; }

    @Override
    protected void init() {
        resetWidgets();
        int w = Math.min(274, frame.width());
        int y = Math.max(frame.y(), frame.centerY() - 62);
        panel = new Rect(frame.centerX() - w / 2, y, w, 123);
        Rect area = new Rect(panel.x() + 12, panel.y() + 65, panel.width() - 24, CapeTheme.CONTROL);
        code = addRenderableWidget(CapeTheme.field(font, text("code"), area));
        code.setMaxLength(8);
        code.setValue(value);
        Rect actions = new Rect(area.x(), area.bottom() + 7, area.width(), CapeTheme.CONTROL);
        button(text("back"), actions.cell(0, 2, 6), Face.BUTTON, this::onClose);
        claim = button(text("claim"), actions.cell(1, 2, 6), Face.PRIMARY, this::claim);
        code.setResponder(next -> { value = next; updateClaim(); });
        setInitialFocus(code);
        updateClaim();
    }

    private void claim() {
        if (!claim.active) return;
        busy = true;
        note = SiteApi.message("working");
        updateClaim();
        SiteApi.claim(value, this::done, this::done);
    }

    private void updateClaim() {
        claim.active = !busy && !value.isBlank();
    }

    private void done(String message) {
        busy = false;
        note = message;
        if (minecraft.screen == this) updateClaim();
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
        if (!code.isFocused() || (key != 257 && key != 335)) return false;
        claim();
        return true;
    }

    @Override
    protected void renderPanels(GuiGraphics graphics) {
        CapeTheme.panel(graphics, panel);
        int x = panel.x() + 12, w = panel.width() - 24;
        CapeTheme.title(graphics, font, getTitle(), x, panel.y() + 13, w);
        CapeTheme.label(graphics, font, text("code_help"), x, panel.y() + 31, w, CapeTheme.MUTED);
        CapeTheme.label(graphics, font, text("code"), x, panel.y() + 52, w, CapeTheme.TEXT);
        CapeTheme.line(graphics, font, note, x, panel.bottom() + 8, w, CapeTheme.MUTED);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
