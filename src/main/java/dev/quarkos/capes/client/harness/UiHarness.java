package dev.quarkos.capes.client.harness;

import com.mojang.blaze3d.platform.InputConstants;
import dev.quarkos.capes.QuarkosCapes;
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
import dev.quarkos.capes.sim.WaveCloth;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
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

/** Captures the cape screens and the equipped cape in a local world. */
public final class UiHarness {
    private enum Scene {
        SELECT("select"), HOVER("select-hover"), STYLES("styles"), GALLERY("gallery"),
        OFFLINE("gallery-empty"), SETTINGS("settings"), LINK("link"),
        WORLD_BACK("world-back"), WORLD_SIDE("world-side"), PAUSE("ingame-pause"),
        INGAME("ingame-wardrobe"), KEYBIND("ingame-keybind"),
        FEATURED_LIST("featured-list"), FEATURED_PREVIEW("featured-preview"), FEATURED_ANIMATED("featured-animated"), FEATURED_WORLD("featured-world-back"),
        BAD_SERVICE("browse-404"), ABOUT("about"), LABY_LIST("labymod-browse"), LABY_TRYON("labymod-tryon"), MODS("mods-list");

        final String file;
        Scene(String file) { this.file = file; }

        boolean world() { return this == WORLD_BACK || this == WORLD_SIDE || this == FEATURED_WORLD; }
        boolean featured() { return this == FEATURED_LIST || this == FEATURED_PREVIEW || this == FEATURED_ANIMATED || this == FEATURED_WORLD; }
        boolean fixedScale() { return world() || ordinal() >= PAUSE.ordinal(); }
    }

    private record Stage(Scene scene, int width, int height, int scale, boolean capture) {
        String name() { return "ui-" + scene.file + (scene.fixedScale() ? "" : "-s" + scale); }
    }

    private static final Path DIRECTORY = Path.of(System.getProperty("quarkos_capes.captures", "build/captures"));
    private static final long STAGE_TIMEOUT = 20_000_000_000L;
    // Vanilla fades to 80% of the previous alpha each tick, then interpolates both samples.
    private static final int SAVE_INDICATOR_FADE_TICKS = 28;
    private static final String OPEN_LABEL = "text.quarkos_capes.title";
    private static final List<String> RESULTS = new ArrayList<>();
    private static List<Stage> stages;
    private static int index, age, pass, fail;
    private static int oldScale;
    private static boolean oldOffline, oldBack, oldHideGui, finished;
    private static CameraType oldCamera;
    private static OwnerCape oldCape;
    private static WaveCloth.Settings oldCloth;
    private static String oldGalleryId;
    private static Screen expectedScreen;
    private static Screen pauseMenu;
    private static UiServiceProbe serviceProbe;
    private static UiFeaturedProbe featuredProbe;
    private static CapeMotionCapture motion;
    private static Button hovered;
    private static int sent, saveFadeTicks;
    private static long deadline;
    private static Path capture;
    private static String observation;
    private static String stageDetail;
    private static boolean opened;

    private UiHarness() { }

    public static boolean selected() { return "ui".equals(System.getProperty("quarkos_capes.only")); }
    static boolean finished() { return finished; }

    static void prepare(Minecraft client) {
        oldOffline = ClientSettings.get().localOnly;
        oldGalleryId = ClientSettings.get().equippedGalleryId;
        oldCape = OwnerCape.get();
        oldCloth = ClientSettings.get().cloth;
        oldBack = CapeBook.previewBack;
        oldScale = client.options.guiScale().get();
        oldCamera = client.options.getCameraType();
        oldHideGui = McBridge.hudHidden(client);
        ClientSettings.get().localOnly = true;
        ClientSettings.get().cloth = WaveCloth.Settings.defaults();
        CapeBook.previewBack = true;
    }

