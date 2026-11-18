//? if (forge || neoforge) && >=1.21.2 {
/*package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.net.CapeCalls;
import dev.quarkos.capes.client.net.ServiceUrl;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.screen.CapeScreens.SettingsTab;
import dev.quarkos.capes.client.screen.ui.CapeGridLayout.Rect;
import dev.quarkos.capes.client.screen.ui.CapeTheme;
import dev.quarkos.capes.client.screen.ui.CapeTheme.Face;
import dev.quarkos.capes.client.screen.ui.CapeUiScreen;
//? if dev_catalog {
/^import dev.quarkos.capes.client.net.LabyBrowse;
import dev.quarkos.capes.client.net.LabyCatalog;
^///?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static dev.quarkos.capes.client.screen.ui.CapeTheme.text;

public final class ForgeCapeScreen extends CapeUiScreen {
    private final Screen parent;
    private SettingsTab tab;
    private Rect pane, serviceLabel;
    private String note = "";
    private String serviceText;
    private int page, row, rowsPerPage;

    ForgeCapeScreen(Screen parent, SettingsTab tab) {
        super(text("ui.settings_title"));
        this.parent = parent;
        this.tab = tab;
    }

    @Override protected void init() {
        resetWidgets();
        serviceLabel = null;
        int w = Math.min(520, frame.width());
        int x = frame.centerX() - w / 2;
        int bottom = frame.bottom() - 38;
        pane = new Rect(x, frame.y() + 54, w, Math.max(0, bottom - frame.y() - 54));
        int backWidth = Math.min(74, w / 3);
        button(text("tab.my_cape"), new Rect(pane.right() - backWidth, frame.y(), backWidth, CapeTheme.CONTROL), Face.BUTTON, this::wardrobe);
        Rect tabs = new Rect(x, frame.y() + 27, w, CapeTheme.CONTROL);
        SettingsTab[] all = SettingsTab.values();
        for (int i = 0; i < all.length; i++) {
            SettingsTab destination = all[i];
            button(text(destination.key), tabs.cell(i, all.length, 4), Face.TAB, () -> {
                page = 0;
                tab = destination;
                init();
            }).selected(tab == destination);
        }

        row = 0;
        rowsPerPage = Math.max(1, (pane.height() - 10) / 24);
        var settings = ClientSettings.get();
        switch (tab) {
            case PHYSICS -> {
                var cloth = settings.cloth;
                choice("model", cloth.movement(), CapeBoard::movement);
                choice("look", cloth.mesh(), CapeBoard::mesh);
                choice("wind", cloth.wind(), CapeBoard::wind);
                slider("gravity", cloth.gravity(), 5, 32, CapeBoard::gravity);
                slider("height", cloth.heightMultiplier(), 4, 16, CapeBoard::height);
                slider("strafe", cloth.straveMultiplier(), 1, 8, CapeBoard::strafe);
                toggle("toggle.gravity_vector", cloth.computeGravityVector(), CapeBoard::gravityVector);
            }
            case SOURCES -> {
                for (String provider : CapeCalls.PROVIDERS) {
                    toggle("toggle." + provider, settings.providerEnabled(provider), value -> CapeBoard.provider(provider, value));
                }
                //? if dev_catalog {
                /^if (LabyCatalog.enabled()) {
                    action("laby.previous", () -> LabyBrowse.previous(this::init));
                    action("laby.next", () -> LabyBrowse.next(this::init));
                    if (!LabyBrowse.status().isBlank()) note = LabyBrowse.status();
                }
                ^///?}
            }
            case ADVANCED -> {
                toggle("toggle.local_only", settings.localOnly, CapeBoard::localOnly);
                toggle("toggle.show_others", settings.showOthers, CapeBoard::showOthers);
                toggle("toggle.elytra", settings.enableElytraTexture, CapeBoard::elytra);
                toggle("preview.back", CapeBook.previewBack, value -> CapeBook.previewBack = value);
                serviceUrl();
            }
        }

        int lastPage = (row - 1) / rowsPerPage;
        if (page > lastPage) {
            page = lastPage;
            init();
            return;
        }
        Rect footer = new Rect(x, frame.bottom() - CapeTheme.CONTROL, w, CapeTheme.CONTROL);
        Rect buttons = new Rect(footer.right() - footer.width() / 2, footer.y(), footer.width() / 2, footer.height());
        button(text("button.discard"), buttons.cell(0, 2, 4), Face.BUTTON, () -> {
            CapeBoard.discard();
            serviceText = null;
            note = text("status.discarded").getString();
            init();
        });
        button(text("button.save_quit"), buttons.cell(1, 2, 4), Face.PRIMARY, () -> CapeBoard.save(message -> {
            note = message;
            if (minecraft.screen == this && message.equals(text("status.saved").getString())) onClose();
        }));
        if (lastPage > 0) {
            Rect pager = new Rect(footer.x(), footer.y(), Math.min(132, footer.width() / 2 - 6), footer.height());
            button(text("site.previous"), pager.cell(0, 2, 4), Face.BUTTON, () -> turn(-1)).active = page > 0;
            button(text("site.next"), pager.cell(1, 2, 4), Face.BUTTON, () -> turn(1)).active = page < lastPage;
        }
    }

    private Rect nextRow() {
        int index = row++;
        if (index / rowsPerPage != page) return null;
        Rect area = new Rect(pane.x() + 6, pane.y() + 6 + index % rowsPerPage * 24, pane.width() - 12, CapeTheme.CONTROL);
        return area.bottom() <= pane.bottom() ? area : null;
    }

    private void turn(int direction) { page += direction; init(); }

    //? if dev_catalog {
    /^private void action(String key, Runnable action) {
        Rect area = nextRow();
        if (area != null) button(text(key), area, Face.BUTTON, action);
    }
    ^///?}

    private <T extends Enum<T>> void choice(String key, T value, Consumer<T> write) {
        Rect area = nextRow();
        if (area == null) return;
        button(text("selector." + key).copy().append("  ·  ").append(CapeChoices.name(value)), area, Face.CYCLE, () -> {
            write.accept(CapeChoices.next(value));
            init();
        });
    }

    private void toggle(String key, boolean value, Consumer<Boolean> write) {
        Rect area = nextRow();
        if (area == null) return;
        button(text(key).copy().append("  ·  ").append(Component.translatable(value ? "options.on" : "options.off")), area, Face.CHIP, () -> {
            write.accept(!value);
            init();
        }).selected(value);
    }

    private void slider(String key, int value, int min, int max, IntConsumer write) {
        Rect area = nextRow();
        if (area != null) addRenderableWidget(new NamedSlider(area, "physics." + key, value, min, max, write));
    }

    private void serviceUrl() {
        Rect area = nextRow();
        if (area == null) return;
        int labelWidth = Math.min(115, area.width() / 3);
        serviceLabel = new Rect(area.x(), area.y(), labelWidth, area.height());
        Rect field = new Rect(area.x() + labelWidth + 4, area.y(), area.width() - labelWidth - 4, area.height());
        var input = addRenderableWidget(CapeTheme.field(font, text("field.base_url"), field));
        input.setMaxLength(2048);
        input.setValue(serviceText == null ? ClientSettings.get().baseUrl : serviceText);
        input.setResponder(value -> {
            serviceText = value;
            if (ServiceUrl.valid(value)) {
                CapeBoard.baseUrl(value);
                note = "";
            } else note = text("ui.invalid_url").getString();
        });
    }

    @Override
    protected void renderPanels(GuiGraphics graphics) {
        CapeTheme.title(graphics, font, getTitle(), pane.x(), frame.y() + 6, pane.width() - Math.min(74, pane.width() / 3) - 8);
        CapeTheme.panel(graphics, pane);
        if (serviceLabel != null) CapeTheme.label(graphics, font, text("field.base_url"), serviceLabel.x() + 3,
                serviceLabel.y() + 6, serviceLabel.width() - 3, CapeTheme.TEXT);
        CapeTheme.line(graphics, font, note, pane.x(), frame.bottom() - 31, pane.width(), CapeTheme.MUTED);
    }

    private void wardrobe() {
        minecraft.setScreen(parent instanceof CapeScreen ? parent : CapeScreens.open(parent));
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    private static final class NamedSlider extends AbstractSliderButton {
        private final String key;
        private final int min, max;
        private final IntConsumer write;

        NamedSlider(Rect area, String key, int value, int min, int max, IntConsumer write) {
            super(area.x(), area.y(), area.width(), area.height(), Component.empty(), (value - min) / (double) (max - min));
            this.key = key;
            this.min = min;
            this.max = max;
            this.write = write;
            updateMessage();
        }

        @Override protected void updateMessage() { setMessage(text(key).copy().append("  " + current())); }
        @Override protected void applyValue() { write.accept(current()); }
        private int current() { return min + (int) Math.round(value * (max - min)); }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            Rect area = new Rect(getX(), getY(), getWidth(), getHeight());
            CapeTheme.box(graphics, area, CapeTheme.PANEL, isFocused() ? CapeTheme.ACCENT : CapeTheme.BORDER);
            var font = Minecraft.getInstance().font;
            CapeTheme.label(graphics, font, getMessage(), area.x() + 6, area.y() + 3, area.width() - 12, CapeTheme.TEXT);
            int left = area.x() + 6, right = area.right() - 6, y = area.bottom() - 4;
            graphics.fill(left, y, right, y + 1, CapeTheme.BORDER);
            int thumb = left + (int) Math.round((right - left - 3) * value);
            graphics.fill(thumb, y - 1, thumb + 3, y + 2, CapeTheme.ACCENT);
        }
    }
}
*///?}
