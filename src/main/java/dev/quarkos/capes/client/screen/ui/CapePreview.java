package dev.quarkos.capes.client.screen.ui;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.screen.ui.CapeGridLayout.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class CapePreview extends AbstractWidget {
    private final Rect area;
    private final Rotation rotation;
    private final Supplier<OwnerCape> cape;
    private final BooleanSupplier loading;

    CapePreview(Rect area, Rotation rotation, Supplier<OwnerCape> cape, BooleanSupplier loading) {
        super(area.x(), area.y(), area.width(), area.height(), CapeTheme.text("ui.preview"));
        this.area = area;
        this.rotation = rotation;
        this.cape = cape;
        this.loading = loading;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        Minecraft client = Minecraft.getInstance();
        OwnerCape shown = cape.get();
        active = client.player != null && shown != null;
        CapeTheme.box(graphics, area, CapeTheme.INSET, isFocused() ? CapeTheme.ACCENT : CapeTheme.BORDER);
        if (!active) {
            clearDraft();
            String hint = loading.getAsBoolean() ? "status.loading" : shown == null ? "site.select_preview" : "ui.preview_world";
            CapeTheme.centered(graphics, client.font, CapeTheme.text(hint), area.centerX(), area.centerY() - 4, area.width() - 12, CapeTheme.MUTED);
            return;
        }

        CapeBook.preview = true;
        CapeBook.draft = shown;
        Rect model = area.inset(5);
        McBridge.preview(graphics, model.x(), model.y(), model.right(), model.bottom(), client.player, rotation.angle());
    }

    static void clearDraft() {
        CapeBook.preview = false;
        CapeBook.draft = null;
    }

    //? if >=1.21.9 {
    @Override public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) { rotation.dragging = true; }
    @Override protected void onDrag(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) { rotation.drag(dx); }
    @Override public void onRelease(net.minecraft.client.input.MouseButtonEvent event) { rotation.dragging = false; }
    //?} else {
    /*@Override public void onClick(double x, double y) { rotation.dragging = true; }
    @Override protected void onDrag(double x, double y, double dx, double dy) { rotation.drag(dx); }
    @Override public void onRelease(double x, double y) { rotation.dragging = false; }
    *///?}

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, getMessage());
    }

    static final class Rotation {
        private long lastFrame;
        private float yaw;
        private boolean dragging;

        private float angle() {
            long time = System.nanoTime();
            if (lastFrame != 0 && !dragging) {
                yaw = (yaw + Math.min(0.1f, (time - lastFrame) / 1_000_000_000f) * 16f) % 360f;
            }
            lastFrame = time;
            return yaw;
        }

        private void drag(double dx) {
            yaw = (yaw + (float) dx * 2f) % 360f;
        }

        void release() {
            dragging = false;
            lastFrame = 0;
        }

        void reset() {
            release();
            yaw = 0;
        }
    }
}
