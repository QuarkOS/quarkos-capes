package dev.quarkos.capes.release;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReleaseServicePolicyTest {
    @Test void localAndDocumentationHostsNeedTheExplicitOverride() {
        for (String blocked : new String[] {
                "http://127.0.0.1:8787",
                "http://127.0.0.1",
                "http://localhost:8787",
                "http://localhost",
                "https://api.localhost/capes",
                "http://[::1]:8787",
                "http://0.0.0.0:8787",
                "http://2130706433",
                "http://0x7f000001",
                "http://example.org/quarkos-capes",
                "https://capes.example.com",
                "http://capes.test",
                "http://capes.invalid",
                "http://printer.local",
                "http://10.0.08.1"
        }) {
            assertThrows(IllegalArgumentException.class, () -> ReleaseServicePolicy.validate(blocked, false), blocked);
            assertDoesNotThrow(() -> ReleaseServicePolicy.validate(blocked, true), blocked);
        }
    }

    @Test void aPublicHttpsOriginIsAcceptedWithoutTheOverride() {
        assertDoesNotThrow(() -> ReleaseServicePolicy.validate("https://capes.quarkos.dev", false));
        assertDoesNotThrow(() -> ReleaseServicePolicy.validate("https://capes.quarkos.dev:443/v1", false));
    }

    @Test void malformedUrlsAreRejectedEvenWhenLocalIsAllowed() {
        for (String blocked : new String[] {
                "",
                "capes.quarkos.dev",
                "ftp://capes.quarkos.dev",
                "https://user:pass@capes.quarkos.dev",
                "https://capes.quarkos.dev/capes?q=1",
                "https://capes.quarkos.dev/capes#top",
                "http://capes.quarkos.dev:0"
        }) {
            assertThrows(IllegalArgumentException.class, () -> ReleaseServicePolicy.validate(blocked, true), blocked);
        }
    }
}
