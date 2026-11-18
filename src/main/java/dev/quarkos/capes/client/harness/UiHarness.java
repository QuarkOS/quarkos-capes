package dev.quarkos.capes.client.harness;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.net.CapeHttp;
import dev.quarkos.capes.client.net.CatalogClient;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.screen.BrowseScreen;
import dev.quarkos.capes.client.screen.CapeBoard;
import dev.quarkos.capes.client.screen.CapeScreen;
import dev.quarkos.capes.client.screen.CapeScreens;
import dev.quarkos.capes.client.screen.LinkScreen;
import dev.quarkos.capes.client.screen.ui.CapeStyles;
import dev.quarkos.capes.client.screen.ui.CapeTheme;
import dev.quarkos.capes.client.screen.ui.CapeUiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Captures the selection and gallery without contacting a cape service. */
public final class UiHarness {
    private enum Scene {
        SELECT("select"), HOVER("select-hover"), STYLES("styles"), GALLERY("gallery"),
        OFFLINE("gallery-empty"), SETTINGS("settings"), LINK("link");

        final String file;
        Scene(String file) { this.file = file; }
    }

    private record Stage(Scene scene, int width, int height, int scale, boolean capture) {
        String name() { return "ui-" + scene.file + "-s" + scale; }
    }

    private static final Path DIRECTORY = Path.of(System.getProperty("quarkos_capes.captures", "build/captures"));
    private static final List<String> RESULTS = new ArrayList<>();
    private static List<Stage> stages;
    private static int index, age, pass, fail;
    private static int oldScale;
    private static boolean oldOffline, oldBack, finished;
    private static OwnerCape oldCape;
    private static String oldGalleryId;
    private static Screen expectedScreen;
    private static Button hovered;
    private static int sent;
    private static long deadline;
    private static Path capture;
    private static String observation;

    private UiHarness() { }

    public static boolean selected() { return "ui".equals(System.getProperty("quarkos_capes.only")); }
    static boolean finished() { return finished; }

    static void prepare(Minecraft client) {
        oldOffline = ClientSettings.get().localOnly;
        oldGalleryId = ClientSettings.get().equippedGalleryId;
        oldCape = OwnerCape.get();
        oldBack = CapeBook.previewBack;
        oldScale = client.options.guiScale().get();
        ClientSettings.get().localOnly = true;
        CapeBook.previewBack = true;
    }

    static void tick(Minecraft client) {
        if (finished) return;
        if (stages == null) stages = plan();
        if (index == stages.size()) {
            finish(client);
            return;
        }
        Stage stage = stages.get(index);
        try {
            if (age == 0) {
                deadline = System.nanoTime() + 20_000_000_000L;
                Stage previous = index == 0 ? null : stages.get(index - 1);
                if (previous == null || previous.scale() != stage.scale() || previous.width() != stage.width() || previous.height() != stage.height()) {
                    client.options.guiScale().set(stage.scale());
                    client.getWindow().setWindowed(stage.width(), stage.height());
                    client.resizeDisplay();
                }
            }
            if (++age == 7) open(client, stage.scene());
            if (age < 22) return;
            if (capture == null) {
                try {
                    check(client, stage);
                } catch (AssertionError failure) {
                    observation = failure.getMessage();
                }
                if (!stage.capture()) {
                    record(client, stage, observation);
                    return;
                }
                capture = DIRECTORY.resolve(stage.name() + ".png");
                Files.deleteIfExists(capture);
                McBridge.shot(client, capture);
            }
            if (completePng(capture)) {
                record(client, stage, observation);
            } else if (System.nanoTime() > deadline) {
                record(client, stage, "Screenshot was not written");
            }
        } catch (IOException | RuntimeException | AssertionError failure) {
            record(client, stage, failure.toString());
        }
    }

    private static List<Stage> plan() {
        int width = Integer.getInteger("quarkos_capes.width", 1920);
        int height = Integer.getInteger("quarkos_capes.height", 1080);
        List<Stage> plan = new ArrayList<>();
        for (int scale : List.of(2, 4)) {
            for (Scene scene : Scene.values()) plan.add(new Stage(scene, width, height, scale, scene != Scene.LINK));
        }
        if (width != 1280 || height != 720) addLayoutSweep(plan, 1280, 720);
        if (width != 1920 || height != 1080) addLayoutSweep(plan, 1920, 1080);
        return plan;
    }

