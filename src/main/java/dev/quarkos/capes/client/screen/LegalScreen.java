package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.screen.ui.CapeGridLayout.Rect;
import dev.quarkos.capes.client.screen.ui.CapeText;
import dev.quarkos.capes.client.screen.ui.CapeTheme;
import dev.quarkos.capes.client.screen.ui.CapeTheme.Face;
import dev.quarkos.capes.client.screen.ui.CapeUiScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** About, credits and legal notices: license, LabyMod attribution and non-affiliation, Minecraft notice. */
public final class LegalScreen extends CapeUiScreen {
    private static final int LINE = 11;
    private final Screen parent;
    private Rect panel;
    private List<String> lines = List.of();
    private int perPage = 1, page;

    public LegalScreen(Screen parent) {
        super(CapeTheme.text("legal.title"));
        this.parent = parent;
    }

    public int pages() { return Math.max(1, (lines.size() + perPage - 1) / perPage); }

    @Override
    protected void init() {
        resetWidgets();
        int w = Math.min(360, frame.width());
        int h = Math.max(120, frame.height() - 8);
        panel = new Rect(frame.centerX() - w / 2, frame.y(), w, h);
        int textWidth = panel.width() - 24;
        lines = CapeText.wrap(CapeTheme.text("legal.body").getString(), textWidth, font::width);
        perPage = Math.max(1, (panel.height() - 34 - CapeTheme.CONTROL - 12) / LINE);
        page = Math.min(page, pages() - 1);
        Rect actions = new Rect(panel.x() + 12, panel.bottom() - CapeTheme.CONTROL - 8, textWidth, CapeTheme.CONTROL);
        button(CapeTheme.text("site.previous"), actions.cell(0, 3, 6), Face.BUTTON, () -> turn(-1)).active = page > 0;
        button(CapeTheme.text("site.next"), actions.cell(1, 3, 6), Face.BUTTON, () -> turn(1)).active = page + 1 < pages();
        button(CapeTheme.text("button.done"), actions.cell(2, 3, 6), Face.PRIMARY, this::onClose);
    }

    private void turn(int by) {
        page = Math.max(0, Math.min(pages() - 1, page + by));
        init();
    }

    @Override
    protected void renderPanels(GuiGraphics graphics) {
        CapeTheme.panel(graphics, panel);
        int x = panel.x() + 12, w = panel.width() - 24;
        CapeTheme.title(graphics, font, getTitle(), x, panel.y() + 10, w);
        int y = panel.y() + 28;
        int start = page * perPage;
        for (int i = start; i < Math.min(lines.size(), start + perPage); i++, y += LINE) {
            CapeTheme.line(graphics, font, lines.get(i), x, y, w, CapeTheme.TEXT);
        }
        if (pages() > 1) {
            CapeTheme.line(graphics, font, Component.translatable("text.quarkos_capes.legal.page", page + 1, pages()).getString(),
                    x, panel.bottom() - CapeTheme.CONTROL - 20, w, CapeTheme.MUTED);
        }
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
