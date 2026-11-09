package dev.quarkos.capes.gametest;

import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.render.CapeLayerWatch;
import dev.quarkos.capes.client.render.CapeTextures;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelPart;

/**
 * The local player's cape layer has to run, and the texture it uses has to be the equipped sample.
 * A screenshot of Steve's shirt is not this test.
 */
@SuppressWarnings("UnstableApiUsage")
public class CapeLayerClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> client.options.pauseOnLostFocus = false);
        try (TestSingleplayerContext world = context.worldBuilder()
                .adjustSettings(settings -> {
                    settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                    settings.setAllowCommands(true);
                })
                .create()) {
            world.getClientWorld().waitForChunksRender();
            context.runOnClient(client -> {
                client.options.setModelPart(PlayerModelPart.CAPE, true);
                client.options.broadcastOptions();
                McBridge.thirdPerson(client.options);
                OwnerCape.set(OwnerCape.sampleBands());
                CapeBook.preview = false;
                CapeBook.draft = null;
                CapeLayerWatch.reset();
            });
            world.getServer().runCommand("tp @p 0 80 0 180 12");
            context.waitTicks(40);
            int calls = context.computeOnClient(client -> CapeLayerWatch.localCalls);
            Identifier texture = context.computeOnClient(client -> CapeLayerWatch.localTexture);
            Identifier equipped = context.computeOnClient(client -> {
                CapeTextures.Image image = CapeTextures.get("local");
                return image == null ? null : image.id();
            });
            if (calls < 1) {
                throw new AssertionError("cape layer was not invoked for the local player");
            }
            if (texture == null) {
                throw new AssertionError("cape layer ran for the local player with a null texture after " + calls + " calls");
            }
            if (equipped == null || !equipped.equals(texture)) {
                throw new AssertionError("local player cape texture was " + texture + " but the equipped sample is " + equipped);
            }
            System.out.println("quarkos_capes layer ok calls=" + calls + " texture=" + texture);
        }
    }
}
