package dev.quarkos.capes.client.screen.ui;

import dev.quarkos.capes.client.screen.ui.CapeGridLayout.Rect;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

public final class CapeTheme {
    public static final int BACKDROP = 0xFF232323;
    public static final int PANEL = 0xFF2B2B2B;
    public static final int INSET = 0xFF262626;
    public static final int BORDER = 0xFF454545;
    public static final int TEXT = 0xFFECECEC;
    public static final int MUTED = 0xFFAAAAAA;
    public static final int ACCENT = 0xFF4C8DFF;
    public static final int CONTROL = 20;
    private static final int RAISED = 0xFF353535;
    private static final int HOVER = 0xFF3D3D3D;
    private static final int HOVER_BORDER = 0xFF696969;
    private static final int DISABLED = 0xFF777777;
    private static final int SELECTED = 0xFF26364E;
    private static final int SELECTED_HOVER = 0xFF304361;
    private static final int PRIMARY_HOVER = 0xFF77A9FF;
    private static final int PRIMARY_TEXT = 0xFF121C2B;

    public enum Face { BUTTON, PRIMARY, CHIP, CYCLE, TAB }

    @FunctionalInterface
    public interface Thumbnail {
        void draw(GuiGraphics graphics, Rect area);
    }

    private CapeTheme() { }

    public static Component text(String key) {
        return Component.translatable("text.quarkos_capes." + key);
    }