    static void tick(Minecraft client) {
        if (finished) return;
        if (stages == null) stages = plan();
        if (index == stages.size()) {
            //? if >=1.21.11 {
            if (motion == null) motion = new CapeMotionCapture(DIRECTORY);
            if (!motion.finished()) {
                var result = motion.tick(client);
                if (result != null) {
                    RESULTS.addAll(result.metrics());
                    recordCheck(result.name(), result.detail(), result.failure());
                }
                return;
            }
            //?}
            finish(client);
            return;
        }
        Stage stage = stages.get(index);
        try {
            if (age == 0) {
                deadline = System.nanoTime() + STAGE_TIMEOUT;
                Stage previous = index == 0 ? null : stages.get(index - 1);
                if (previous == null || previous.scale() != stage.scale() || previous.width() != stage.width() || previous.height() != stage.height()) {
                    client.options.guiScale().set(stage.scale());
                    client.getWindow().setWindowed(stage.width(), stage.height());
                    client.resizeDisplay();
                }
            }
            if (++age >= 7 && !opened) {
                if (stage.scene() == Scene.KEYBIND && client.player != null && client.screen == null
                        && CapeBook.draws(client.player.getUUID()) == 0 && System.nanoTime() < deadline) return;
                if (stage.scene().featured()) Files.deleteIfExists(DIRECTORY.resolve(stage.name() + ".png"));
                open(client, stage.scene());
                opened = true;
                age = 7;
                saveFadeTicks = SAVE_INDICATOR_FADE_TICKS;
            }
            if (age > 7 && stage.scene() == Scene.BAD_SERVICE) {
                if (!serviceProbe.tick(client)) {
                    if (System.nanoTime() > deadline) record(client, stage, "Timed out testing the Advanced URL and the 404 response");
                    return;
                }
                expectedScreen = serviceProbe.screen();
            }
            if (age > 7 && stage.scene() == Scene.KEYBIND && expectedScreen == null && client.screen instanceof CapeScreen) {
                expectedScreen = client.screen;
            }
            boolean waitingForSave = stage.capture() && capture == null && saveIndicatorSettling(client);
            if (age < 22) return;
            if (capture == null) {
                if (waitingForSave) {
                    if (System.nanoTime() > deadline) record(client, stage, "World save indicator did not clear");
                    return;
                }
                if (stage.scene().featured() && !featuredReady(client, stage)) return;
                if (stage.scene() == Scene.LABY_LIST && !labyReady(client, stage)) return;
                if (stage.scene() == Scene.LABY_TRYON && client.player != null
                        && (CapeBook.draws(client.player.getUUID()) == 0 || dev.quarkos.capes.client.render.CapeTextures.get(dev.quarkos.capes.client.render.CapeDraw.TRY_ON_TEXTURE) == null)
                        && System.nanoTime() < deadline) return;
                if (stage.scene().world() && client.player != null && CapeBook.draws(client.player.getUUID()) == 0
                        && System.nanoTime() < deadline) return;
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
                deadline = System.nanoTime() + STAGE_TIMEOUT;
            }
            if (completePng(capture)) {
                record(client, stage, observation);
            } else if (System.nanoTime() > deadline) {
                record(client, stage, observation == null ? "Screenshot was not written" : observation + ". Screenshot was not written");
            }
        } catch (IOException | RuntimeException | AssertionError failure) {
            record(client, stage, failure.toString());
        }
    }

    private static boolean saveIndicatorSettling(Minecraft client) {
        if (!client.options.showAutosaveIndicator().get()) return false;
        var server = client.getSingleplayerServer();
        if (server != null && server.isCurrentlySaving()) saveFadeTicks = SAVE_INDICATOR_FADE_TICKS;
        else if (saveFadeTicks > 0) saveFadeTicks--;
        return saveFadeTicks > 0;
    }

