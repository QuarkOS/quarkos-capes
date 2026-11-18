package dev.quarkos.capes.client.harness;

import com.sun.net.httpserver.HttpServer;
import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.net.CapeHttp;
import dev.quarkos.capes.client.net.ServiceResponse;
import dev.quarkos.capes.client.screen.BrowseScreen;
import dev.quarkos.capes.client.screen.CapeBoard;
import dev.quarkos.capes.client.screen.CapeScreen;
import dev.quarkos.capes.client.screen.CapeScreens;
import dev.quarkos.capes.client.screen.ui.CapeStyles;
import dev.quarkos.capes.client.screen.ui.CapeTheme;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises Browse against a loopback listener returning a 404 on every route. */
final class UiServiceProbe implements AutoCloseable {
    private static final String HEALTH = "GET /v1/health";
    private static final byte[] HTML = "<html><body>Not found</body></html>".getBytes(StandardCharsets.UTF_8);
    private static final String BROWSE = "text.quarkos_capes.site.browse";
    private static final String RETRY = "text.quarkos_capes.site.retry_action";

    private final ClientSettings settings = ClientSettings.get();
    private final String oldUrl = settings.baseUrl;
    private final boolean oldOffline = settings.localOnly;
    private final HttpServer server;
    private final String url;
    private final ConcurrentLinkedQueue<String> requests = new ConcurrentLinkedQueue<>();
    private final AtomicInteger warnings = new AtomicInteger();
    private final AtomicInteger exceptions = new AtomicInteger();
    private final Logger logger = (Logger) LogManager.getRootLogger();
    private final AbstractAppender appender;
    private final CapeScreen wardrobe = (CapeScreen) CapeScreens.open(null);
    private BrowseScreen browse;
    private boolean retried, checked;

    UiServiceProbe() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        url = "http://127.0.0.1:" + server.getAddress().getPort();
        appender = new AbstractAppender("capes-ui-404", null, null, true, Property.EMPTY_ARRAY) {
            @Override public void append(LogEvent event) {
                boolean capes = event.getLoggerName().equals("quarkos_capes") || event.getThreadName().startsWith("quarkos-capes");
                if (capes && (event.getThrown() != null || event.getThrownProxy() != null)) exceptions.incrementAndGet();
                if (capes && event.getLevel() == Level.WARN && event.getMessage().getFormattedMessage().contains(url)) {
                    warnings.incrementAndGet();
                }
            }
        };
        server.createContext("/", exchange -> {
            try (exchange) {
                requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(404, HTML.length);
                exchange.getResponseBody().write(HTML);
            }
        });
        server.start();
        appender.start();
        logger.addAppender(appender);
    }

    void openAdvanced(Minecraft client) {
        //? if fabric && >=1.21.11 {
        var advanced = CapeScreens.openTab(wardrobe, 5);
        client.setScreen(advanced);
        if (!(advanced instanceof me.shedaniel.clothconfig2.gui.ClothConfigScreen cloth)
                || !CapeTheme.text("tab.advanced").equals(cloth.getSelectedCategory())) {
            throw new AssertionError("Advanced Cloth settings did not open in the world");
        }
        var urlField = cloth.getCategorizedEntries().get(cloth.getSelectedCategory()).stream()
                .filter(me.shedaniel.clothconfig2.gui.entries.StringListEntry.class::isInstance)
                .map(me.shedaniel.clothconfig2.gui.entries.StringListEntry.class::cast)
                .filter(entry -> CapeTheme.text("field.base_url").equals(entry.getFieldName()))
                .findFirst().orElseThrow(() -> new AssertionError("Missing Advanced service URL field"));
        urlField.setValue(url);
        //?} else {
        /*CapeBoard.baseUrl(url);
        *///?}
    }

    boolean tick(Minecraft client) {
        if (checked) return true;
        if (browse == null) {
            if (!url.equals(settings.baseUrl)) return false;
            settings.localOnly = false;
            openBrowse(client);
            return false;
        }
        if (!url.equals(browse.unavailableUrl())) return false;
        assertOffline();
        if (!retried) {
            if (!List.copyOf(requests).equals(List.of(HEALTH))) throw new AssertionError("First 404 requests " + requests);
            retried = true;
            UiHarness.press(UiHarness.button(browse, RETRY), "Retry");
            return false;
        }
        if (!List.copyOf(requests).equals(List.of(HEALTH, HEALTH)) || warnings.get() != 1 || exceptions.get() != 0) {
            throw new AssertionError("404 requests " + requests + ", WARN=" + warnings + ", exceptions=" + exceptions);
        }
        browse.onClose();
        if (client.screen != wardrobe || wardrobe.online()) throw new AssertionError("Wardrobe did not show Offline");
        UiHarness.press(wardrobe.sampleCard("bands"), "Bands while offline");
        UiHarness.press(wardrobe.styleChip(CapeStyles.Chip.WAVY), "Wavy while offline");
        if (!CapeBoard.sampleOn("bands") || !CapeBoard.styleOn("wave")) {
            throw new AssertionError("Local equipping and style changes failed while offline");
        }
        openBrowse(client);
        assertOffline();
        checked = true;
        return true;
    }

    BrowseScreen screen() { return browse; }

    String check() {
        assertOffline();
        if (!List.copyOf(requests).equals(List.of(HEALTH, HEALTH)) || warnings.get() != 1 || exceptions.get() != 0) {
            throw new AssertionError("404 requests " + requests + ", WARN=" + warnings + ", exceptions=" + exceptions);
        }
        return "Advanced URL applied, health returned 404 twice, WARN=1, exceptions=0";
    }

    private void openBrowse(Minecraft client) {
        if (client.screen != wardrobe) client.setScreen(wardrobe);
        UiHarness.press(UiHarness.button(wardrobe, BROWSE), "Browse");
        if (!(client.screen instanceof BrowseScreen opened)) throw new AssertionError("Browse did not open");
        browse = opened;
    }

    private void assertOffline() {
        var issue = CapeHttp.serviceIssue();
        var retry = UiHarness.button(browse, RETRY);
        if (issue == null || issue.reason() != ServiceResponse.OfflineReason.WRONG_SERVICE
                || !url.equals(browse.unavailableUrl()) || !CapeTheme.text("site.unreachable").equals(browse.emptyHeading())
                || retry == null || !retry.active || browse.isLoading() || !browse.items().isEmpty() || !browse.status().isEmpty()) {
            throw new AssertionError("Browse did not settle into the offline state");
        }
    }

    @Override public void close() {
        settings.baseUrl = oldUrl;
        settings.localOnly = oldOffline;
        server.stop(0);
        logger.removeAppender(appender);
        appender.stop();
    }
}