    private static void addLayoutSweep(List<Stage> plan, int width, int height) {
        for (int scale : List.of(2, 4)) {
            for (Scene scene : List.of(Scene.SELECT, Scene.GALLERY, Scene.OFFLINE, Scene.SETTINGS, Scene.LINK)) {
                plan.add(new Stage(scene, width, height, scale, false));
            }
        }
    }

    private static void open(Minecraft client, Scene scene) {
        client.options.hideGui = false;
        sent = CapeHttp.sent;
        hovered = null;
        switch (scene) {
            case SELECT, HOVER, STYLES, SETTINGS -> {
                CapeBoard.equipSample("bands");
                CapeScreen wardrobe = (CapeScreen) CapeScreens.open(null);
                client.setScreen(wardrobe);
                String sample = scene == Scene.STYLES ? "slide" : "cloak";
                press(wardrobe.sampleCard(sample), "sample " + sample);
                if (!CapeBoard.sampleOn(sample)) throw new AssertionError("Sample was not equipped: " + sample);
                if (scene == Scene.HOVER) hovered = wardrobe.sampleCard("bands");
                if (scene == Scene.STYLES) {
                    press(wardrobe.styleChip(CapeStyles.Chip.ANIMATED), "Animation");
                    press(wardrobe.styleChip(CapeStyles.Chip.WAVY), "Wavy");
                    if (OwnerCape.get().animateFrames || !"wave".equals(OwnerCape.get().style)) {
                        throw new AssertionError("Shape and Animation controls did not update the render state");
                    }
                }
                if (scene == Scene.SETTINGS) press(button(wardrobe, "text.quarkos_capes.ui.settings"), "Settings");
            }
            case GALLERY -> {
                BrowseScreen gallery = new BrowseScreen(null, new BundledCatalog());
                client.setScreen(gallery);
                checkGalleryPaging(gallery);
                gallery.selectFirst();
            }
            case OFFLINE -> client.setScreen(new BrowseScreen(null));
            case LINK -> client.setScreen(new LinkScreen(null));
        }
        expectedScreen = client.screen;
        CapeBook.resetDraws();
        if (scene == Scene.HOVER && hovered == null) throw new AssertionError("Missing hover card");
        if (expectedScreen instanceof CapeUiScreen screen) {
            int x = hovered == null ? 1 : hovered.getX() + hovered.getWidth() / 2;
            int y = hovered == null ? 1 : hovered.getY() + hovered.getHeight() / 2;
            screen.setHarnessPointer(x, y);
        }
    }

    private static void check(Minecraft client, Stage stage) {
        if (client.screen != expectedScreen) throw new AssertionError("Unexpected screen during " + stage.name());
        var window = client.getWindow();
        if (window.getScreenWidth() != stage.width() || window.getScreenHeight() != stage.height()) {
            throw new AssertionError("Window could not reach " + stage.width() + "x" + stage.height());
        }
        if (window.getWidth() / stage.scale() >= 320 && window.getHeight() / stage.scale() >= 240
                && window.getGuiScale() != stage.scale()) throw new AssertionError("Requested GUI scale was not applied");
        HarnessLayout.check(expectedScreen);
        if (CapeHttp.sent != sent) throw new AssertionError("UI capture sent a cape service request");
        if (stage.scene() == Scene.HOVER && !hovered.isHovered()) throw new AssertionError("Pointer did not hover the cape card");
        if (stage.scene() == Scene.GALLERY && expectedScreen instanceof BrowseScreen gallery
                && (gallery.items().size() != BundledCatalog.PAGE_SIZE || !gallery.previewReady())) {
            throw new AssertionError("Bundled gallery did not load");
        }
        if (stage.scene() == Scene.OFFLINE && button(expectedScreen, "text.quarkos_capes.site.go_online") == null) {
            throw new AssertionError("Offline gallery has no Go online action");
        }
        if (stage.scene() == Scene.SETTINGS) {
            //? if fabric || <1.21.2 {
            if (!CapeScreens.isClothSettings(expectedScreen)) throw new AssertionError("Settings did not open the Cloth screen");
            //?} else {
            /*if (!CapeScreens.isSettings(expectedScreen) || expectedScreen instanceof CapeScreen)
                throw new AssertionError("Settings did not open the native screen");
            *///?}
        }
        boolean hasCape = switch (stage.scene()) {
            case SELECT, HOVER, STYLES, GALLERY -> true;
            default -> false;
        };
        if (hasCape && expectedScreen instanceof CapeUiScreen screen && screen.previewBounds() != null
                && CapeBook.previewDraws(client.player.getUUID()) == 0) throw new AssertionError("Player preview did not draw a cape");
    }

