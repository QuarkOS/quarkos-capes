package dev.quarkos.capes;

import dev.quarkos.capes.client.render.CapeCompatibility;
import dev.quarkos.capes.client.render.CapeRenderPass;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CapeCompatibilityTest {
    private static class VanillaCape { }
    private static final class MissingCape extends VanillaCape { }

    @Test void vanillaCapeLayerPreventsFallback() {
        assertFalse(CapeCompatibility.needsFallback(List.of(new Object(), new VanillaCape()), VanillaCape.class::isInstance));
    }

    @Test void absentCapeGetsExactlyOneFallbackEvenWithOtherFeatures() {
        List<Object> layers = new ArrayList<>(List.of(new Object()));
        assertTrue(CapeCompatibility.needsFallback(layers, VanillaCape.class::isInstance));
        layers.add(new MissingCape());
        assertFalse(CapeCompatibility.needsFallback(layers, VanillaCape.class::isInstance));
        assertTrue(CapeCompatibility.needsFallback(List.of(), VanillaCape.class::isInstance));
    }

    @Test void firstPersonHidesCameraEntityButPreservesOtherPlayersAndGui() {
        assertFalse(CapeRenderPass.allowed(false, true, true, false));
        assertTrue(CapeRenderPass.allowed(false, false, true, false));
        assertTrue(CapeRenderPass.allowed(false, true, false, false));
        assertTrue(CapeRenderPass.allowed(true, true, true, false));
    }

    @Test void shaderShadowPassKeepsVanillaVisibilityAndNeverCountsAsWorldDraw() {
        assertTrue(CapeRenderPass.allowed(false, true, true, true));
        assertFalse(CapeRenderPass.worldDraw(false, true));
        assertFalse(CapeRenderPass.worldDraw(true, false));
        assertFalse(CapeRenderPass.worldDraw(true, true));
        assertTrue(CapeRenderPass.worldDraw(false, false));
    }
}
