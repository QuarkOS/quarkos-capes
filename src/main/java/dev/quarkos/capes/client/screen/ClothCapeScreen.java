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
    private static String note = "";

    private ClothCapeScreen() {
    }

    static Screen refreshed(ClothConfigScreen previous) {
        int tab = previous.selectedCategoryIndex;
        Screen fresh = create(parentScreen);
        if (fresh instanceof ClothConfigScreen cloth) cloth.selectedCategoryIndex = tab;
        return fresh;
    }

    static Screen create(Screen parent) {
        parentScreen = parent;
        ClientSettings settings = ClientSettings.get();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("text.quarkos_capes.title"))
                .setDoesConfirmSave(false)
                .setAlwaysShowTabs(true)
                .setTransparentBackground(true)
                .setSavingRunnable(() -> CapeBoard.save(value -> note = value));
        ConfigEntryBuilder fields = builder.entryBuilder();
        Held held = new Held();
        ConfigCategory mine = category(builder, "text.quarkos_capes.tab.mine");
        held.bands = toggle(fields, mine, "text.quarkos_capes.sample.bands", CapeBoard.sampleOn("bands"), true);
        held.slide = toggle(fields, mine, "text.quarkos_capes.sample.slide", CapeBoard.sampleOn("slide"), false);
        held.cloak = toggle(fields, mine, "text.quarkos_capes.sample.cloak", CapeBoard.sampleOn("cloak"), false);
        held.save = toggle(fields, mine, "text.quarkos_capes.button.save", false, false);

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
            held.labyPrevious = toggle(fields, sources, "text.quarkos_capes.laby.previous", false, false);
            held.labyNext = toggle(fields, sources, "text.quarkos_capes.laby.next", false, false);
            if (!LabyBrowse.status().isBlank()) {
                sources.addEntry(fields.startTextDescription(Component.literal(LabyBrowse.status())).build());
            }
        }