    private static List<Stage> plan() {
        int width = Integer.getInteger("quarkos_capes.width", 1920);
        int height = Integer.getInteger("quarkos_capes.height", 1080);
        List<Stage> plan = new ArrayList<>();
        for (int scale : List.of(2, 4)) {
            for (Scene scene : List.of(Scene.SELECT, Scene.HOVER, Scene.STYLES, Scene.GALLERY, Scene.OFFLINE, Scene.SETTINGS, Scene.LINK, Scene.ABOUT)) {
                plan.add(new Stage(scene, width, height, scale, scene != Scene.LINK));
            }
        }
        if (width != 1280 || height != 720) addLayoutSweep(plan, 1280, 720);
        if (width != 1920 || height != 1080) addLayoutSweep(plan, 1920, 1080);
        for (Scene scene : List.of(Scene.WORLD_BACK, Scene.WORLD_SIDE, Scene.PAUSE, Scene.INGAME, Scene.KEYBIND,
                Scene.FEATURED_LIST, Scene.FEATURED_PREVIEW, Scene.FEATURED_ANIMATED, Scene.FEATURED_WORLD, Scene.BAD_SERVICE,
                Scene.LABY_LIST, Scene.LABY_TRYON, Scene.MODS)) {
            plan.add(new Stage(scene, width, height, 2, scene != Scene.KEYBIND));
        }
        return plan;
    }

    private static void addLayoutSweep(List<Stage> plan, int width, int height) {
        for (int scale : List.of(2, 4)) {
            for (Scene scene : List.of(Scene.SELECT, Scene.GALLERY, Scene.OFFLINE, Scene.SETTINGS, Scene.LINK)) {
                plan.add(new Stage(scene, width, height, scale, false));
            }
        }
    }