    private static Button button(Screen screen, String key) {
        for (var child : screen.children()) {
            if (child instanceof Button candidate && candidate.getMessage().getContents() instanceof TranslatableContents label
                    && label.getKey().equals(key)) return candidate;
        }
        return null;
    }

    private static void press(Button button, String label) {
        if (button == null || !button.active) throw new AssertionError("Missing active control: " + label);
        McBridge.press(button);
    }

    private static void checkGalleryPaging(BrowseScreen gallery) {
        if (gallery.items().size() != BundledCatalog.PAGE_SIZE) throw new AssertionError("Missing gallery fixtures");
        String title = gallery.items().getFirst().title();
        press(button(gallery, "text.quarkos_capes.site.next"), "Next gallery page");
        if (hasCard(gallery, title)) throw new AssertionError("Next page still shows the first cape");
        press(button(gallery, "text.quarkos_capes.site.previous"), "Previous gallery page");
        if (!hasCard(gallery, title)) throw new AssertionError("Previous page did not restore the first cape");
    }

    private static boolean hasCard(BrowseScreen gallery, String title) {
        return gallery.children().stream().anyMatch(child -> child instanceof Button button
                && !(button.getMessage().getContents() instanceof TranslatableContents)
                && button.getMessage().getString().equals(title));
    }