¹^///?}


        ConfigCategory gallery = category(builder, "text.quarkos_capes.tab.gallery");
        held.publish = toggle(fields, gallery, "text.quarkos_capes.button.publish", false, false);
        held.unpublish = toggle(fields, gallery, "text.quarkos_capes.button.unpublish", false, false);
        held.report = toggle(fields, gallery, "text.quarkos_capes.gallery.report", false, false);

        ConfigCategory advanced = category(builder, "text.quarkos_capes.tab.advanced");
        held.others = toggle(fields, advanced, "text.quarkos_capes.toggle.show_others", settings.showOthers, true);
        held.offline = toggle(fields, advanced, "text.quarkos_capes.toggle.local_only", settings.localOnly, true);
        held.elytra = toggle(fields, advanced, "text.quarkos_capes.toggle.elytra", settings.enableElytraTexture, true);
        advanced.addEntry(fields.startStrField(Component.translatable("text.quarkos_capes.field.base_url"), settings.baseUrl)
                .setDefaultValue(dev.quarkos.capes.client.net.CapeCalls.DEFAULT_SERVICE_URL)
                .setSaveConsumer(CapeBoard::baseUrl)
                .build());

        category(builder, "text.quarkos_capes.site.browse").addEntry(fields.startTextDescription(Component.translatable("text.quarkos_capes.site.browse")).build());

        builder.setAfterInitConsumer(screen -> {
            PreviewPane pane = new PreviewPane(parent, held);
            pane.setX(Math.max(8, screen.width - 110));
            pane.setY(28);
            attach(screen, pane);
        });
        return builder.build();
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
                .setSaveConsumer(value -> {
                })
                .build();
        category.addEntry(entry);
        return entry;
    }

    private static IntegerSliderEntry slider(ConfigEntryBuilder fields, ConfigCategory category, String name, int value, int min, int max, int fallback) {
        IntegerSliderEntry entry = fields.startIntSlider(Component.translatable(name), value, min, max)
                .setDefaultValue(fallback)
                .setTextGetter(current -> Component.translatable(name).append(" " + current))
                .setSaveConsumer(current -> {
                })
                .build();
        category.addEntry(entry);
        return entry;
    }

    private static final class Held {
        private BooleanListEntry bands;
        private BooleanListEntry slide;
        private BooleanListEntry cloak;
        private BooleanListEntry save;
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
        private BooleanListEntry labyPrevious;
        private BooleanListEntry labyNext;
        private BooleanListEntry publish;
        private BooleanListEntry unpublish;
        private BooleanListEntry report;
        private BooleanListEntry others;
        private BooleanListEntry offline;
        private BooleanListEntry elytra;
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
                McBridge.preview(graphics, getX(), getY() + 16, minecraft.screen.width - 8, minecraft.screen.height - 60, minecraft.player);
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
            boolean dirty = false;
            String sample = exclusiveSample();
            if (sample != null && !CapeBoard.sampleOn(sample)) {
                CapeBoard.equipSample(sample);
                dirty = true;
            } else if (!sampleMatches()) {
                dirty = true;
            }
            if (held.style.getValue() != CapeChoices.style()) {
                CapeBoard.style(CapeChoices.id(held.style.getValue()));
                dirty = true;
            } else CapeBoard.animation(Boolean.TRUE.equals(held.animation.getValue()));
            CapeBoard.movement(held.movement.getValue());
            CapeBoard.mesh(held.mesh.getValue());
            CapeBoard.wind(held.wind.getValue());
            if (held.gravity.getValue() != ClientSettings.get().cloth.gravity()) {
                CapeBoard.gravity(held.gravity.getValue());
            }
            if (held.height.getValue() != ClientSettings.get().cloth.heightMultiplier()) {
                CapeBoard.height(held.height.getValue());
            }
            if (held.strafe.getValue() != ClientSettings.get().cloth.straveMultiplier()) {
                CapeBoard.strafe(held.strafe.getValue());
            }
            if (Boolean.TRUE.equals(held.vector.getValue()) != ClientSettings.get().cloth.computeGravityVector()) {
                CapeBoard.gravityVector(Boolean.TRUE.equals(held.vector.getValue()));
            }
            CapeBoard.provider("optifine", Boolean.TRUE.equals(held.optifine.getValue()));
            CapeBoard.provider("labymod", Boolean.TRUE.equals(held.labymod.getValue()));
            CapeBoard.provider("minecraftcapes", Boolean.TRUE.equals(held.minecraftCapes.getValue()));
            CapeBoard.provider("cosmetica", Boolean.TRUE.equals(held.cosmetica.getValue()));
            CapeBoard.provider("cloaksplus", Boolean.TRUE.equals(held.cloaks.getValue()));
            CapeBoard.showOthers(Boolean.TRUE.equals(held.others.getValue()));
            CapeBoard.localOnly(Boolean.TRUE.equals(held.offline.getValue()));
            CapeBoard.elytra(Boolean.TRUE.equals(held.elytra.getValue()));
//? if dev_catalog {
            /^¹if (held.labyPrevious != null && Boolean.TRUE.equals(held.labyPrevious.getValue())) {
                busy = true;
                LabyBrowse.previous(() -> Minecraft.getInstance().execute(() -> {
                    busy = false;
                    Minecraft client = Minecraft.getInstance();
                    int tab = client.screen instanceof ClothConfigScreen cloth ? cloth.selectedCategoryIndex : 0;
                    client.setScreen(CapeScreens.openTab(parent, tab));
                }));
                return;
            }
            if (held.labyNext != null && Boolean.TRUE.equals(held.labyNext.getValue())) {
                busy = true;
                LabyBrowse.next(() -> Minecraft.getInstance().execute(() -> {
                    busy = false;
                    Minecraft client = Minecraft.getInstance();
                    int tab = client.screen instanceof ClothConfigScreen cloth ? cloth.selectedCategoryIndex : 0;
                    client.setScreen(CapeScreens.openTab(parent, tab));
                }));
                return;
            }

¹^///?}
            if (Boolean.TRUE.equals(held.save.getValue())) {
                CapeBoard.save(value -> note = value);
                dirty = true;
            }
            if (Boolean.TRUE.equals(held.publish.getValue())) {
                CapeBoard.publish(ignored -> {
                });
                dirty = true;
            }
            if (Boolean.TRUE.equals(held.unpublish.getValue())) {
                CapeBoard.unpublish(ignored -> {
                });
                dirty = true;
            }
            if (Boolean.TRUE.equals(held.report.getValue())) {
                CapeBoard.report(null, ignored -> {
                });
                dirty = true;
            }
            CapeBook.previewBack = Boolean.TRUE.equals(held.showBack.getValue());
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

        private String exclusiveSample() {
            if (turnedOn(held.slide, "slide")) {
                return "slide";
            }
            if (turnedOn(held.cloak, "cloak")) {
                return "cloak";
            }
            if (turnedOn(held.bands, "bands")) {
                return "bands";
            }
            return null;
        }

        private boolean turnedOn(BooleanListEntry entry, String id) {
            return Boolean.TRUE.equals(entry.getValue()) && !CapeBoard.sampleOn(id);
        }

        private boolean sampleMatches() {
            return Boolean.TRUE.equals(held.bands.getValue()) == CapeBoard.sampleOn("bands")
                    && Boolean.TRUE.equals(held.slide.getValue()) == CapeBoard.sampleOn("slide")
                    && Boolean.TRUE.equals(held.cloak.getValue()) == CapeBoard.sampleOn("cloak");
        }

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