    public static void background(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, BACKDROP);
    }

    public static void panel(GuiGraphics graphics, Rect area) {
        box(graphics, area, PANEL, BORDER);
    }

    public static void box(GuiGraphics graphics, Rect area, int fill, int border) {
        if (area.width() <= 0 || area.height() <= 0) return;
        graphics.fill(area.x(), area.y(), area.right(), area.bottom(), border);
        if (area.width() > 2 && area.height() > 2) {
            graphics.fill(area.x() + 1, area.y() + 1, area.right() - 1, area.bottom() - 1, fill);
        }
    }

    public static String ellipsis(Font font, String value, int width) {
        return CapeText.ellipsis(value, width, font::width);
    }

    public static void label(GuiGraphics graphics, Font font, Component label, int x, int y, int width, int color) {
        line(graphics, font, label.getString(), x, y, width, color);
    }

    public static void line(GuiGraphics graphics, Font font, String value, int x, int y, int width, int color) {
        graphics.drawString(font, ellipsis(font, value, width), x, y, color, false);
    }

    public static void centered(GuiGraphics graphics, Font font, Component label, int center, int y, int width, int color) {
        String value = ellipsis(font, label.getString(), width);
        graphics.drawString(font, value, center - font.width(value) / 2, y, color, false);
    }

    public static void title(GuiGraphics graphics, Font font, Component title, int x, int y, int width) {
        int color = ACCENT;
        graphics.fill(x + 1, y, x + 8, y + 2, color);
        graphics.fill(x, y + 2, x + 8, y + 8, color);
        graphics.fill(x, y + 8, x + 6, y + 9, color);
        String value = CapeText.ellipsis(title.getString(), width - 14,
                string -> font.width(Component.literal(string).withStyle(ChatFormatting.BOLD)));
        graphics.drawString(font, Component.literal(value).withStyle(ChatFormatting.BOLD), x + 14, y, TEXT, false);
    }

    public static FlatButton button(Component label, Rect area, Face face, Runnable action) {
        return new FlatButton(label, area, face, action);
    }

    public static FlatButton card(Component title, Component creator, Rect area, Thumbnail image,
                                  BooleanSupplier selected, BooleanSupplier equipped, Runnable action) {
        return new Card(title, creator, area, image, selected, equipped, action);
    }

    public static EditBox field(Font font, Component label, Rect area) {
        return new Field(font, label, area);
    }

    private static Rect bounds(net.minecraft.client.gui.components.AbstractWidget widget) {
        return new Rect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
    }

    private static void check(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y + 3, x + 2, y + 5, color);
        graphics.fill(x + 1, y + 4, x + 3, y + 6, color);
        graphics.fill(x + 2, y + 3, x + 4, y + 5, color);
        graphics.fill(x + 3, y + 2, x + 5, y + 4, color);
        graphics.fill(x + 4, y + 1, x + 6, y + 3, color);
    }

    public static class FlatButton extends Button {
        private final Face face;
        private boolean selected;

        private FlatButton(Component label, Rect area, Face face, Runnable action) {
            super(area.x(), area.y(), area.width(), area.height(), label, button -> action.run(), DEFAULT_NARRATION);
            this.face = face;
        }

        public FlatButton selected(boolean value) {
            selected = value;
            return this;
        }

        //? if >=1.21.11 {
        @Override
        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            draw(graphics, mouseX, mouseY);
        }
        //?} else {
        /*@Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            draw(graphics, mouseX, mouseY);
        }
        *///?}

        protected void draw(GuiGraphics graphics, int mouseX, int mouseY) {
            Rect area = bounds(this);
            boolean hover = active && isMouseOver(mouseX, mouseY);
            boolean primary = active && face == Face.PRIMARY;
            boolean checked = selected && face == Face.CHIP;
            int fill = !active ? PANEL : primary ? (hover ? PRIMARY_HOVER : ACCENT)
                    : checked ? (hover ? SELECTED_HOVER : SELECTED) : hover || selected && face == Face.TAB ? HOVER : RAISED;
            int border = isFocused() ? TEXT : primary || checked ? ACCENT : hover ? HOVER_BORDER : BORDER;
            int color = !active ? DISABLED : primary ? PRIMARY_TEXT : TEXT;
            box(graphics, area, fill, border);
            Font font = Minecraft.getInstance().font;
            int y = area.centerY() - font.lineHeight / 2;
            if (face == Face.CYCLE && area.width() >= 28) {
                label(graphics, font, getMessage(), area.x() + 6, y, area.width() - 17, color);
                int x = area.right() - 9;
                graphics.fill(x, y + 3, x + 5, y + 4, color);
                graphics.fill(x + 1, y + 4, x + 4, y + 5, color);
                graphics.fill(x + 2, y + 5, x + 3, y + 6, color);
            } else {
                int mark = checked && area.width() >= 44 ? 10 : 0;
                if (mark > 0) check(graphics, area.x() + 5, y, ACCENT);
                centered(graphics, font, getMessage(), area.centerX() + mark / 2, y, area.width() - 10 - mark, color);
            }
        }
    }

    private static final class Card extends FlatButton {
        private final Component creator;
        private final Thumbnail image;
        private final BooleanSupplier selected;
        private final BooleanSupplier equipped;

        private Card(Component title, Component creator, Rect area, Thumbnail image,
                     BooleanSupplier selected, BooleanSupplier equipped, Runnable action) {
            super(title, area, Face.BUTTON, action);
            this.creator = creator;
            this.image = image;
            this.selected = selected;
            this.equipped = equipped;
        }

        @Override
        protected void draw(GuiGraphics graphics, int mouseX, int mouseY) {
            Rect area = bounds(this);
            boolean hover = active && isMouseOver(mouseX, mouseY);
            boolean worn = equipped.getAsBoolean();
            boolean marked = worn || selected.getAsBoolean();
            box(graphics, area, hover ? HOVER : RAISED, marked ? ACCENT : isFocused() ? TEXT : hover ? HOVER_BORDER : BORDER);
            int labels = creator == null ? 17 : 26;
            Rect well = new Rect(area.x() + 4, area.y() + 4, area.width() - 8, area.height() - labels - 4);
            if (well.height() > 0) {
                graphics.fill(well.x(), well.y(), well.right(), well.bottom(), INSET);
                image.draw(graphics, well.inset(3));
            }
            Font font = Minecraft.getInstance().font;
            int y = area.bottom() - labels + 5;
            label(graphics, font, getMessage(), area.x() + 6, y, area.width() - 12, TEXT);
            if (creator != null) label(graphics, font, creator, area.x() + 6, y + 10, area.width() - 12, MUTED);
            if (worn) {
                int x = area.right() - 15;
                graphics.fill(x, area.y() + 4, x + 11, area.y() + 15, ACCENT);
                check(graphics, x + 2, area.y() + 6, PRIMARY_TEXT);
            }
        }
    }

    private static final class Field extends EditBox {
        private final Rect frame;

        private Field(Font font, Component label, Rect area) {
            super(font, area.x() + 5, area.y() + 5, area.width() - 10, area.height() - 5, label);
            frame = area;
            setBordered(false);
            setTextColor(TEXT);
            setTextColorUneditable(MUTED);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            box(graphics, frame, INSET, isFocused() ? ACCENT : BORDER);
            super.renderWidget(graphics, mouseX, mouseY, delta);
        }
    }
}
