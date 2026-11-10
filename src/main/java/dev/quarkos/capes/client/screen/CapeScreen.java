//? if fabric {
package dev.quarkos.capes.client.screen;

import dev.quarkos.capes.client.McBridge;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.LocalFiles;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.net.CapeHttp;
import dev.quarkos.capes.client.net.GalleryPages;
//? if dev_catalog {
/*import dev.quarkos.capes.client.net.LabyBrowse;
*///?}
//? if dev_catalog {
/*import dev.quarkos.capes.client.net.LabyCatalog;
*///?}
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.render.CapeTextures;
import dev.quarkos.capes.sim.WaveCloth;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerSliderEntry;
import me.shedaniel.clothconfig2.gui.entries.StringListEntry;
import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/** Cloth settings with seven tabs and a responsive player preview. */
public final class CapeScreen {
    public static Screen current;
    private static Screen parent;
    private static boolean rebuilding;
    private static SettingsEdits edits;
    private static String note = "";
    private static String query = "";
    private static int requestGeneration;
    private static List<GalleryPages.Entry> results = List.of();

    private static EnumListEntry<CapeChoices.Style> styleChoice;
    private static EnumListEntry<WaveCloth.Movement> modelChoice;
    private static EnumListEntry<WaveCloth.Mesh> lookChoice;
    private static EnumListEntry<WaveCloth.Wind> windChoice;
    private static BooleanListEntry gravityVector;
    private static BooleanListEntry showOthers;
    private static BooleanListEntry localOnly;
    private static BooleanListEntry elytra;
    private static BooleanListEntry optifine;
    private static BooleanListEntry laby;
    private static BooleanListEntry minecraftCapes;
    private static BooleanListEntry cosmetica;
    private static BooleanListEntry cloaks;
    private static BooleanListEntry animation;
    private static BooleanListEntry showBack;
    private static IntegerSliderEntry gravity;
    private static IntegerSliderEntry height;
    private static IntegerSliderEntry strave;
    private static StringListEntry baseUrl;
    private static EditBox gallerySearch;

    private CapeScreen() {
    }

    public static Screen create(Screen parentScreen) {
        return create(parentScreen, null);
    }

    public static Screen create(Screen parentScreen, Component select) {
        parent = parentScreen;
        edits = new SettingsEdits();
        ClientSettings settings = ClientSettings.get();
        OwnerCape cape = OwnerCape.get();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parentScreen)
                .setTitle(Component.translatable("text.quarkos_capes.title"))
                .setEditable(true)
                .setTransparentBackground(true)
                .setSavingRunnable(CapeScreen::save);
        ConfigEntryBuilder fields = builder.entryBuilder();
        Component myCape = Component.translatable("text.quarkos_capes.tab.my_cape");
        Component style = Component.translatable("text.quarkos_capes.tab.style");
        Component physics = Component.translatable("text.quarkos_capes.tab.physics");
        Component sources = Component.translatable("text.quarkos_capes.tab.sources");
        Component gallery = Component.translatable("text.quarkos_capes.tab.gallery");
        Component advanced = Component.translatable("text.quarkos_capes.tab.advanced");

        ConfigCategory myCategory = builder.getOrCreateCategory(myCape);
        myCategory.addEntry(new SummaryRow());
        if (!note.isEmpty()) {
            myCategory.addEntry(fields.startTextDescription(Component.literal(note)).build());
        }
        myCategory.addEntry(new SampleRow(Component.translatable("text.quarkos_capes.sample.bands"), CapeTextures.bandsTexture(), 64, 32, CapeActions::bands));
        myCategory.addEntry(new SampleRow(Component.translatable("text.quarkos_capes.sample.slide"), CapeTextures.slideTexture(), 64, 32, 256, 32, CapeActions::slide));
        myCategory.addEntry(new SampleRow(Component.translatable("text.quarkos_capes.sample.cloak"), CapeTextures.cloakTexture(), 64, 32, CapeActions::cloak));
        myCategory.addEntry(new ActionRow(Component.translatable("text.quarkos_capes.button.save"), CapeScreen::save));
        myCategory.addEntry(new ActionRow(Component.translatable("text.quarkos_capes.button.publish"), () -> CapeBoard.publish(CapeScreen::statusNote)));
        myCategory.addEntry(new ActionRow(Component.translatable("text.quarkos_capes.button.unpublish"), () -> CapeBoard.unpublish(CapeScreen::statusNote)));

