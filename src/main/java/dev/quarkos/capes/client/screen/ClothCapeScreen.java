//? if fabric || <1.21.2 {
package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.net.CapeCalls;
import dev.quarkos.capes.client.net.ServiceUrl;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.screen.ui.CapeGridLayout.Rect;
import dev.quarkos.capes.client.screen.ui.CapeTheme;
import dev.quarkos.capes.sim.WaveCloth;
//? if dev_catalog {
/*import dev.quarkos.capes.client.net.LabyBrowse;
import dev.quarkos.capes.client.net.LabyCatalog;
*///?}
//? if fabric {
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
//?}
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import static dev.quarkos.capes.client.screen.ui.CapeTheme.text;

final class ClothCapeScreen {
    private static Screen current;
    private static SettingsEdits currentEdits;

    private ClothCapeScreen() { }

    static boolean isOpen(Screen screen) { return screen != null && screen == current; }

    static void tick(Screen screen) {
        if (isOpen(screen)) currentEdits.apply();
    }

    static Screen create(Screen parent, CapeScreens.SettingsTab tab) {
        ClientSettings settings = ClientSettings.get();
        SettingsEdits edits = new SettingsEdits();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(text("ui.settings_title"))
                .setTransparentBackground(true)
                .setSavingRunnable(() -> {
                    edits.apply();
                    CapeBoard.save(ignored -> { });
                });
        ConfigEntryBuilder fields = builder.entryBuilder();

        ConfigCategory physics = builder.getOrCreateCategory(text("tab.physics"));
        physics.addEntry(new ActionRow(text("tab.my_cape"), () -> {
            edits.apply();
            returnToWardrobe(parent);
        }));
        WaveCloth.Settings cloth = settings.cloth;
        choice(fields, physics, edits, "model", WaveCloth.Movement.class, cloth.movement(), CapeBoard::movement);
        choice(fields, physics, edits, "look", WaveCloth.Mesh.class, cloth.mesh(), CapeBoard::mesh);
        choice(fields, physics, edits, "wind", WaveCloth.Wind.class, cloth.wind(), CapeBoard::wind);
        slider(fields, physics, edits, "gravity", cloth.gravity(), 5, 32, 25, CapeBoard::gravity);
        slider(fields, physics, edits, "height", cloth.heightMultiplier(), 4, 16, 6, CapeBoard::height);
        slider(fields, physics, edits, "strafe", cloth.straveMultiplier(), 1, 8, 2, CapeBoard::strafe);
        toggle(fields, physics, edits, "gravity_vector", cloth.computeGravityVector(), false, CapeBoard::gravityVector);

        ConfigCategory sources = builder.getOrCreateCategory(text("tab.sources"));
        sources.addEntry(fields.startTextDescription(text("sources.help")).build());
        for (String provider : CapeCalls.PROVIDERS) {
            toggle(fields, sources, edits, provider, settings.providerEnabled(provider), false, value -> CapeBoard.provider(provider, value));
        }
        //? if dev_catalog {
        /*if (LabyCatalog.enabled()) {
            sources.addEntry(new ActionRow(text("laby.previous"), () -> LabyBrowse.previous(() -> { })));
            sources.addEntry(new ActionRow(text("laby.next"), () -> LabyBrowse.next(() -> { })));
            if (!LabyBrowse.status().isBlank()) sources.addEntry(fields.startTextDescription(Component.literal(LabyBrowse.status())).build());
        }
        *///?}

        ConfigCategory advanced = builder.getOrCreateCategory(text("tab.advanced"));
        toggle(fields, advanced, edits, "local_only", settings.localOnly, true, CapeBoard::localOnly);
        toggle(fields, advanced, edits, "show_others", settings.showOthers, true, CapeBoard::showOthers);
        toggle(fields, advanced, edits, "elytra", settings.enableElytraTexture, true, CapeBoard::elytra);
        var back = fields.startBooleanToggle(text("preview.back"), CapeBook.previewBack).setDefaultValue(true).build();
        advanced.addEntry(back);
        edits.track(back::getValue, value -> CapeBook.previewBack = value);
        var url = fields.startStrField(text("field.base_url"), settings.baseUrl)
                .setDefaultValue(CapeCalls.DEFAULT_SERVICE_URL)
                .setErrorSupplier(value -> ServiceUrl.valid(value) ? Optional.empty() : help(text("ui.invalid_url")))
                .build();
        advanced.addEntry(url);
        edits.track(url::getValue, CapeBoard::baseUrl);

        Component selected = text(tab.key);
        builder.setFallbackCategory(builder.getOrCreateCategory(selected));
        //? if fabric {
        builder.setAfterInitConsumer(screen -> ScreenEvents.beforeRender(screen).register((view, graphics, x, y, delta) ->
                CapeTheme.background(graphics, view.width, view.height)));
        //?}

        Screen built = builder.build();
        if (built instanceof ClothConfigScreen config) {
            int index = 0;
            for (Component label : config.getCategorizedEntries().keySet()) {
                if (selected.getString().equals(label.getString())) {
                    config.selectedCategoryIndex = index;
                    break;
                }
                index++;
            }
        }
        current = built;
        currentEdits = edits;
        return built;
    }

