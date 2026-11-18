package dev.quarkos.capes.client.screen.ui;

import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.screen.ui.CapeGridLayout.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public abstract class CapeUiScreen extends Screen {
    protected Rect frame;
    private final CapePreview.Rotation rotation = new CapePreview.Rotation();
    private Rect previewBounds;

    protected CapeUiScreen(Component title) {
        super(title);
    }

    protected void resetWidgets() {
        setFocused(null);
        setDragging(false);
        clearWidgets();
        rotation.release();
        previewBounds = null;
        CapePreview.clearDraft();
        frame = CapeGridLayout.frame(width, height);
    }

    protected CapeTheme.FlatButton button(Component label, Rect area, CapeTheme.Face face, Runnable action) {
        return addRenderableWidget(CapeTheme.button(label, area, face, action));
    }

    protected void preview(Rect area, Supplier<OwnerCape> cape) {
        preview(area, cape, () -> false);
    }

    protected void preview(Rect area, Supplier<OwnerCape> cape, BooleanSupplier loading) {
        if (area.height() < 24 || area.width() < 36) return;
        previewBounds = area;
        addRenderableWidget(new CapePreview(area, rotation, cape, loading));
    }

    public Rect previewBounds() {
        return previewBounds;
    }

    @Override
    public void removed() {
        rotation.reset();
        CapePreview.clearDraft();
        super.removed();
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (event.button() == 0) rotation.release();
        return super.mouseReleased(event);
    }
    //?} else {
    /*@Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0) rotation.release();
        return super.mouseReleased(x, y, button);
    }
    *///?}

    //? if <1.20.2 {
    /*@Override
    public void renderBackground(GuiGraphics graphics) {
        CapeTheme.background(graphics, width, height);
    }
    *///?} else {
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        CapeTheme.background(graphics, width, height);
    }
    //?}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        CapeTheme.background(graphics, width, height);
        renderPanels(graphics);
        super.render(graphics, mouseX, mouseY, delta);
    }

    protected abstract void renderPanels(GuiGraphics graphics);
}