        ConfigCategory styleCategory = builder.getOrCreateCategory(style);
        styleChoice = choice(fields, "style", CapeChoices.Style.class, CapeChoices.style());
        animation = toggle(fields, "text.quarkos_capes.toggle.animation", cape.animateFrames);
        showBack = toggle(fields, "text.quarkos_capes.preview.back", CapeBook.previewBack);
        styleCategory.addEntry(styleChoice);
        styleCategory.addEntry(animation);
        styleCategory.addEntry(showBack);

        WaveCloth.Settings cloth = settings.cloth;
        ConfigCategory physicsCategory = builder.getOrCreateCategory(physics);
        modelChoice = choice(fields, "model", WaveCloth.Movement.class, cloth.movement());
        lookChoice = choice(fields, "look", WaveCloth.Mesh.class, cloth.mesh());
        windChoice = choice(fields, "wind", WaveCloth.Wind.class, cloth.wind());
        gravity = slider(fields, "text.quarkos_capes.physics.gravity", cloth.gravity(), 5, 32);
        height = slider(fields, "text.quarkos_capes.physics.height", cloth.heightMultiplier(), 4, 16);
        strave = slider(fields, "text.quarkos_capes.physics.strafe", cloth.straveMultiplier(), 1, 8);
        gravityVector = toggle(fields, "text.quarkos_capes.toggle.gravity_vector", cloth.computeGravityVector());
        physicsCategory.addEntry(modelChoice);
        physicsCategory.addEntry(lookChoice);
        physicsCategory.addEntry(windChoice);
        physicsCategory.addEntry(gravity);
        physicsCategory.addEntry(height);
        physicsCategory.addEntry(strave);
        physicsCategory.addEntry(gravityVector);

        ConfigCategory sourcesCategory = builder.getOrCreateCategory(sources);
        optifine = toggle(fields, "text.quarkos_capes.toggle.optifine", settings.enableOptifine);
        laby = toggle(fields, "text.quarkos_capes.toggle.labymod", settings.enableLabyMod);
        minecraftCapes = toggle(fields, "text.quarkos_capes.toggle.minecraftcapes", settings.enableMinecraftCapesMod);
        cosmetica = toggle(fields, "text.quarkos_capes.toggle.cosmetica", settings.enableCosmetica);
        cloaks = toggle(fields, "text.quarkos_capes.toggle.cloaksplus", settings.enableCloaksPlus);
        sourcesCategory.addEntry(fields.startTextDescription(Component.translatable("text.quarkos_capes.sources.help")).build());
        sourcesCategory.addEntry(optifine);
        sourcesCategory.addEntry(laby);
        sourcesCategory.addEntry(minecraftCapes);
        sourcesCategory.addEntry(cosmetica);
        sourcesCategory.addEntry(cloaks);
//? if dev_catalog {
        /*if (LabyCatalog.enabled()) {
            Component sourcesTab = sources;
            sourcesCategory.addEntry(new ActionRow(Component.translatable("text.quarkos_capes.laby.previous"), () -> LabyBrowse.previous(() -> reopen(sourcesTab))));
            sourcesCategory.addEntry(new ActionRow(Component.translatable("text.quarkos_capes.laby.next"), () -> LabyBrowse.next(() -> reopen(sourcesTab))));
            if (!LabyBrowse.status().isBlank()) {
                sourcesCategory.addEntry(fields.startTextDescription(Component.literal(LabyBrowse.status())).build());
            }
        }
*///?}