    private static void open(Minecraft client, Scene scene) throws IOException {
        McBridge.hideHud(client, scene.world() && scene != Scene.FEATURED_WORLD);
        sent = CapeHttp.sent;
        hovered = null;
        switch (scene) {
            case SELECT, HOVER, STYLES, SETTINGS -> {
                CapeBoard.equipSample("bands");
                var title = new TitleScreen();
                client.setScreen(title);
                CapeScreen wardrobe = openWardrobe(client, title);
                String sample = scene == Scene.STYLES ? "slide" : "cloak";
                press(wardrobe.sampleCard(sample), "sample " + sample);
                if (!CapeBoard.sampleOn(sample)) throw new AssertionError("Sample was not equipped: " + sample);
                if (scene == Scene.HOVER) hovered = wardrobe.sampleCard("bands");
                if (scene == Scene.STYLES) {
                    Button animation = wardrobe.styleChip(CapeStyles.Chip.ANIMATED);
                    if (animation == null || animation != button(wardrobe, "text.quarkos_capes.ui.animation_on")) {
                        throw new AssertionError("Animation did not show On");
                    }
                    press(animation, "Animation");
                    if (animation != button(wardrobe, "text.quarkos_capes.ui.animation_off")) {
                        throw new AssertionError("Animation did not show Off");
                    }
                    press(wardrobe.styleChip(CapeStyles.Chip.WAVY), "Wavy");
                    if (OwnerCape.get().animateFrames || !"wave".equals(OwnerCape.get().style)) {
                        throw new AssertionError("Shape and Animation controls did not update the render state");
                    }
                }
                if (scene == Scene.SETTINGS) press(button(wardrobe, "text.quarkos_capes.ui.settings"), "Settings");
            }
            case GALLERY -> {
                var catalog = new BundledCatalog();
                BrowseScreen gallery = new BrowseScreen(null, catalog);
                client.setScreen(gallery);
                checkGalleryPaging(gallery, catalog);
                gallery.selectFirst();
            }
            case OFFLINE -> client.setScreen(new BrowseScreen(null));
            case LINK -> client.setScreen(new LinkScreen(null));
            case ABOUT -> client.setScreen(new dev.quarkos.capes.client.screen.LegalScreen(null));
            case LABY_LIST -> {
                // Live laby.net read: the release gallery's default source, online.
                ClientSettings.get().localOnly = false;
                labyBrowse = new BrowseScreen(null);
                labySelected = false;
                client.setScreen(labyBrowse);
            }
            case LABY_TRYON -> {
                if (client.screen != labyBrowse || client.player == null) throw new AssertionError("LabyMod gallery is not open over the world");
                client.options.setCameraType(CameraType.FIRST_PERSON);
                press(button(labyBrowse, "text.quarkos_capes.site.try_on"), "Try on LabyMod cloak");
                if (client.screen != null) throw new AssertionError("LabyMod Try on did not open the world view: " + labyBrowse.status());
                McBridge.hideHud(client, true);
                ShotView.frame(0f, 8f, 3.5f);
                CapeBook.resetDraws();
            }
            case MODS -> {
                ShotView.clear();
                McBridge.hideHud(client, false);
                client.setScreen(modsScreen());
            }
            case WORLD_BACK, WORLD_SIDE -> {
                client.setScreen(null);
                CapeBoard.equipSample("cloak");
                CapeBook.preview = false;
                CapeBook.draft = null;
                McBridge.showCape(client.options);
                McBridge.thirdPerson(client.options);
                ShotView.frame(scene == Scene.WORLD_BACK ? 0f : 50f, 8f, 3.5f);
            }
            case PAUSE -> {
                client.setScreen(null);
                client.pauseGame(false);
                if (!(client.screen instanceof PauseScreen)) throw new AssertionError("Pause menu did not open in the world");
                pauseMenu = client.screen;
            }
            case INGAME -> {
                if (client.screen != pauseMenu || client.player == null) throw new AssertionError("The in-game pause screen is missing");
                CapeBoard.equipSample("bands");
                CapeScreen wardrobe = openWardrobe(client, pauseMenu);
                press(wardrobe.sampleCard("cloak"), "Cloak in-game");
                press(wardrobe.styleChip(CapeStyles.Chip.WAVY), "Wavy in-game");
                if (!CapeBoard.sampleOn("cloak") || !CapeBoard.styleOn("wave")) throw new AssertionError("World wardrobe did not apply the cape");
            }
            case KEYBIND -> {
                if (client.screen != null || client.player == null || !CapeBoard.sampleOn("cloak")
                        || !CapeBoard.styleOn("wave") || CapeBook.draws(client.player.getUUID()) == 0) {
                    throw new AssertionError("Cape edits did not render after returning to the world");
                }
                if (QuarkosCapes.OPEN == null) throw new AssertionError("Wardrobe key mapping was not registered");
                //? if >=26.3 {
                /*client.keyboardHandler.keyPress(client.getWindow().handle(), InputConstants.PRESS,
                        new net.minecraft.client.input.KeyEvent(InputConstants.KEY_O, 'o', 0));
                if (!(client.screen instanceof CapeScreen)) throw new AssertionError("O was consumed before opening the wardrobe");
                *///?} else {
                KeyMapping.click(QuarkosCapes.OPEN.getDefaultKey());
                //?}
            }
            case FEATURED_LIST -> {
                featuredProbe = new UiFeaturedProbe();
                featuredProbe.open(client);
            }
            case FEATURED_PREVIEW, FEATURED_ANIMATED, FEATURED_WORLD -> { }
            case BAD_SERVICE -> {
                serviceProbe = new UiServiceProbe();
                serviceProbe.openAdvanced(client);
            }
        }
        expectedScreen = client.screen;
        if (scene == Scene.MODS && expectedScreen != null) filterMods(expectedScreen);
        CapeBook.resetDraws();
        if (scene == Scene.HOVER && hovered == null) throw new AssertionError("Missing hover card");
        if (expectedScreen instanceof CapeUiScreen screen) {
            int x = hovered == null ? 1 : hovered.getX() + hovered.getWidth() / 2;
            int y = hovered == null ? 1 : hovered.getY() + hovered.getHeight() / 2;
            screen.setHarnessPointer(x, y);
        }
    }

