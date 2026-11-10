//? if forge {

/*//? if =1.21.1 {
/^package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.ClientSettings;
//? if dev_catalog {
/^¹import dev.quarkos.capes.client.net.LabyBrowse;
¹^///?}
//? if dev_catalog {
/^¹import dev.quarkos.capes.client.net.LabyCatalog;
¹^///?}
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.sim.WaveCloth;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import java.util.List;
import java.util.Optional;
import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ClothCapeScreen {
    private static boolean busy;
    private static Screen parentScreen;
    private static Screen current;
    private static String note = "";

    private ClothCapeScreen() {
    }

    static boolean isOpen(Screen screen) { return screen != null && screen == current; }

    static Screen refreshed(ClothConfigScreen previous) {
        int tab = previous.selectedCategoryIndex;
        Screen fresh = create(parentScreen);
        if (fresh instanceof ClothConfigScreen cloth) cloth.selectedCategoryIndex = tab;
        return fresh;
    }

    static Screen create(Screen parent) {
        parentScreen = parent;
        ClientSettings settings = ClientSettings.get();
        Held held = new Held();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("text.quarkos_capes.title"))
                .setDoesConfirmSave(false)
                .setAlwaysShowTabs(true)
                .setTransparentBackground(true)
                .setSavingRunnable(() -> { held.edits.apply(); CapeBoard.save(value -> note = value); });
        ConfigEntryBuilder fields = builder.entryBuilder();
        ConfigCategory mine = category(builder, "text.quarkos_capes.tab.mine");
        for (String sample : List.of("bands", "slide", "cloak")) mine.addEntry(new SampleRow(sample));
        mine.addEntry(new ActionRow("text.quarkos_capes.button.save", () -> { held.edits.apply(); CapeBoard.save(value -> note = value); }));
        mine.addEntry(new ActionRow("text.quarkos_capes.button.publish", () -> CapeBoard.publish(value -> note = value)));
        mine.addEntry(new ActionRow("text.quarkos_capes.button.unpublish", () -> CapeBoard.unpublish(value -> note = value)));

        ConfigCategory style = category(builder, "text.quarkos_capes.tab.style");
        held.style = choice(fields, style, "style", CapeChoices.Style.class, CapeChoices.style());
        held.showBack = toggle(fields, style, "text.quarkos_capes.preview.back", CapeBook.previewBack, true);
        held.animation = toggle(fields, style, "text.quarkos_capes.toggle.animation", OwnerCape.get().animateFrames, false);

        ConfigCategory physics = category(builder, "text.quarkos_capes.tab.physics");
        held.movement = choice(fields, physics, "model", WaveCloth.Movement.class, settings.cloth.movement());
        held.mesh = choice(fields, physics, "look", WaveCloth.Mesh.class, settings.cloth.mesh());
        held.wind = choice(fields, physics, "wind", WaveCloth.Wind.class, settings.cloth.wind());
        held.gravity = slider(fields, physics, "text.quarkos_capes.physics.gravity", settings.cloth.gravity(), 5, 32, 25);
        held.height = slider(fields, physics, "text.quarkos_capes.physics.height", settings.cloth.heightMultiplier(), 4, 16, 6);
        held.strafe = slider(fields, physics, "text.quarkos_capes.physics.strafe", settings.cloth.straveMultiplier(), 1, 8, 2);
        held.vector = toggle(fields, physics, "text.quarkos_capes.toggle.gravity_vector", settings.cloth.computeGravityVector(), false);

        ConfigCategory sources = category(builder, "text.quarkos_capes.tab.sources");
        held.optifine = toggle(fields, sources, "text.quarkos_capes.toggle.optifine", settings.enableOptifine, false);
        held.labymod = toggle(fields, sources, "text.quarkos_capes.toggle.labymod", settings.enableLabyMod, false);
        held.minecraftCapes = toggle(fields, sources, "text.quarkos_capes.toggle.minecraftcapes", settings.enableMinecraftCapesMod, false);
        held.cosmetica = toggle(fields, sources, "text.quarkos_capes.toggle.cosmetica", settings.enableCosmetica, false);
        held.cloaks = toggle(fields, sources, "text.quarkos_capes.toggle.cloaksplus", settings.enableCloaksPlus, false);
//? if dev_catalog {
        /^¹if (LabyCatalog.enabled()) {
            sources.addEntry(new ActionRow("text.quarkos_capes.laby.previous", () -> LabyBrowse.previous(() -> {})));
            sources.addEntry(new ActionRow("text.quarkos_capes.laby.next", () -> LabyBrowse.next(() -> {})));
            if (!LabyBrowse.status().isBlank()) {
                sources.addEntry(fields.startTextDescription(Component.literal(LabyBrowse.status())).build());
            }
        }
¹^///?}


        ConfigCategory gallery = category(builder, "text.quarkos_capes.tab.gallery");
        gallery.addEntry(new ActionRow("text.quarkos_capes.button.publish", () -> CapeBoard.publish(value -> note = value)));
        gallery.addEntry(new ActionRow("text.quarkos_capes.button.unpublish", () -> CapeBoard.unpublish(value -> note = value)));
        gallery.addEntry(new ActionRow("text.quarkos_capes.gallery.report", () -> CapeBoard.report(null, value -> note = value)));

        ConfigCategory advanced = category(builder, "text.quarkos_capes.tab.advanced");
        held.others = toggle(fields, advanced, "text.quarkos_capes.toggle.show_others", settings.showOthers, true);
        held.offline = toggle(fields, advanced, "text.quarkos_capes.toggle.local_only", settings.localOnly, true);
        held.elytra = toggle(fields, advanced, "text.quarkos_capes.toggle.elytra", settings.enableElytraTexture, true);
        held.baseUrl = fields.startStrField(Component.translatable("text.quarkos_capes.field.base_url"), settings.baseUrl)
                .setDefaultValue(dev.quarkos.capes.client.net.CapeCalls.DEFAULT_SERVICE_URL)
                .setErrorSupplier(value -> dev.quarkos.capes.client.net.ServiceUrl.valid(value) ? Optional.empty()
                        : Optional.of(Component.translatable("text.quarkos_capes.status.invalid_url")))
                .build();
        advanced.addEntry(held.baseUrl);

        held.edits.track(held.style::getValue, value -> CapeBoard.style(CapeChoices.id(value)));
        held.edits.track(held.animation::getValue, CapeBoard::animation);
        held.edits.track(held.showBack::getValue, value -> CapeBook.previewBack = value);
        held.edits.track(held.movement::getValue, CapeBoard::movement);
        held.edits.track(held.mesh::getValue, CapeBoard::mesh);
        held.edits.track(held.wind::getValue, CapeBoard::wind);
        held.edits.track(held.gravity::getValue, CapeBoard::gravity);
        held.edits.track(held.height::getValue, CapeBoard::height);
        held.edits.track(held.strafe::getValue, CapeBoard::strafe);
        held.edits.track(held.vector::getValue, CapeBoard::gravityVector);
        held.edits.track(held.others::getValue, CapeBoard::showOthers);
        held.edits.track(held.offline::getValue, CapeBoard::localOnly);
        held.edits.track(held.elytra::getValue, CapeBoard::elytra);
        held.edits.track(held.baseUrl::getValue, CapeBoard::baseUrl);
        held.edits.track(held.optifine::getValue, value -> CapeBoard.provider("optifine", value));
        held.edits.track(held.labymod::getValue, value -> CapeBoard.provider("labymod", value));
        held.edits.track(held.minecraftCapes::getValue, value -> CapeBoard.provider("minecraftcapes", value));
        held.edits.track(held.cosmetica::getValue, value -> CapeBoard.provider("cosmetica", value));
        held.edits.track(held.cloaks::getValue, value -> CapeBoard.provider("cloaksplus", value));

        category(builder, "text.quarkos_capes.site.browse").addEntry(fields.startTextDescription(Component.translatable("text.quarkos_capes.site.browse")).build());

        builder.setAfterInitConsumer(screen -> {
            PreviewPane pane = new PreviewPane(parent, held);
            pane.setX(Math.max(8, screen.width - 110));
            pane.setY(28);
            attach(screen, pane);
        });
        current = builder.build();
        return current;
    }

    static int[] previewBounds(Screen screen) {
        if (screen.width < 480 || screen.height < 300) return null;
        int top = screen instanceof ClothConfigScreen cloth ? Math.max(80, cloth.listWidget.top + 8) : 80;
        return new int[]{Math.max(8, screen.width - 110), top, screen.width - 8, screen.height - 84};
    }

    private static ConfigCategory category(ConfigBuilder builder, String key) {
        return builder.getOrCreateCategory(Component.translatable(key));
    }

    private static <T extends Enum<T>> EnumListEntry<T> choice(ConfigEntryBuilder fields, ConfigCategory category, String key, Class<T> type, T value) {
        EnumListEntry<T> entry = fields.startEnumSelector(Component.translatable("text.quarkos_capes.selector." + key), type, value)
                .setDefaultValue(value).setEnumNameProvider(CapeChoices::name)
                .setTooltipSupplier(selected -> compactHelp(CapeChoices.help(selected))).build();
        category.addEntry(entry);
        return entry;
    }

    private static Component hint;
    private static java.util.Optional<Component[]> compactHelp(Component value) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && (screen.width < 480 || screen.height < 300)) {
            hint = value;
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Component[]{value});
    }

    private static BooleanListEntry toggle(ConfigEntryBuilder fields, ConfigCategory category, String key, boolean on, boolean fallback) {
        String name = Component.translatable(key).getString();
        BooleanListEntry entry = fields.startBooleanToggle(Component.translatable(key), on)
                .setDefaultValue(fallback)
                .setTooltipSupplier(() -> compactHelp(Component.translatable(key.contains(".sample.") ? "text.quarkos_capes.tooltip.equip" : key.contains(".button.") ? key : "text.quarkos_capes.tooltip." + key.substring(key.lastIndexOf('.') + 1))))
                .setYesNoTextSupplier(value -> CapeBoard.state(name, Boolean.TRUE.equals(value)))
                .build();
        category.addEntry(entry);
        return entry;
    }

    private static IntegerSliderEntry slider(ConfigEntryBuilder fields, ConfigCategory category, String name, int value, int min, int max, int fallback) {
        IntegerSliderEntry entry = fields.startIntSlider(Component.translatable(name), value, min, max)
                .setDefaultValue(fallback)
                .setTextGetter(current -> Component.translatable(name).append(" " + current))
                .setTooltipSupplier(() -> compactHelp(Component.translatable("text.quarkos_capes.tooltip." + name.substring(name.lastIndexOf('.') + 1))))
                .build();
        category.addEntry(entry);
        return entry;
    }

    private static final class Held {
        private final SettingsEdits edits = new SettingsEdits();
        private EnumListEntry<CapeChoices.Style> style;
        private EnumListEntry<WaveCloth.Movement> movement;
        private EnumListEntry<WaveCloth.Mesh> mesh;
        private EnumListEntry<WaveCloth.Wind> wind;
        private BooleanListEntry animation;
        private BooleanListEntry showBack;
        private IntegerSliderEntry gravity;
        private IntegerSliderEntry height;
        private IntegerSliderEntry strafe;
        private BooleanListEntry vector;
        private BooleanListEntry optifine;
        private BooleanListEntry labymod;
        private BooleanListEntry minecraftCapes;
        private BooleanListEntry cosmetica;
        private BooleanListEntry cloaks;
        private BooleanListEntry others;
        private BooleanListEntry offline;
        private BooleanListEntry elytra;
        private me.shedaniel.clothconfig2.gui.entries.StringListEntry baseUrl;
    }

    private static final class PreviewPane extends AbstractWidget {
        private final Screen parent;
        private final Held held;

        private PreviewPane(Screen parent, Held held) {
            super(0, 0, 100, 180, Component.empty());
            this.parent = parent;
            this.held = held;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof ClothConfigScreen cloth
                    && cloth.getSelectedCategory().getString().equals(Component.translatable("text.quarkos_capes.tab.gallery").getString())) {
                minecraft.execute(() -> minecraft.setScreen(new CapeGalleryScreen(parent)));
                return;
            }
            if (minecraft.screen instanceof ClothConfigScreen cloth
                    && cloth.getSelectedCategory().getString().equals(Component.translatable("text.quarkos_capes.site.browse").getString())) {
                minecraft.execute(() -> minecraft.setScreen(new BrowseScreen(parent))); return;
            }
            sync();
            if (hint != null) {
                graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(hint.getString(), minecraft.screen.width - 16), 8, minecraft.screen.height - 42, 0xFFB6C8DD);
                hint = null;
            }
            if (!note.isBlank()) graphics.drawString(minecraft.font,
                    minecraft.font.plainSubstrByWidth(note, Math.max(20, minecraft.screen.width - 16)),
                    8, minecraft.screen.height - 42, 0xFFE8B84A);
            if (minecraft.screen instanceof ClothConfigScreen cloth) {
                cloth.listWidget.updateSize(Math.max(120, cloth.width - (cloth.width < 480 || cloth.height < 300 ? 16 : 120)), cloth.listWidget.height, cloth.listWidget.top, cloth.listWidget.bottom);
            }
            if (minecraft.player != null && minecraft.screen.width >= 480 && minecraft.screen.height >= 300) {
                CapeBoard.show();
                int[] bounds = previewBounds(minecraft.screen);
                McBridge.preview(graphics, bounds[0], bounds[1], bounds[2], bounds[3], minecraft.player);
            }
            if (minecraft.screen instanceof ClothConfigScreen screen
                    && screen.getSelectedCategory().getString().equals(Component.translatable("text.quarkos_capes.tab.gallery").getString())) {
                CapeThumbs.draw(graphics, getX() + 8, getY());
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
        }

        private void sync() {
            if (busy) {
                return;
            }
            CapeChoices.Style style = CapeChoices.style();
            held.edits.apply();
            boolean dirty = style != CapeChoices.style();
            CapeBook.preview = true;
            if (dirty) {
                busy = true;
                Minecraft.getInstance().execute(() -> {
                    busy = false;
                    Minecraft client = Minecraft.getInstance();
                    int tab = client.screen instanceof ClothConfigScreen cloth ? cloth.selectedCategoryIndex : 0;
                    client.setScreen(CapeScreens.openTab(parent, tab));
                });
            }
        }

    }

    private static class ActionRow extends TooltipListEntry<Object> {
        protected final Button button;

        private ActionRow(String key, Runnable action) {
            super(Component.translatable(key), Optional::empty, false);
            this.button = Button.builder(Component.translatable(key), pressed -> action.run()).bounds(0, 0, 140, 20).build();
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
            button.setWidth(Math.min(140, entryWidth));
            button.setX(x);
            button.setY(y);
            button.render(graphics, mouseX, mouseY, delta);
        }

        @Override
        public int getItemHeight() {
            return 24;
        }

        @Override
        public Object getValue() {
            return null;
        }

        @Override
        public Optional<Object> getDefaultValue() {
            return Optional.empty();
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(button);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(button);
        }
    }

    private static final class SampleRow extends ActionRow {
        private final String sample;
        private SampleRow(String sample) {
            super("text.quarkos_capes.sample." + sample, () -> {
                CapeBoard.equipSample(sample);
                Minecraft.getInstance().setScreen(CapeScreens.openTab(parentScreen, 0));
            });
            this.sample = sample;
        }
        @Override public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
            var font = Minecraft.getInstance().font;
            var texture = switch (sample) {
                case "slide" -> dev.quarkos.capes.client.render.CapeTextures.slideTexture();
                case "cloak" -> dev.quarkos.capes.client.render.CapeTextures.cloakTexture();
                default -> dev.quarkos.capes.client.render.CapeTextures.bandsTexture();
            };
            McBridge.capeThumb(graphics, texture, x, y + 2, 20, 32, sample.equals("slide") ? 256 : 64, 32, 64, 32);
            graphics.drawString(font, font.plainSubstrByWidth(getFieldName().getString(), Math.max(1, entryWidth - 120)), x + 28, y + 14, 0xFFFFFFFF);
            button.active = !CapeBoard.sampleOn(sample);
            button.setMessage(Component.translatable(button.active ? "text.quarkos_capes.gallery.equip" : "text.quarkos_capes.gallery.equipped"));
            button.setWidth(80); button.setX(x + Math.max(28, entryWidth - 84)); button.setY(y + 8);
            button.render(graphics, mouseX, mouseY, delta);
        }
        @Override public int getItemHeight() { return 40; }
    }

    private static void attach(Screen screen, AbstractWidget widget) {
        try {
            java.lang.reflect.Method method = Screen.class.getDeclaredMethod(
                    "addRenderableWidget",
                    net.minecraft.client.gui.components.events.GuiEventListener.class);
            method.setAccessible(true);
            method.invoke(screen, widget);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not attach the cape preview", failure);
        }
    }
}
^///?}
*///?}