        ConfigCategory galleryCategory = builder.getOrCreateCategory(gallery);
        if (!note.isEmpty()) {
            galleryCategory.addEntry(fields.startTextDescription(Component.literal(note)).build());
        }
        if (settings.localOnly) {
            galleryCategory.addEntry(new OfflineGalleryRow(() -> {
                CapeBoard.localOnly(false);
                CapeBoard.save(CapeScreen::statusNote);
                reopen(gallery);
                searchFor(query);
            }));
        } else {
            galleryCategory.addEntry(new ActionRow(Component.translatable("text.quarkos_capes.button.search_now"), CapeScreen::searchNow));
            String localAuthor = localAuthor();
            String bands = Component.translatable("text.quarkos_capes.sample.bands").getString();
            String slide = Component.translatable("text.quarkos_capes.sample.slide").getString();
            String cloak = Component.translatable("text.quarkos_capes.sample.cloak").getString();
            List<GalleryRows.Shared> shared = new java.util.ArrayList<>();
            for (GalleryPages.Entry entry : results) {
                String title = entry.title() == null || entry.title().isBlank() ? entry.id() : entry.title();
                shared.add(new GalleryRows.Shared(entry.id(), title, entry.author()));
            }
            for (GalleryRows.Row row : GalleryRows.visible(query, List.of(
                    new GalleryRows.Local(bands, localAuthor),
                    new GalleryRows.Local(slide, localAuthor),
                    new GalleryRows.Local(cloak, localAuthor)
            ), shared)) {
                if ("local".equals(row.tag()) && slide.equals(row.name())) {
                    galleryCategory.addEntry(new SampleRow(Component.literal(row.name()), row.author(), row.tag(), CapeTextures.slideTexture(), 64, 32, 256, 32, CapeActions::slide));
                } else if ("local".equals(row.tag()) && cloak.equals(row.name())) {
                    galleryCategory.addEntry(new SampleRow(Component.literal(row.name()), row.author(), row.tag(), CapeTextures.cloakTexture(), 64, 32, CapeActions::cloak));
                } else if ("local".equals(row.tag())) {
                    galleryCategory.addEntry(new SampleRow(Component.literal(row.name()), row.author(), row.tag(), CapeTextures.bandsTexture(), 64, 32, CapeActions::bands));
                } else {
                    String id = shared.stream().filter(item -> item.name().equals(row.name()) && item.author().equals(row.author()))
                            .map(GalleryRows.Shared::id).findFirst().orElse(null);
                    galleryCategory.addEntry(new SampleRow(Component.literal(row.name()), row.author(), row.tag(), null, 0, 0, () -> equipShared(id)));
                }
            }
            galleryCategory.addEntry(new ActionRow(Component.translatable("text.quarkos_capes.gallery.report"), CapeScreen::reportFirst));

        }

        ConfigCategory advancedCategory = builder.getOrCreateCategory(advanced);
        showOthers = toggle(fields, "text.quarkos_capes.toggle.show_others", settings.showOthers);
        localOnly = toggle(fields, "text.quarkos_capes.toggle.local_only", settings.localOnly);
        elytra = toggle(fields, "text.quarkos_capes.toggle.elytra", settings.enableElytraTexture);
        baseUrl = fields.startStrField(Component.translatable("text.quarkos_capes.field.base_url"), settings.baseUrl).setDefaultValue(settings.baseUrl)
                .setErrorSupplier(value -> dev.quarkos.capes.client.net.ServiceUrl.valid(value) ? Optional.empty() : Optional.of(Component.translatable("text.quarkos_capes.status.invalid_url"))).build();
        advancedCategory.addEntry(showOthers);
        advancedCategory.addEntry(localOnly);
        advancedCategory.addEntry(elytra);
        advancedCategory.addEntry(baseUrl);