    private static void returnToWardrobe(Screen parent) {
        Minecraft.getInstance().setScreen(parent instanceof CapeScreen ? parent : CapeScreens.open(parent));
    }

    private static <T extends Enum<T>> void choice(ConfigEntryBuilder fields, ConfigCategory category, SettingsEdits edits,
                                                   String key, Class<T> type, T value, Consumer<T> write) {
        var entry = fields.startEnumSelector(text("selector." + key), type, value)
                .setDefaultValue(value).setEnumNameProvider(CapeChoices::name)
                .setTooltipSupplier(selected -> tooltip(CapeChoices.help(selected))).build();
        category.addEntry(entry);
        edits.track(entry::getValue, write);
    }

    private static void toggle(ConfigEntryBuilder fields, ConfigCategory category, SettingsEdits edits,
                               String key, boolean value, boolean fallback, Consumer<Boolean> write) {
        var entry = fields.startBooleanToggle(text("toggle." + key), value)
                .setDefaultValue(fallback).setTooltipSupplier(() -> tooltip(text("tooltip." + key))).build();
        category.addEntry(entry);
        edits.track(entry::getValue, write);
    }

    private static void slider(ConfigEntryBuilder fields, ConfigCategory category, SettingsEdits edits,
                               String key, int value, int min, int max, int fallback, IntConsumer write) {
        var entry = fields.startIntSlider(text("physics." + key), value, min, max)
                .setDefaultValue(fallback).setTextGetter(number -> Component.literal(Integer.toString(number)))
                .setTooltipSupplier(() -> tooltip(text("tooltip." + key))).build();
        category.addEntry(entry);
        edits.track(entry::getValue, write::accept);
    }

    private static Optional<Component> help(Component value) {
        var client = Minecraft.getInstance();
        if (client.screen == null) return Optional.of(value);
        int width = Math.min(146, client.screen.width - 24);
        return Optional.of(Component.literal(CapeTheme.ellipsis(client.font, value.getString(), width)));
    }

    private static Optional<Component[]> tooltip(Component value) {
        return help(value).map(line -> new Component[]{line});
    }

    private static final class ActionRow extends TooltipListEntry<Object> {
        private final Button button;

        ActionRow(Component label, Runnable action) {
            super(label, Optional::empty, false);
            button = CapeTheme.button(label, new Rect(0, 0, 126, 20), CapeTheme.Face.BUTTON, action);
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean hovered, float delta) {
            button.setWidth(Math.min(126, entryWidth));
            button.setX(x);
            button.setY(y);
            button.render(graphics, mouseX, mouseY, delta);
        }

        @Override public int getItemHeight() { return 24; }
        @Override public Object getValue() { return null; }
        @Override public Optional<Object> getDefaultValue() { return Optional.empty(); }
        @Override public List<? extends GuiEventListener> children() { return List.of(button); }
        @Override public List<? extends NarratableEntry> narratables() { return List.of(button); }

        //? if <1.21 {
        /*private boolean dragging;
        @Override public void setFocused(GuiEventListener listener) { }
        @Override public GuiEventListener getFocused() { return null; }
        @Override public boolean isDragging() { return dragging; }
        @Override public void setDragging(boolean dragging) { this.dragging = dragging; }
        @Override public NarratableEntry.NarrationPriority narrationPriority() { return NarratableEntry.NarrationPriority.NONE; }
        @Override public void updateNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) { }
        *///?}
    }
}
//?}
