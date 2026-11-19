package dev.quarkos.capes.client.harness;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.LocalFiles;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.net.CapeHttp;
import dev.quarkos.capes.client.net.CatalogClient;
import dev.quarkos.capes.client.net.ServiceResponse.OfflineReason;
import dev.quarkos.capes.client.net.ServiceUrl;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.render.CapeLayerWatch;
import dev.quarkos.capes.client.render.CapeTextures;
import dev.quarkos.capes.client.screen.BrowseScreen;
import dev.quarkos.capes.client.screen.ui.CapeTheme;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

final class UiFeaturedProbe implements AutoCloseable {
    private static final String WEAR = "text.quarkos_capes.site.wear";
    private static final String UNWEAR = "text.quarkos_capes.site.unwear";
    private static final String RETRY = "text.quarkos_capes.site.retry_action";

    private enum StaticWear { SELECTED, CLEARED, WORN }

    private final ClientSettings settings = ClientSettings.get();
    private final String oldUrl = settings.baseUrl;
    private final boolean oldOffline = settings.localOnly;
    private final OwnerCape oldCape = OwnerCape.get();
    private final String oldGalleryId = settings.equippedGalleryId;
    private final String url = System.getProperty("quarkos_capes.serverUrl", oldUrl).trim();
    private final BrowseScreen browse = new BrowseScreen(null);
    private CatalogClient.Item still, animated;
    private CapeTextures.Image previousPreview;
    private StaticWear staticWear = StaticWear.SELECTED;
    private boolean sawFirstFrame, animatedWear, worldOpened;
    private String metric, skip;

    void open(Minecraft client) {
        if (!ServiceUrl.valid(url)) throw new AssertionError("Invalid featured service URL");
        settings.baseUrl = url;
        settings.localOnly = false;
        McBridge.showCape(client.options);
        client.setScreen(browse);
    }

    BrowseScreen screen() { return browse; }
    String metric() { return metric; }
    String skip() { return skip; }
    void skipRemaining() { skip = "preceding featured check failed"; }

    boolean listReady() {
        var issue = CapeHttp.serviceIssue();
        if (issue != null) {
            if (browse.unavailableUrl().isEmpty()) return false;
            boolean unreachable = issue.reason() == OfflineReason.CONNECTION || issue.reason() == OfflineReason.TIMEOUT;
            if (metric != null || !unreachable) {
                throw new AssertionError("Featured service request failed at " + issue.url() + ": " + issue.reason());
            }
            var retry = UiHarness.button(browse, RETRY);
            if (retry == null || !retry.active || browse.isLoading() || !browse.items().isEmpty()
                    || !CapeTheme.text("site.unreachable").equals(browse.emptyHeading())) {
                throw new AssertionError("Featured Browse did not show the offline state");
            }
            skip = "service unavailable at " + url;
            return true;
        }
        if (browse.isLoading()) return false;
        if (metric == null) {
            var items = browse.items();
            long featured = items.stream().filter(item -> CatalogClient.FEATURED.equals(item.source())).count();
            long animations = items.stream().filter(item -> CatalogClient.FEATURED.equals(item.source()))
                    .filter(UiFeaturedProbe::animated).count();
            metric = "CAPE_API featured=" + featured + " animated=" + animations + " source=" + url;
            if (featured == 0) throw new AssertionError("Featured Browse returned no capes: " + browse.status());
            if (featured != items.size()) throw new AssertionError("Featured Browse included another catalog source");
            still = items.stream().filter(item -> item.frameCount() == 1 && card(item) != null).findFirst()
                    .orElseThrow(() -> new AssertionError("No static featured card is visible"));
            animated = items.stream().filter(item -> animated(item) && card(item) != null).findFirst()
                    .orElseThrow(() -> new AssertionError("No animated featured card is visible"));
        }
        if (browse.items().stream().anyMatch(item -> card(item) != null && !browse.thumbnailReady(item))) return false;
        if (browse.selected() == null) {
            previousPreview = CapeTextures.get("preview");
            select(still);
            return false;
        }
        var texture = CapeTextures.get("preview");
        return browse.previewReady() && texture != null && texture != previousPreview;
    }

    boolean previewReady(Minecraft client) {
        if (browse.isWriting()) return false;
        if (staticWear != StaticWear.WORN) {
            if (browse.isWearing(still)) {
                if (staticWear == StaticWear.CLEARED) throw wearFailure();
                staticWear = StaticWear.CLEARED;
                UiHarness.press(UiHarness.button(browse, UNWEAR), "Unwear previous featured cape");
                return false;
            }
            staticWear = StaticWear.WORN;
            UiHarness.press(UiHarness.button(browse, WEAR), "Wear static featured cape");
            return false;
        }
        if (!browse.isWearing(still)) throw wearFailure();
        if (browse.selected() == still) {
            assertWorn(still);
            select(animated);
            return false;
        }
        if (!browse.previewReady()) return false;
        int frame = CapeBook.previewFrame(client.player.getUUID());
        if (frame == 0) sawFirstFrame = true;
        return sawFirstFrame && frame > 0 && frame < animated.frameCount();
    }

    boolean worldReady(Minecraft client) {
        if (!animatedWear) {
            animatedWear = true;
            UiHarness.press(UiHarness.button(browse, WEAR), "Wear animated featured cape");
            return false;
        }
        if (browse.isWriting()) return false;
        if (!worldOpened) {
            if (!browse.isWearing(animated)) throw wearFailure();
            assertWorn(animated);
            client.setScreen(null);
            McBridge.hideHud(client, true);
            McBridge.thirdPerson(client.options);
            ShotView.frame(0f, 8f, 3.5f);
            CapeBook.resetDraws();
            CapeLayerWatch.reset();
            worldOpened = true;
            return false;
        }
        var texture = CapeTextures.get("local");
        return client.screen == null && client.options.getCameraType() == CameraType.THIRD_PERSON_BACK
                && texture != null && texture.id().equals(CapeLayerWatch.localTexture)
                && CapeBook.draws(client.player.getUUID()) > 0;
    }

    private static boolean animated(CatalogClient.Item item) {
        return item.animateFrames() && item.frameCount() > 1;
    }

    private Button card(CatalogClient.Item item) {
        Component title = Component.literal(item.title());
        for (var child : browse.children()) {
            if (child instanceof Button button && button.visible && title.equals(button.getMessage())) return button;
        }
        return null;
    }

    private void select(CatalogClient.Item item) {
        UiHarness.press(card(item), item.title());
        if (browse.selected() != item) throw new AssertionError("Browse selected a different featured cape");
        CapeBook.resetDraws();
    }

    private AssertionError wearFailure() {
        return new AssertionError("Featured wear failed: " + browse.status());
    }

    private void assertWorn(CatalogClient.Item item) {
        OwnerCape worn = OwnerCape.get();
        if (!item.id().equals(settings.equippedGalleryId) || worn.png.length == 0
                || worn.animateFrames != item.animateFrames() || worn.frameCount != item.frameCount()
                || worn.frameWidth != item.frameWidth() || worn.frameHeight != item.frameHeight()
                || !Arrays.equals(worn.frameDelayMs, item.delays())) {
            throw new AssertionError("Featured cape was not applied to the local outfit");
        }
    }

    @Override public void close() {
        settings.baseUrl = oldUrl;
        settings.localOnly = oldOffline;
        boolean worn = OwnerCape.get() != oldCape;
        OwnerCape.set(oldCape);
        settings.equippedGalleryId = oldGalleryId;
        if (worn) LocalFiles.saveAsync(ignored -> { });
        ShotView.clear();
    }
}
