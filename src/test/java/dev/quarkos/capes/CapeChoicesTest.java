package dev.quarkos.capes;

import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.screen.CapeBoard;
import dev.quarkos.capes.client.screen.CapeChoices;
import dev.quarkos.capes.sim.JsonValues;
import dev.quarkos.capes.sim.WaveCloth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CapeChoicesTest {
    @TempDir Path directory;

    @Test void selectingFlatStopsPlaybackWithoutReplacingTheStrip() {
        OwnerCape previous=OwnerCape.get();
        try {
            OwnerCape strip=OwnerCape.sampleSlide();
            OwnerCape.set(strip);
            byte[] png=strip.png;
            CapeBoard.style(CapeChoices.id(CapeChoices.Style.FLAT));
            assertEquals("flat",strip.style);
            assertFalse(strip.animateFrames);
            assertSame(png,strip.png);
            assertTrue(strip.frameCount>1);
            CapeBoard.style(CapeChoices.id(CapeChoices.Style.FRAMES));
            assertTrue(strip.animateFrames);
            assertSame(png,strip.png);
        } finally { OwnerCape.set(previous); }
    }

    @Test void selectorPhysicsValuesRoundTripUsingExistingSettingIds() throws Exception {
        var previous=ClientSettings.get().cloth;
        try {
            CapeBoard.movement(WaveCloth.Movement.SIMULATION);
            CapeBoard.mesh(WaveCloth.Mesh.BLOCKY);
            CapeBoard.wind(WaveCloth.Wind.WAVES);
            var settings=ClientSettings.get();
            var doc=JsonValues.object(JsonValues.parse(settings.json()));
            assertEquals("simulation",doc.get("capeMovement"));
            assertEquals("blocky",doc.get("capeStyle"));
            assertEquals("waves",doc.get("windMode"));
            Path file=directory.resolve("settings.json");settings.save(file);
            ClientSettings.load(file);
            assertEquals(WaveCloth.Movement.SIMULATION,ClientSettings.get().cloth.movement());
            assertEquals(WaveCloth.Mesh.BLOCKY,ClientSettings.get().cloth.mesh());
            assertEquals(WaveCloth.Wind.WAVES,ClientSettings.get().cloth.wind());
        } finally { ClientSettings.get().cloth=previous; }
    }
}