        edits.track(styleChoice::getValue, value -> CapeBoard.style(CapeChoices.id(value)));
        edits.track(animation::getValue, CapeBoard::animation);
        edits.track(showBack::getValue, value -> CapeBook.previewBack = value);
        edits.track(modelChoice::getValue, CapeBoard::movement);
        edits.track(lookChoice::getValue, CapeBoard::mesh);
        edits.track(windChoice::getValue, CapeBoard::wind);
        edits.track(gravity::getValue, CapeBoard::gravity);
        edits.track(height::getValue, CapeBoard::height);
        edits.track(strave::getValue, CapeBoard::strafe);
        edits.track(gravityVector::getValue, CapeBoard::gravityVector);
        edits.track(showOthers::getValue, CapeBoard::showOthers);
        edits.track(localOnly::getValue, CapeBoard::localOnly);
        edits.track(elytra::getValue, CapeBoard::elytra);
        edits.track(optifine::getValue, value -> CapeBoard.provider("optifine", value));
        edits.track(laby::getValue, value -> CapeBoard.provider("labymod", value));
        edits.track(minecraftCapes::getValue, value -> CapeBoard.provider("minecraftcapes", value));
        edits.track(cosmetica::getValue, value -> CapeBoard.provider("cosmetica", value));
        edits.track(cloaks::getValue, value -> CapeBoard.provider("cloaksplus", value));
        edits.track(baseUrl::getValue, CapeBoard::baseUrl);

        builder.getOrCreateCategory(Component.translatable("text.quarkos_capes.site.browse"))
                .addEntry(new ActionRow(Component.translatable("text.quarkos_capes.site.browse"),
                        () -> Minecraft.getInstance().setScreen(new BrowseScreen(current))));