    private static BrowseScreen labyBrowse;
    private static boolean labySelected;

    private static boolean labyReady(Minecraft client, Stage stage) {
        boolean ready = false;
        if (!labyBrowse.items().isEmpty()) {
            if (!labySelected) { labyBrowse.selectFirst(); labySelected = true; }
            ready = labyBrowse.previewReady();
        }
        if (!ready && System.nanoTime() > deadline) {
            record(client, stage, "Timed out waiting for the LabyMod gallery. " + labyBrowse.status());
            return false;
        }
        if (ready) expectedScreen = labyBrowse;
        return ready;
    }

    private static boolean featuredReady(Minecraft client, Stage stage) {
        if (featuredProbe != null && featuredProbe.skip() == null) {
            boolean ready = switch (stage.scene()) {
                case FEATURED_LIST -> featuredProbe.listReady();
                case FEATURED_PREVIEW -> featuredProbe.previewReady(client);
                case FEATURED_ANIMATED -> featuredProbe.animatedReady(client, DIRECTORY);
                case FEATURED_WORLD -> featuredProbe.worldReady(client);
                default -> throw new AssertionError("Expected a featured capture stage");
            };
            if (!ready) {
                if (System.nanoTime() > deadline) record(client, stage,
                        "Timed out waiting for " + stage.name() + ". " + featuredProbe.screen().status());
                return false;
            }
        }
        if (featuredProbe == null || featuredProbe.skip() != null) {
            record(client, stage, null);
            return false;
        }
        expectedScreen = stage.scene().world() ? null : featuredProbe.screen();
        if (stage.scene() == Scene.FEATURED_PREVIEW) {
            stageDetail = "frame=" + CapeBook.previewFrame(client.player.getUUID());
        }
        if (stage.scene() == Scene.FEATURED_ANIMATED) stageDetail = featuredProbe.animatedDetail();
        return true;
    }

