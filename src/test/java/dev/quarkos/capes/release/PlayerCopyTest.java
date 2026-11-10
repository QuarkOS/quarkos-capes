package dev.quarkos.capes.release;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Player-facing strings stay free of auth jargon and developer-only wording. */
class PlayerCopyTest {
    @Test void englishCopyDoesNotExposeDevOrAuthInternals() throws Exception {
        try (var stream = PlayerCopyTest.class.getResourceAsStream("/assets/quarkos_capes/lang/en_us.json")) {
            assertNotNull(stream, "english language file");
            String lang = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(lang.contains("hasJoined"), "gallery errors should not name hasJoined");
            assertFalse(lang.contains("Developer mode"), "shipped labels should not mention developer mode");
            assertFalse(lang.contains("strave"), "strafe labels should use the spelled word");
            assertFalse(lang.contains("127.0.0.1"), "labels should not bake a localhost URL");
            assertFalse(lang.contains("localhost"), "labels should not bake localhost");
        }
    }
}
