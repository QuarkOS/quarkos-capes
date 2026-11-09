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
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class ClothCapeScreen {
    private static boolean busy;
    private static String note = "";

    private ClothCapeScreen() {
    }

    static Screen create(Screen parent) {
        ClientSettings settings = ClientSettings.get();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("text.quarkos_capes.title"))
                .setDoesConfirmSave(false)
                .setAlwaysShowTabs(true)
                .setSavingRunnable(() -> CapeBoard.save(value -> note = value));
        ConfigEntryBuilder fields = builder.entryBuilder();
        Held held = new Held();
        ConfigCategory mine = category(builder, "text.quarkos_capes.tab.mine");
        held.bands = toggle(fields, mine, "text.quarkos_capes.sample.bands", CapeBoard.sampleOn("bands"), true);
        held.slide = toggle(fields, mine, "text.quarkos_capes.sample.slide", CapeBoard.sampleOn("slide"), false);
        held.cloak = toggle(fields, mine, "text.quarkos_capes.sample.cloak", CapeBoard.sampleOn("cloak"), false);
        held.save = toggle(fields, mine, "text.quarkos_capes.button.save", false, false);

        ConfigCategory style = category(builder, "text.quarkos_capes.tab.style");
        held.flat = toggle(fields, style, "text.quarkos_capes.style.flat", CapeBoard.styleOn("flat"), true);
        held.frames = toggle(fields, style, "text.quarkos_capes.style.frames", CapeBoard.styleOn("frames"), false);
        held.wave = toggle(fields, style, "text.quarkos_capes.style.wave", CapeBoard.styleOn("wave"), false);
        held.model = toggle(fields, style, "text.quarkos_capes.style.model", CapeBoard.styleOn("model"), false);
        held.showBack = toggle(fields, style, "text.quarkos_capes.preview.back", CapeBook.previewBack, true);
        held.animation = toggle(fields, style, "text.quarkos_capes.toggle.animation", OwnerCape.get().animateFrames, false);

        ConfigCategory physics = category(builder, "text.quarkos_capes.tab.physics");
        held.classic = toggle(fields, physics, "text.quarkos_capes.movement.classic", CapeBoard.movementOn(WaveCloth.Movement.CLASSIC), false);
        held.simulation = toggle(fields, physics, "text.quarkos_capes.movement.simulation", CapeBoard.movementOn(WaveCloth.Movement.SIMULATION), false);
        held.simulationV2 = toggle(fields, physics, "text.quarkos_capes.movement.simulation_v2", CapeBoard.movementOn(WaveCloth.Movement.SIMULATION_V2), true);
        held.dungeons = toggle(fields, physics, "text.quarkos_capes.movement.dungeons", CapeBoard.movementOn(WaveCloth.Movement.DUNGEONS), false);
        held.smooth = toggle(fields, physics, "text.quarkos_capes.mesh.smooth", CapeBoard.meshOn(WaveCloth.Mesh.SMOOTH), true);
        held.blocky = toggle(fields, physics, "text.quarkos_capes.mesh.blocky", CapeBoard.meshOn(WaveCloth.Mesh.BLOCKY), false);
        held.windOff = toggle(fields, physics, "text.quarkos_capes.wind.none", CapeBoard.windOn(WaveCloth.Wind.NONE), true);
        held.waves = toggle(fields, physics, "text.quarkos_capes.wind.waves", CapeBoard.windOn(WaveCloth.Wind.WAVES), false);
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
                .setDefaultValue("http://127.0.0.1:8787")
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

    private static BooleanListEntry toggle(ConfigEntryBuilder fields, ConfigCategory category, String key, boolean on, boolean fallback) {
        String name = Component.translatable(key).getString();
        BooleanListEntry entry = fields.startBooleanToggle(Component.translatable(key), on)
                .setDefaultValue(fallback)
                .setTooltip(Component.translatable(key.contains(".sample.") ? "text.quarkos_capes.tooltip.equip" : key.contains(".button.") ? key : "text.quarkos_capes.tooltip." + key.substring(key.lastIndexOf('.') + 1)))
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
        private BooleanListEntry flat;
        private BooleanListEntry frames;
        private BooleanListEntry wave;
        private BooleanListEntry model;
        private BooleanListEntry animation;
        private BooleanListEntry showBack;
        private BooleanListEntry classic;
        private BooleanListEntry simulation;
        private BooleanListEntry simulationV2;
        private BooleanListEntry dungeons;
        private BooleanListEntry smooth;
        private BooleanListEntry blocky;
        private BooleanListEntry windOff;
        private BooleanListEntry waves;
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
            if (!note.isBlank()) graphics.drawString(minecraft.font,
                    minecraft.font.plainSubstrByWidth(note, Math.max(20, minecraft.screen.width - 16)),
                    8, minecraft.screen.height - 42, 0xFFE8B84A);
            if (minecraft.screen instanceof ClothConfigScreen cloth) {
                cloth.listWidget.updateSize(Math.max(150, cloth.width - 120), cloth.listWidget.height, cloth.listWidget.top, cloth.listWidget.bottom);
            }
            if (minecraft.player != null) {
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
            String style = exclusive(new BooleanListEntry[] {held.flat, held.frames, held.wave, held.model},
                    new String[] {"flat", "frames", "wave", "model"}, OwnerCape.get().style);
            if (style != null && !style.equals(OwnerCape.get().style)) {
                CapeBoard.style(style);
                dirty = true;
            } else if (!groupMatches(new BooleanListEntry[] {held.flat, held.frames, held.wave, held.model},
                    new String[] {"flat", "frames", "wave", "model"}, OwnerCape.get().style)) {
                dirty = true;
            }
            if (Boolean.TRUE.equals(held.animation.getValue()) != OwnerCape.get().animateFrames) {
                CapeBoard.animation(Boolean.TRUE.equals(held.animation.getValue()));
            }
            WaveCloth.Movement movement = movementPick();
            if (movement != null && !CapeBoard.movementOn(movement)) {
                CapeBoard.movement(movement);
                dirty = true;
            }
            WaveCloth.Mesh mesh = Boolean.TRUE.equals(held.blocky.getValue()) && !CapeBoard.meshOn(WaveCloth.Mesh.BLOCKY)
                    ? WaveCloth.Mesh.BLOCKY
                    : Boolean.TRUE.equals(held.smooth.getValue()) && !CapeBoard.meshOn(WaveCloth.Mesh.SMOOTH)
                    ? WaveCloth.Mesh.SMOOTH : null;
            if (mesh != null) {
                CapeBoard.mesh(mesh);
                dirty = true;
            }
            if (Boolean.TRUE.equals(held.waves.getValue()) && !CapeBoard.windOn(WaveCloth.Wind.WAVES)) {
                CapeBoard.wind(WaveCloth.Wind.WAVES);
                dirty = true;
            } else if (Boolean.TRUE.equals(held.windOff.getValue()) && !CapeBoard.windOn(WaveCloth.Wind.NONE)) {
                CapeBoard.wind(WaveCloth.Wind.NONE);
                dirty = true;
            }
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

        private String exclusive(BooleanListEntry[] entries, String[] ids, String current) {
            for (int i = 0; i < entries.length; i++) {
                if (Boolean.TRUE.equals(entries[i].getValue()) && !ids[i].equals(current)) {
                    return ids[i];
                }
            }
            return null;
        }

        private boolean groupMatches(BooleanListEntry[] entries, String[] ids, String current) {
            for (int i = 0; i < entries.length; i++) {
                if (Boolean.TRUE.equals(entries[i].getValue()) != ids[i].equals(current)) {
                    return false;
                }
            }
            return true;
        }

        private WaveCloth.Movement movementPick() {
            if (Boolean.TRUE.equals(held.classic.getValue()) && !CapeBoard.movementOn(WaveCloth.Movement.CLASSIC)) {
                return WaveCloth.Movement.CLASSIC;
            }
            if (Boolean.TRUE.equals(held.simulation.getValue()) && !CapeBoard.movementOn(WaveCloth.Movement.SIMULATION)) {
                return WaveCloth.Movement.SIMULATION;
            }
            if (Boolean.TRUE.equals(held.dungeons.getValue()) && !CapeBoard.movementOn(WaveCloth.Movement.DUNGEONS)) {
                return WaveCloth.Movement.DUNGEONS;
            }
            if (Boolean.TRUE.equals(held.simulationV2.getValue()) && !CapeBoard.movementOn(WaveCloth.Movement.SIMULATION_V2)) {
                return WaveCloth.Movement.SIMULATION_V2;
            }
            return null;
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