        Component initial = select == null ? myCape : select;
        builder.setFallbackCategory(builder.getOrCreateCategory(initial));
        builder.setAfterInitConsumer(screen -> open(screen, initial));
        Screen built = builder.build();
        if (built instanceof ClothConfigScreen config) {
            int index = 0;
            for (Component key : config.getCategorizedEntries().keySet()) {
                if (initial.getString().equals(key.getString())) {
                    config.selectedCategoryIndex = index;
                    break;
                }
                index++;
            }
        }
        current = built;
        return built;
    }

    public static Screen refreshed(Screen screen) {
        return create(parent, screen instanceof ClothConfigScreen cloth ? cloth.getSelectedCategory() : null);
    }

    public static boolean isOpen(Screen screen) {
        return screen != null && screen == current;
    }

    public static String note() {
        return note;
    }

    public static List<GalleryPages.Entry> results() {
        return results;
    }

    /** Rebuilds the Gallery tab so a harness status line is on screen. */
    public static void showNote(String value) {
        note = value == null ? "" : value;
        Minecraft.getInstance().setScreen(create(parent, Component.translatable("text.quarkos_capes.tab.gallery")));
    }

    public static void searchFor(String queryText) {
        query = queryText == null ? "" : queryText;
        int request = ++requestGeneration;
        Screen source = Minecraft.getInstance().screen;
        String submitted = query;
        String category = source instanceof ClothConfigScreen cloth ? cloth.getSelectedCategory().getString() : "";
        if (ClientSettings.get().localOnly) {
            note = Component.translatable("text.quarkos_capes.gallery.quiet").getString();
            results = List.of();
            reopen(Component.translatable("text.quarkos_capes.tab.gallery"));
            return;
        }
        note = Component.translatable("text.quarkos_capes.status.loading").getString();
        CapeHttp.gallery(submitted, "", 8, "", page -> {
            if (request != requestGeneration || !sameCategory(source, category)) return;
            results = page.entries();
            note = Component.translatable("text.quarkos_capes.gallery.search_result", submitted, results.size()).getString();
            reopen(Component.translatable("text.quarkos_capes.tab.gallery"));
        }, value -> {
            if (request != requestGeneration || !sameCategory(source, category)) return;
            results = List.of(); note = value;
            reopen(Component.translatable("text.quarkos_capes.tab.gallery"));
        });
    }

    private static boolean sameCategory(Screen source, String category) {
        return Minecraft.getInstance().screen == source && (!(source instanceof ClothConfigScreen cloth)
                || cloth.getSelectedCategory().getString().equals(category));
    }

    private static void equipShared(String id) {
        Screen source = Minecraft.getInstance().screen;
        note = Component.translatable("text.quarkos_capes.status.loading").getString();
        CapeHttp.open(id, opened -> {
            if (Minecraft.getInstance().screen != source) return;
            CapeHttp.equip(opened.detail(), opened.png());
            CapeBoard.save(CapeScreen::statusNote);
        }, CapeScreen::statusNote);
    }

    public static void reportListed() {
        reportFirst();
    }

    private static void open(Screen screen, Component initial) {
        current = screen;
        if (screen instanceof ClothConfigScreen cloth && cloth.listWidget != null) {
            var list = cloth.listWidget;
            int bottom = Math.max(list.top + 40, screen.height - 26 - 10);
            list.updateSize(Math.max(120, screen.width - previewWidth(screen) - 16), bottom - list.top, list.top, bottom);
        }
        CapeBook.preview = true;
        CapeBook.draft = OwnerCape.get();
        placeQuery(screen);
        ScreenEvents.beforeRender(screen).register((view, graphics, mouseX, mouseY, delta) -> {
            graphics.fill(0, 0, view.width, view.height, 0xFF10141C);
            compactHint = null;
        });
        ScreenEvents.afterRender(screen).register((current, graphics, mouseX, mouseY, delta) -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.screen != current) {
                return;
            }
            if (current instanceof ClothConfigScreen cloth && cloth.getSelectedCategory().getString().equals(Component.translatable("text.quarkos_capes.site.browse").getString())) {
                minecraft.execute(() -> minecraft.setScreen(new BrowseScreen(create(parent, Component.translatable("text.quarkos_capes.tab.my_cape")))));
                return;
            }
            pull();
            CapeBook.preview = true;
            CapeBook.draft = OwnerCape.get();
            if (previewWidth(current) > 0) McBridge.preview(graphics, previewLeft(current), previewTop(current), previewRight(current), previewBottom(current), minecraft.player);
            if (compactHint != null) graphics.drawString(minecraft.font,
                    minecraft.font.plainSubstrByWidth(compactHint.getString(), current.width - 24), 12, current.height - 40, 0xFFB6C8DD);
            if (!note.isBlank() && compactHint == null) graphics.drawString(minecraft.font,
                    minecraft.font.plainSubstrByWidth(note, current.width - 24), 12, current.height - 40, 0xFFE8B84A);
        });
    }

    private static void pull() {
        if (rebuilding) {
            return;
        }
        CapeChoices.Style style = CapeChoices.style();
        boolean offline = settings().localOnly;
        edits.apply();
        if (style != CapeChoices.style() || offline != settings().localOnly) {
            Component category = current instanceof ClothConfigScreen config ? config.getSelectedCategory()
                    : Component.translatable("text.quarkos_capes.tab.my_cape");
            reopen(category);
        }
    }

    private static void reopen(Component category) {
        rebuilding = true;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(create(parent, category));
        rebuilding = false;
    }

    private static void searchNow() {
        searchFor(gallerySearch == null ? query : gallerySearch.getValue());
    }

    private static void reportFirst() {
        if (ClientSettings.get().equippedGalleryId == null) {
            note = Component.translatable("text.quarkos_capes.status.select_report").getString();
            return;
        }
        CapeBoard.report(ClientSettings.get().equippedGalleryId, CapeScreen::statusNote);
    }

    private static void statusNote(String value) {
        note = value;
    }

    private static Component compactHint;

    private static Optional<Component[]> help(Component value) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && (screen.width < 480 || screen.height < 300)) {
            compactHint = value;
            return Optional.empty();
        }
        return Optional.of(new Component[]{value});
    }

    private static <T extends Enum<T>> EnumListEntry<T> choice(ConfigEntryBuilder fields, String key, Class<T> type, T value) {
        return fields.startEnumSelector(Component.translatable("text.quarkos_capes.selector." + key), type, value)
                .setDefaultValue(value).setEnumNameProvider(CapeChoices::name)
                .setTooltipSupplier(selected -> help(CapeChoices.help(selected))).build();
    }

    private static BooleanListEntry toggle(ConfigEntryBuilder fields, String key, boolean value) {
        return fields.startBooleanToggle(Component.translatable(key), value)
                .setDefaultValue(value)
                .setYesNoTextSupplier(on -> Component.translatable(on ? "options.on" : "options.off"))
                .setTooltipSupplier(() -> help(Component.translatable("text.quarkos_capes.tooltip." + key.substring(key.lastIndexOf('.') + 1))))
                .build();
    }

    private static IntegerSliderEntry slider(ConfigEntryBuilder fields, String key, int value, int min, int max) {
        return fields.startIntSlider(Component.translatable(key), value, min, max)
                .setDefaultValue(value)
                .setTextGetter(number -> Component.literal(Integer.toString(number)))
                .setTooltipSupplier(() -> help(Component.translatable("text.quarkos_capes.tooltip." + key.substring(key.lastIndexOf('.') + 1))))
                .build();
    }

    private static void save() {
        pull();
        CapeBoard.save(CapeScreen::statusNote);
    }

    private static ClientSettings settings() {
        return ClientSettings.get();
    }

    private static String localAuthor() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return "";
        }
        return minecraft.player.getName().getString();
    }

    static String shownAuthor(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            String name = McBridge.playerName(java.util.UUID.fromString(raw.trim()));
            return name == null || name.isBlank() ? raw : name;
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }

    /**
     * The query belongs in the top search box. Cloth also hides every row whose name misses
     * the query, and its box runs under the preview, so the gallery keeps its own list and
     * draws that same box only up to the preview.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void placeQuery(Screen screen) {
        gallerySearch = null;
        if (!(screen instanceof ClothConfigScreen cloth) || cloth.listWidget == null) {
            return;
        }
        cloth.listWidget.entriesTransformer = UnaryOperator.identity();
        List children = cloth.listWidget.children();
        for (int i = 0; i < children.size(); i++) {
            Object child = children.get(i);
            if (child == null || !"SearchFieldEntry".equals(child.getClass().getSimpleName())) {
                continue;
            }
            EditBox box = null;
            for (GuiEventListener nested : ((AbstractConfigListEntry<?>) child).children()) {
                if (nested instanceof EditBox edit) {
                    box = edit;
                }
            }
            if (box == null) {
                return;
            }
            gallerySearch = box;
            String shown = query == null ? "" : query;
            if (!shown.equals(box.getValue())) {
                box.setValue(shown);
            }
            box.setSuggestion(shown.isEmpty() ? Component.translatable("text.quarkos_capes.search.placeholder").getString() : null);
            children.remove(i);
            children.add(i, new QueryField(box));
            return;
        }
    }

    public static int[] previewBounds(Screen screen) {
        return previewWidth(screen) == 0 ? null : new int[]{previewLeft(screen),previewTop(screen),previewRight(screen),previewBottom(screen)};
    }

    private static int previewLeft(Screen screen) {
        return previewRight(screen) - previewWidth(screen);
    }

    private static int previewRight(Screen screen) {
        return screen.width - 8;
    }

    private static int previewTop(Screen screen) {
        if (screen instanceof ClothConfigScreen cloth && cloth.listWidget != null) {
            return cloth.listWidget.top + 8;
        }
        return 78;
    }

    private static int previewBottom(Screen screen) {
        int top = previewTop(screen);
        int limit = screen.height - 48;
        if (screen instanceof ClothConfigScreen cloth && cloth.listWidget != null) {
            limit = cloth.listWidget.bottom - 8;
        }
        return Math.min(top + previewHeight(screen), limit);
    }

    private static int previewWidth(Screen screen) {
        return screen.width < 480 || screen.height < 300 ? 0 : Math.max(64, Math.min(96, screen.width / 5));
    }

    private static int previewHeight(Screen screen) {
        return Math.max(96, previewWidth(screen) + 48);
    }

    private static String status() {
        ClientSettings settings = settings();
        String from = Component.translatable("text.quarkos_capes.gallery." + (settings.equippedGalleryId == null ? "local" : "copy")).getString();
        return Component.translatable("text.quarkos_capes.status.summary", Component.translatable("text.quarkos_capes.style." + OwnerCape.get().style), from,
                Component.translatable(settings.showOthers ? "text.quarkos_capes.status.visible" : "text.quarkos_capes.status.hidden"),
                Component.translatable(settings.localOnly ? "options.off" : "options.on")).getString();
    }

    private static final class OfflineGalleryRow extends TooltipListEntry<Object> {
        private final Button online;
        OfflineGalleryRow(Runnable action) {
            super(BrowseScreen.text("offline_title"), Optional::empty, false);
            online=Button.builder(BrowseScreen.text("go_online"), b -> action.run()).bounds(0,0,120,20).build();
        }
        @Override public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
            var font=Minecraft.getInstance().font;
            int center=x+entryWidth/2;
            graphics.drawCenteredString(font,BrowseScreen.text("offline_title"),center,y+8,0xFFFFFFFF);
            graphics.drawCenteredString(font,Component.literal(font.plainSubstrByWidth(Component.translatable("text.quarkos_capes.gallery.quiet").getString(),entryWidth-8)),center,y+24,0xFFB6C8DD);
            online.setX(center-60);online.setY(y+42);online.render(graphics,mouseX,mouseY,delta);
        }
        @Override public int getItemHeight() { return 68; }
        @Override public Object getValue() { return null; }
        @Override public Optional<Object> getDefaultValue() { return Optional.empty(); }
        @Override public List<? extends GuiEventListener> children() { return List.of(online); }
        @Override public List<? extends NarratableEntry> narratables() { return List.of(online); }
    }

    private static final class SummaryRow extends TooltipListEntry<Object> {
        SummaryRow() { super(Component.empty(), Optional::empty, false); }
        @Override public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, font.plainSubstrByWidth(status(), entryWidth - 8), x, y + 6, 0xFFB6C8DD);
        }
        @Override public int getItemHeight() { return 24; }
        @Override public Object getValue() { return null; }
        @Override public Optional<Object> getDefaultValue() { return Optional.empty(); }
        @Override public List<? extends GuiEventListener> children() { return List.of(); }
        @Override public List<? extends NarratableEntry> narratables() { return List.of(); }
    }

    private static final class SampleRow extends TooltipListEntry<Object> {
        private final Button equip;
        private final String author;
        private final String tag;
        private final Identifier texture;
        private final int width;
        private final int height;
        private final int textureWidth;
        private final int textureHeight;

        private SampleRow(Component title, Identifier texture, int width, int height, Runnable equip) {
            this(title, "", "", texture, width, height, width, height, equip);
        }

        private SampleRow(Component title, Identifier texture, int width, int height, int textureWidth, int textureHeight, Runnable equip) {
            this(title, "", "", texture, width, height, textureWidth, textureHeight, equip);
        }

        private SampleRow(Component title, String author, String tag, Identifier texture, int width, int height, Runnable equip) {
            this(title, author, tag, texture, width, height, width, height, equip);
        }

        private SampleRow(Component title, String author, String tag, Identifier texture, int width, int height, int textureWidth, int textureHeight, Runnable equip) {
            super(title, Optional::empty, false);
            this.author = author == null ? "" : author;
            this.tag = tag == null ? "" : tag;
            this.texture = texture;
            this.width = width;
            this.height = height;
            this.textureWidth = textureWidth;
            this.textureHeight = textureHeight;
            {
                this.equip = Button.builder(Component.translatable("text.quarkos_capes.gallery.equip"), button -> {
                    Component selected = current instanceof ClothConfigScreen cloth ? cloth.getSelectedCategory() : Component.translatable("text.quarkos_capes.tab.my_cape");
                    equip.run();
                    if (this.tag.isEmpty() || this.tag.equals("local")) reopen(selected);
                })
                        .bounds(0, 0, 60, 20)
                        .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("text.quarkos_capes.tooltip.equip")))
                        .build();
            }
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
            Minecraft minecraft = Minecraft.getInstance();
            if (texture != null) {
                String sample = texture.equals(CapeTextures.bandsTexture()) ? "bands" : texture.equals(CapeTextures.slideTexture()) ? "slide" : "cloak";
                equip.active = !CapeBoard.sampleOn(sample);
                equip.setMessage(Component.translatable(equip.active ? "text.quarkos_capes.gallery.equip" : "text.quarkos_capes.gallery.equipped"));
                equip.setWidth(80);
            }
            if (tag.isEmpty()) {
                graphics.drawString(minecraft.font, getFieldName(), x, y + 8, 0xFFFFFFFF);
                if (texture != null) {
                    McBridge.capeThumb(graphics, texture, x + 80, y + 8, 20, 32, textureWidth, textureHeight, width, height);
                }
                if (equip != null) {
                    equip.setX(Math.max(x, Math.min(x + 148, previewLeft(minecraft.screen) - 88)));
                    equip.setY(y + 12);
                    equip.render(graphics, mouseX, mouseY, delta);
                }
                return;
            }
            String line = getFieldName().getString() + "  " + shownAuthor(author) + "  " + Component.translatable("text.quarkos_capes.gallery." + tag).getString();
            int available = Math.max(20, Math.min(entryWidth, previewLeft(minecraft.screen) - x - 8));
            graphics.drawString(minecraft.font, minecraft.font.plainSubstrByWidth(line, available), x, y + 4, 0xFFFFFFFF);
            if (texture != null) {
                McBridge.capeThumb(graphics, texture, x, y + 16, 20, 32, textureWidth, textureHeight, width, height);
            }
            if (equip != null) {
                equip.setX(x + 72);
                equip.setY(y + 22);
                equip.render(graphics, mouseX, mouseY, delta);
            }
        }

        @Override
        public int getItemHeight() {
            return 52;
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
            return equip == null ? List.of() : List.of(equip);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return equip == null ? List.of() : List.of(equip);
        }
    }

    /** Top search box. Stops before the preview so the query does not sit on the player. */
    private static final class QueryField extends TooltipListEntry<Object> {
        private final EditBox box;

        private QueryField(EditBox box) {
            super(Component.translatable("text.quarkos_capes.field.search"), Optional::empty, false);
            this.box = box;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float delta) {
            Screen screen = Minecraft.getInstance().screen;
            int full = Math.min(Math.max(0, entryWidth - 10), 500);
            int left = x + entryWidth / 2 - full / 2;
            int stop = screen == null ? left + full : previewLeft(screen) - 8;
            if (stop - left < 80) {
                left = x;
                stop = x + Math.min(full, 220);
            }
            box.setX(left);
            box.setY(y + entryHeight / 2 - 9);
            box.setWidth(Math.max(80, stop - left));
            if (box.getValue() == null || box.getValue().isEmpty()) {
                box.setSuggestion(Component.translatable("text.quarkos_capes.search.placeholder").getString());
            } else {
                box.setSuggestion(null);
            }
            box.render(graphics, mouseX, mouseY, delta);
        }

        @Override
        public int getItemHeight() {
            return 24;
        }

        @Override
        public Object getValue() {
            return box.getValue();
        }

        @Override
        public Optional<Object> getDefaultValue() {
            return Optional.of("");
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(box);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(box);
        }
    }

    private static final class ActionRow extends TooltipListEntry<Object> {
        private final Button button;

        private ActionRow(Component label, Runnable action) {
            super(label, Optional::empty, false);
            this.button = Button.builder(label, pressed -> action.run()).bounds(0, 0, 140, 20).build();
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
}
//?}
