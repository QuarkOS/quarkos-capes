package dev.quarkos.capes;

import dev.quarkos.capes.client.screen.SettingsEdits;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SettingsEditsTest {
    @Test void browseGoOnlineSurvivesReturningRenderingAndSaving() {
        AtomicBoolean offlineEntry = new AtomicBoolean(true);
        AtomicBoolean offlineSetting = new AtomicBoolean(true);
        AtomicInteger writes = new AtomicInteger();
        SettingsEdits edits = new SettingsEdits();
        edits.track(offlineEntry::get, value -> { offlineSetting.set(value); writes.incrementAndGet(); });
        offlineSetting.set(false);
        edits.apply();
        edits.apply();
        assertFalse(offlineSetting.get());
        assertEquals(0, writes.get());
    }

    @Test void appliedEditsAreNotReplayedOverLaterExternalChanges() {
        AtomicReference<String> entry = new AtomicReference<>("flat");
        AtomicReference<String> setting = new AtomicReference<>("flat");
        SettingsEdits edits = new SettingsEdits();
        edits.track(entry::get, setting::set);
        entry.set("frames");
        edits.apply();
        assertEquals("frames", setting.get());
        setting.set("model"); // Sample Equip / Wear / Unwear / harness replaces the cape.
        edits.apply();
        assertEquals("model", setting.get());
        entry.set("flat"); // A later explicit edit, including back to the initial value, still applies.
        edits.apply();
        assertEquals("flat", setting.get());
    }

    @Test void changingOneFieldPreservesOtherExternallyChangedFields() {
        record Physics(int gravity, int height) { }
        AtomicReference<Physics> setting = new AtomicReference<>(new Physics(25, 6));
        AtomicInteger gravityEntry = new AtomicInteger(25);
        AtomicInteger heightEntry = new AtomicInteger(6);
        SettingsEdits edits = new SettingsEdits();
        edits.track(gravityEntry::get, value -> setting.set(new Physics(value, setting.get().height())));
        edits.track(heightEntry::get, value -> setting.set(new Physics(setting.get().gravity(), value)));
        setting.set(new Physics(25, 12));
        gravityEntry.set(18);
        edits.apply();
        assertEquals(new Physics(18, 12), setting.get());
    }

    @Test void unchangedStringsUseValueEqualityAndFreshScreensStartClean() {
        AtomicReference<String> entry = new AtomicReference<>("https://service.org");
        AtomicInteger writes = new AtomicInteger();
        SettingsEdits edits = new SettingsEdits();
        edits.track(entry::get, value -> writes.incrementAndGet());
        entry.set(new String("https://service.org"));
        edits.apply();
        assertEquals(0, writes.get());
        entry.set("https://other.org");
        SettingsEdits fresh = new SettingsEdits();
        fresh.track(entry::get, value -> writes.incrementAndGet());
        fresh.apply();
        assertEquals(0, writes.get());
    }
}
