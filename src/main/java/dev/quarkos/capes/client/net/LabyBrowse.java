package dev.quarkos.capes.client.net;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.LocalFiles;
import dev.quarkos.capes.client.render.LabyWear;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/** Previous and next through the dev catalog. Does nothing when the flag or offline mode is closed. */
public final class LabyBrowse {
    private static final List<LabyCatalog.Item> ITEMS = new ArrayList<>();
    private static int index = -1;
    private static int capeOffset;
    private static int cloakOffset;
    private static boolean capeDone;
    private static boolean cloakDone;
    private static boolean busy;
    private static String status = "";

    private LabyBrowse() {
    }

    public static String status() {
        return status;
    }

    public static void next(Runnable done) {
        step(1, done);
    }

    public static void previous(Runnable done) {
        step(-1, done);
    }

    private static void step(int direction, Runnable done) {
        if (!LabyCatalog.allow(ClientSettings.get().localOnly)) {
            finish(done);
            return;
        }
        if (busy) {
            finish(done);
            return;
        }
        int target = index + direction;
        if (target < 0) {
            finish(done);
            return;
        }
        if (target < ITEMS.size()) {
            show(target, done);
            return;
        }
        if (direction < 0 || (capeDone && cloakDone)) {
            finish(done);
            return;
        }
        String kind = capeDone ? "cloak" : "cape";
        int offset = "cape".equals(kind) ? capeOffset : cloakOffset;
        busy = true;
        Thread thread = new Thread(() -> load(kind, offset, target, direction, done), "quarkos-laby-catalog");
        thread.setDaemon(true);
        thread.start();
    }

    private static void load(String kind, int offset, int target, int direction, Runnable done) {
        List<LabyCatalog.Item> found;
        String error = null;
        try {
            found = LabyCatalog.page(true, kind, LabyCatalog.PAGE, offset, CapeHttp::labyExchange);
        } catch (Exception e) {
            found = List.of();
            error = net.minecraft.network.chat.Component.translatable("text.quarkos_capes.status.catalog_failed").getString();
        }
        List<LabyCatalog.Item> page = found;
        String message = error;
        Minecraft.getInstance().execute(() -> {
            busy = false;
            if (message != null) {
                status = message;
                finish(done);
                return;
            }
            boolean repeat = !page.isEmpty() && seen(page.get(0).hash());
            if ("cape".equals(kind)) {
                capeOffset += page.size();
                capeDone = repeat || page.size() < LabyCatalog.PAGE;
            } else {
                cloakOffset += page.size();
                cloakDone = repeat || page.size() < LabyCatalog.PAGE;
            }
            if (!repeat) {
                ITEMS.addAll(page);
            }
            if (target < ITEMS.size()) {
                show(target, done);
            } else if (!capeDone || !cloakDone) {
                step(direction, done);
            } else {
                finish(done);
            }
        });
    }

    private static void show(int target, Runnable done) {
        LabyCatalog.Item item = ITEMS.get(target);
        busy = true;
        Thread thread = new Thread(() -> {
            byte[] png = null;
            try {
                png = LabySource.texture(item.hash(), LocalFiles.folder().resolve("labymod"), CapeHttp::labyExchange);
            } catch (Exception ignored) {
                png = null;
            }
            byte[] shown = png;
            Minecraft.getInstance().execute(() -> {
                busy = false;
                index = target;
                if (shown != null) {
                    LabyWear.wear(shown);
                }
                status = item.label();
                finish(done);
            });
        }, "quarkos-laby-texture");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean seen(String hash) {
        for (LabyCatalog.Item item : ITEMS) {
            if (item.hash().equalsIgnoreCase(hash)) {
                return true;
            }
        }
        return false;
    }

    private static void finish(Runnable done) {
        if (done != null) {
            Minecraft.getInstance().execute(done);
        }
    }
}