    private static void check(Minecraft client, Stage stage) {
        if (client.screen != expectedScreen) throw new AssertionError("Unexpected screen during " + stage.name());
        var window = client.getWindow();
        if (window.getScreenWidth() != stage.width() || window.getScreenHeight() != stage.height()) {
            throw new AssertionError("Window could not reach " + stage.width() + "x" + stage.height());
        }
        if (window.getWidth() / stage.scale() >= 320 && window.getHeight() / stage.scale() >= 240
                && window.getGuiScale() != stage.scale()) throw new AssertionError("Requested GUI scale was not applied");
        if (!stage.scene().world() && stage.scene() != Scene.LABY_TRYON) {
            if (expectedScreen == null) throw new AssertionError("Wardrobe did not open");
            if (stage.scene() != Scene.MODS) HarnessLayout.check(expectedScreen);
        }
        if (stage.scene() == Scene.BAD_SERVICE) {
            stageDetail = serviceProbe.check();
            return;
        }
        if (stage.scene().featured()) return;
        if (stage.scene() == Scene.LABY_LIST) {
            if (labyBrowse.items().isEmpty() || !labyBrowse.previewReady()) throw new AssertionError("LabyMod gallery did not load");
            boolean cloaksOnly = labyBrowse.items().stream().allMatch(item -> "labymod".equals(item.source()) && "cloak".equals(item.kind()));
            if (!cloaksOnly) throw new AssertionError("LabyMod gallery listed something other than LabyMod cloaks");
            stageDetail = "labymod items=" + labyBrowse.items().size() + " first=" + labyBrowse.items().get(0).title();
            return;
        }
        if (stage.scene() == Scene.LABY_TRYON) {
            if (client.screen != null || client.player == null || CapeBook.draws(client.player.getUUID()) == 0
                    || dev.quarkos.capes.client.render.CapeTextures.get(dev.quarkos.capes.client.render.CapeDraw.TRY_ON_TEXTURE) == null) {
                throw new AssertionError("LabyMod try-on cloak did not draw on the player");
            }
            stageDetail = "try-on " + labyBrowse.items().get(0).title();
            return;
        }
        if (stage.scene() == Scene.MODS) {
            if (expectedScreen == null || expectedScreen.getClass().getName().equals(TitleScreen.class.getName())) throw new AssertionError("No mods list screen");
            stageDetail = "mods " + expectedScreen.getClass().getSimpleName() + (modsFiltered ? " filtered" : "") + (modsSelected ? " selected" : "");
            return;
        }
        if (CapeHttp.sent != sent) throw new AssertionError("UI capture sent a cape service request");
        if (stage.scene().world()) {
            if (client.screen != null || client.options.getCameraType() != CameraType.THIRD_PERSON_BACK
                    || !CapeBoard.sampleOn("cloak")) throw new AssertionError("Cloak was not equipped in third-person view");
            if (client.player == null || CapeBook.draws(client.player.getUUID()) == 0) {
                throw new AssertionError("Cloak did not draw in the world");
            }
            return;
        }
        if (stage.scene() == Scene.HOVER && !hovered.isHovered()) throw new AssertionError("Pointer did not hover the cape card");
        if (stage.scene() == Scene.GALLERY && expectedScreen instanceof BrowseScreen gallery
                && (gallery.items().size() != BundledCatalog.PAGE_SIZE || !gallery.previewReady())) {
            throw new AssertionError("Bundled gallery did not load");
        }
        if (stage.scene() == Scene.OFFLINE && button(expectedScreen, "text.quarkos_capes.site.go_online") == null) {
            throw new AssertionError("Offline gallery has no Go online action");
        }
        if (stage.scene() == Scene.PAUSE && button(expectedScreen, OPEN_LABEL) == null) {
            throw new AssertionError("Pause menu did not show the wardrobe button");
        }
        if (stage.scene() == Scene.KEYBIND && (!(expectedScreen instanceof CapeScreen) || !CapeBoard.styleOn("wave"))) {
            throw new AssertionError("Keybind did not reopen the edited wardrobe");
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
            case SELECT, HOVER, STYLES, GALLERY, INGAME, KEYBIND -> true;
            default -> false;
        };
        if (hasCape && expectedScreen instanceof CapeUiScreen screen && screen.previewBounds() != null
                && CapeBook.previewDraws(client.player.getUUID()) == 0) throw new AssertionError("Player preview did not draw a cape");
    }

    private static CapeScreen openWardrobe(Minecraft client, Screen parent) {
        press(button(parent, OPEN_LABEL), "Wardrobe");
        if (!(client.screen instanceof CapeScreen wardrobe)) throw new AssertionError("Wardrobe did not open");
        return wardrobe;
    }

    private static boolean modsFiltered, modsSelected;

    /** Loader mods list (Forge/NeoForge built in, Fabric via Mod Menu when on the dev runtime), filtered to this mod. */
    private static Screen modsScreen() {
        String[] names = {"net.minecraftforge.client.gui.ModListScreen", "net.neoforged.neoforge.client.gui.ModListScreen",
                "com.terraformersmc.modmenu.gui.ModsScreen"};
        Screen screen = null;
        for (String name : names) {
            try {
                screen = (Screen) Class.forName(name).getConstructor(Screen.class).newInstance(new TitleScreen());
                break;
            } catch (ReflectiveOperationException | LinkageError ignored) { }
        }
        if (screen == null) throw new AssertionError("No mods list screen on this loader (Mod Menu absent?)");
        return screen;
    }