    private static boolean completePng(Path path) throws IOException {
        if (!Files.isRegularFile(path)) return false;
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long size = file.length();
            if (size < 45 || file.readLong() != 0x89504E470D0A1A0AL) return false;
            file.seek(size - 12);
            return file.readInt() == 0 && file.readInt() == 0x49454E44;
        }
    }

    private static void record(Minecraft client, Stage stage, String error) {
        var window = client.getWindow();
        String mode = stage.capture() ? "capture" : "layout";
        RESULTS.add((error == null ? "PASS " : "FAIL ") + stage.name() + " " + mode + " "
                + window.getWidth() + "x" + window.getHeight() + " GUI " + window.getGuiScale()
                + (error == null ? "" : " " + error.replace('\n', ' ')));
        if (error == null) pass++;
        else fail++;
        index++;
        age = 0;
        capture = null;
        observation = null;
    }

    static void abort(Minecraft client, String message) {
        if (finished) return;
        RESULTS.add("FAIL startup " + message.replace('\n', ' '));
        fail++;
        finish(client);
    }

    private static void finish(Minecraft client) {
        finished = true;
        if (oldCape != null) {
            OwnerCape.set(oldCape);
            ClientSettings.get().equippedGalleryId = oldGalleryId;
            ClientSettings.get().localOnly = oldOffline;
            client.options.guiScale().set(oldScale);
            CapeBook.previewBack = oldBack;
        }
        client.setScreen(null);
        String summary = "UI_HARNESS pass=" + pass + " fail=" + fail;
        try {
            Files.createDirectories(DIRECTORY);
            Files.writeString(DIRECTORY.resolve("ui-result.txt"), summary + "\n" + String.join("\n", RESULTS) + "\n");
        } catch (IOException error) {
            System.err.println("Could not write UI harness result: " + error);
        } finally {
            System.out.println(summary);
            client.stop();
        }
    }

    private static final class BundledCatalog implements BrowseScreen.Catalog {
        private static final int PAGE_SIZE = 12;
        private record Entry(CatalogClient.Item item, byte[] png) { }

        private final Map<String, OwnerCape> capes = Map.of("bands", OwnerCape.sampleBands(),
                "slide", OwnerCape.sampleSlide(), "cloak", OwnerCape.sampleCloak());
        private final List<Entry> entries = List.of(
                entry("cloak", CapeTheme.text("sample.cloak").getString(), "QuarkOS", "cloak"),
                entry("bands", CapeTheme.text("sample.bands").getString(), "QuarkOS", "bands"),
                entry("slide", CapeTheme.text("sample.slide").getString(), "QuarkOS", "slide"),
                entry("sun-dial", "Sun Dial", "Meridian", "cloak"),
                entry("tideline", "Tideline", "Sandbar", "bands"),
                entry("afterglow", "Afterglow", "Turbine", "slide"),
                entry("crosswind", "Crosswind", "Kiteworks", "slide"),
                entry("harvest", "Harvest", "Alder", "bands"),
                entry("brass-weave", "Brass Weave", "Loomworks", "cloak"),
                entry("gold-coast", "Gold Coast", "Ridge", "bands"),
                entry("wayfinder", "Wayfinder", "Quarry", "cloak"),
                entry("night-shift", "Night Shift", "Vale", "slide"),
                entry("shale", "Shale", "Mossworks", "cloak"),
                entry("highwater", "Highwater", "Northbank", "bands"));
        private CatalogClient.Item worn;

        private Entry entry(String id, String title, String author, String sample) {
            OwnerCape cape = capes.get(sample);
            var item = new CatalogClient.Item("ours", id, sample.equals("cloak") ? "cloak" : "cape",
                    title, author, cape.style, cape.animateFrames, cape.frameCount, cape.frameWidth, cape.frameHeight,
                    cape.frameDelayMs, "", "", cape.modelJson);
            return new Entry(item, cape.png);
        }

        @Override public boolean offline() { return false; }

        @Override
        public void page(CatalogClient.Query query, Consumer<CatalogClient.Page> ready, Consumer<String> error) {
            String search = query.q().toLowerCase(Locale.ROOT);
            var matches = entries.stream().map(Entry::item)
                    .filter(item -> query.source().isEmpty() || query.source().equals(item.source()))
                    .filter(item -> query.kind().isEmpty() || query.kind().equals(item.kind()))
                    .filter(item -> item.title().toLowerCase(Locale.ROOT).contains(search)
                            || item.author().toLowerCase(Locale.ROOT).contains(search));
            if ("name".equals(query.sort())) matches = matches.sorted(Comparator.comparing(CatalogClient.Item::title, String.CASE_INSENSITIVE_ORDER));
            List<CatalogClient.Item> items = matches.toList();
            int start = query.cursor().isEmpty() ? 0 : Integer.parseInt(query.cursor());
            int end = Math.min(items.size(), start + Math.min(PAGE_SIZE, query.limit()));
            String next = end < items.size() ? Integer.toString(end) : null;
            ready.accept(new CatalogClient.Page(items.subList(start, end), next, Map.of("ours", "available")));
        }

        @Override public void detail(CatalogClient.Item item, Consumer<CatalogClient.Item> ready, Consumer<String> error) { ready.accept(item); }
        @Override
        public void image(CatalogClient.Item item, Consumer<byte[]> ready, Consumer<String> error) {
            entries.stream().filter(entry -> entry.item().id().equals(item.id())).findFirst()
                    .ifPresentOrElse(entry -> ready.accept(entry.png()), () -> error.accept("Missing fixture texture " + item.id()));
        }
        @Override public void wearing(Consumer<CatalogClient.Item> ready, Consumer<String> error) { ready.accept(worn); }
        @Override public void wear(CatalogClient.Item item, Consumer<CatalogClient.Item> ready, Consumer<String> error) { worn = item; ready.accept(item); }
        @Override public void unwear(Consumer<String> ready, Consumer<String> error) { worn = null; ready.accept(BrowseScreen.text("unworn").getString()); }
    }
}