    /** After init: type the mod name into the search box and select the first match. Best effort. */
    private static void filterMods(Screen screen) {
        for (var child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.EditBox box) {
                box.setValue("Quark");
                modsFiltered = true;
                break;
            }
        }
        try {
            for (Class<?> c = screen.getClass(); c != null && c != Screen.class; c = c.getSuperclass()) {
                for (var field : c.getDeclaredFields()) {
                    if (!net.minecraft.client.gui.components.AbstractSelectionList.class.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true);
                    Object list = field.get(screen);
                    if (!(list instanceof net.minecraft.client.gui.components.events.ContainerEventHandler handler)) continue;
                    for (Object entry : handler.children()) {
                        if (!entry.toString().isEmpty()) {
                            for (var m : screen.getClass().getDeclaredMethods()) {
                                if (m.getName().equals("setSelected") && m.getParameterCount() == 1 && m.getParameterTypes()[0].isInstance(entry)) {
                                    m.setAccessible(true); m.invoke(screen, entry); modsSelected = true; return;
                                }
                            }
                            for (Class<?> lc = list.getClass(); lc != null; lc = lc.getSuperclass()) {
                                for (var m : lc.getDeclaredMethods()) {
                                    if (m.getName().equals("setSelected") && m.getParameterCount() == 1) {
                                        m.setAccessible(true); m.invoke(list, entry); modsSelected = true; return;
                                    }
                                }
                            }
                            return;
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    static Button button(Screen screen, String key) {
        for (var child : screen.children()) {
            if (child instanceof Button candidate && candidate.getMessage().getContents() instanceof TranslatableContents label
                    && label.getKey().equals(key)) return candidate;
        }
        return null;
    }

    static void press(Button button, String label) {
        if (button == null || !button.active) throw new AssertionError("Missing active control: " + label);
        McBridge.press(button);
    }

    private static void checkGalleryPaging(BrowseScreen gallery, BundledCatalog catalog) {
        var titles = catalog.entries.stream().map(entry -> entry.item().title()).toList();
        if (titles.size() != 14 || catalog.entries.stream().map(entry -> entry.item().id()).distinct().count() != titles.size()) {
            throw new AssertionError("Expected 14 distinct gallery fixtures");
        }
        if (gallery.items().size() != BundledCatalog.PAGE_SIZE) throw new AssertionError("Missing gallery fixtures");
        List<String> firstPage = visibleCapes(gallery);
        if (gallery.width == 480 && gallery.height == 270 && firstPage.size() != 6) {
            throw new AssertionError("Browse did not fit two rows of three capes at GUI 4");
        }

        int turns = 0;
        int start = 0;
        while (start < titles.size()) {
            List<String> visible = visibleCapes(gallery);
            int end = start + visible.size();
            if (visible.isEmpty() || end > titles.size() || !visible.equals(titles.subList(start, end))) {
                throw new AssertionError("Gallery cards are missing or repeated");
            }
            Component expected = Component.translatable("text.quarkos_capes.ui.range", start + 1, end, titles.size());
            if (!expected.equals(gallery.pageLabel())) throw new AssertionError("Incorrect gallery range: " + gallery.pageLabel());
            Button next = button(gallery, "text.quarkos_capes.site.next");
            if (next == null || next.active != (end < titles.size())) {
                throw new AssertionError("Next has the wrong state at cape " + end);
            }
            start = end;
            if (next.active) {
                press(next, "Next gallery page");
                turns++;
            }
        }
        for (int i = 0; i < turns; i++) press(button(gallery, "text.quarkos_capes.site.previous"), "Previous gallery page");
        Button previous = button(gallery, "text.quarkos_capes.site.previous");
        if (!visibleCapes(gallery).equals(firstPage) || previous == null || previous.active) {
            throw new AssertionError("Previous did not restore the first gallery page");
        }
    }

    private static List<String> visibleCapes(BrowseScreen gallery) {
        return gallery.children().stream().filter(child -> child instanceof Button button && button.visible
                && !(button.getMessage().getContents() instanceof TranslatableContents))
                .map(child -> ((Button) child).getMessage().getString()).toList();
    }

    static boolean completePng(Path path) throws IOException {
        if (!Files.isRegularFile(path)) return false;
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long size = file.length();
            if (size < 45 || file.readLong() != 0x89504E470D0A1A0AL) return false;
            file.seek(size - 12);
            return file.readInt() == 0 && file.readInt() == 0x49454E44;
        }
    }

    private static void record(Minecraft client, Stage stage, String error) {
        String skip = null;
        if (stage.scene() == Scene.BAD_SERVICE) {
            closeServiceProbe();
        } else if (stage.scene().featured()) {
            skip = featuredProbe == null ? "featured check could not start" : featuredProbe.skip();
            if (featuredProbe != null) {
                if (stage.scene() == Scene.FEATURED_LIST && featuredProbe.metric() != null) {
                    RESULTS.add(featuredProbe.metric());
                    System.out.println(featuredProbe.metric());
                }
                if (error != null) featuredProbe.skipRemaining();
                if (stage.scene() == Scene.FEATURED_WORLD) closeFeaturedProbe();
            }
        } else if (error == null) {
            try {
                checkReturn(client, stage.scene());
            } catch (RuntimeException | AssertionError failure) {
                error = failure.toString();
            }
        }
        var window = client.getWindow();
        String mode = stage.capture() ? "capture" : "layout";
        String detail = mode + " " + window.getWidth() + "x" + window.getHeight() + " GUI " + window.getGuiScale();
        if (stageDetail != null) detail += " " + stageDetail;
        if (error == null && skip != null) detail = "SKIP " + skip;
        recordCheck(stage.name(), detail, error);
        index++;
        age = 0;
        opened = false;
        capture = null;
        observation = null;
        stageDetail = null;
    }

    private static void checkReturn(Minecraft client, Scene scene) {
        if (scene == Scene.INGAME) {
            press(button(client.screen, "text.quarkos_capes.button.done"), "Done");
            if (client.screen != pauseMenu) throw new AssertionError("Done did not return to the pause menu");
            CapeBook.resetDraws();
            client.setScreen(null);
        } else if (scene == Scene.KEYBIND) {
            //? if >=1.21.9 {
            client.screen.keyPressed(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
            //?} else {
            /*client.screen.keyPressed(InputConstants.KEY_ESCAPE, 0, 0);
            *///?}
            if (client.screen != null) throw new AssertionError("Esc did not return to the game");
        }
    }

    private static void recordCheck(String name, String detail, String error) {
        RESULTS.add((error == null ? "PASS " : "FAIL ") + name + (detail.isBlank() ? "" : " " + detail)
                + (error == null ? "" : " " + error.replace('\n', ' ')));
        if (error == null) pass++;
        else fail++;
    }

    private static void closeServiceProbe() {
        if (serviceProbe != null) {
            serviceProbe.close();
            serviceProbe = null;
        }
    }

    private static void closeFeaturedProbe() {
        if (featuredProbe != null) {
            featuredProbe.close();
            featuredProbe = null;
        }
    }

    static void abort(Minecraft client, String message) {
        if (finished) return;
        RESULTS.add("FAIL startup " + message.replace('\n', ' '));
        fail++;
        finish(client);
    }

    private static void finish(Minecraft client) {
        finished = true;
        closeServiceProbe();
        closeFeaturedProbe();
        if (motion != null) motion.stop(client);
        if (oldCape != null) {
            OwnerCape.set(oldCape);
            ClientSettings.get().equippedGalleryId = oldGalleryId;
            ClientSettings.get().localOnly = oldOffline;
            ClientSettings.get().cloth = oldCloth;
            client.options.guiScale().set(oldScale);
            client.options.setCameraType(oldCamera);
            McBridge.hideHud(client, oldHideGui);
            CapeBook.previewBack = oldBack;
        }
        ShotView.clear();
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
                    cape.frameDelayMs, "", "", cape.modelJson, false);
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
            ready.accept(new CatalogClient.Page(items.subList(start, end), next, Map.of("ours", "available"), items.size()));
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
