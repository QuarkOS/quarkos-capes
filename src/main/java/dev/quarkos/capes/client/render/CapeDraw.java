package dev.quarkos.capes.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.sim.CapeGeometry;
import dev.quarkos.capes.sim.StripFrames;
import dev.quarkos.capes.sim.WaveCloth;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.joml.Matrix4f;

import java.util.List;
import java.util.UUID;

/** One draw path for the world and the picker preview. */
public final class CapeDraw {
    private static final TextureSlot LOCAL = new TextureSlot("local");
    private static final TextureSlot PREVIEW = new TextureSlot("preview");

    private CapeDraw() {
    }

    public static void syncLocal() {
        LOCAL.sync(OwnerCape.get().png);
    }

    private static final class TextureSlot {
        private final String key;
        private byte[] current;
        private byte[] rejected;

        private TextureSlot(String key) { this.key = key; }

        private CapeTextures.Image sync(byte[] png) {
            CapeTextures.Image image = CapeTextures.get(key);
            if (png == current && (png.length == 0 || image != null || png == rejected)) return image;
            current = png;
            image = png.length == 0 ? null : CapeTextures.upload(key, png, false, false);
            rejected = image == null ? png : null;
            if (image == null) CapeTextures.clear(key);
            return image;
        }
    }

    public static void emit(PoseStack.Pose pose, VertexConsumer consumer, int light, UUID id, CapeBook.Choice choice, boolean preview) {
        if (choice.kind() == CapeBook.Kind.NONE || choice.texture() == null) {
            return;
        }
        int frames = Math.max(1, choice.frames());
        int frame = 0;
        if (choice.animate() && frames > 1) {
            frame = StripFrames.index(System.currentTimeMillis(), choice.delays());
        }
        List<CapeGeometry.Quad> quads;
        if (choice.kind() == CapeBook.Kind.MODEL) {
            quads = CapeGeometry.model(choice.modelJson(), frame, frames, choice.frameWidth(), choice.frameHeight(), choice.vertical());
        } else if (choice.kind() == CapeBook.Kind.CLOTH) {
            WaveCloth.Settings settings = ClientSettings.get().cloth;
            PlayerFacts.Fact fact = PlayerFacts.byUuid(id);
            WaveCloth.Motion motion = preview || fact == null ? WaveCloth.Motion.still() : ClothSim.motion(fact);
            if (!preview) rememberGravity(pose, id, fact);
            WaveCloth.State state = preview ? dev.quarkos.capes.sim.PreviewCloth.state(settings) : ClothSim.state(id);
            WaveCloth.ClassicPose classic = settings.movement() == WaveCloth.Movement.CLASSIC
                    ? WaveCloth.classic(settings, motion, state) : null;
            quads = CapeGeometry.cloth(
                    state,
                    settings,
                    motion,
                    classic == null ? null : classic.pitch(),
                    classic == null ? null : classic.roll(),
                    frame,
                    frames,
                    choice.vertical(),
                    settings.mesh() == WaveCloth.Mesh.BLOCKY,
                    McBridge.partialTick()
            );
            if (!preview) ClothSim.markDrawn(id);
        } else {
            quads = CapeGeometry.flat(frame, frames, choice.vertical());
        }
        for (CapeGeometry.Quad quad : quads) {
            McBridge.quad(consumer, pose, light, quad);
        }
        if (!quads.isEmpty()) CapeBook.drawn(id, choice.texture(), preview);
    }

    public static CapeBook.Choice resolve(UUID id, boolean local, Identifier vanillaCape) {
        if (local) {
            boolean preview = CapeRenderPass.guiDepth > 0 || CapeBook.renderingPreview;
            OwnerCape cape = preview && CapeBook.preview && CapeBook.draft != null ? CapeBook.draft : OwnerCape.get();
            PlayerFacts.Fact fact = PlayerFacts.byUuid(id);
            // The synced customization byte starts at 0, so isModelPartShown(CAPE) is false
            // until the server applies client settings. Options are the local player's choice.
            boolean capeOn = Minecraft.getInstance().options.isModelPartEnabled(PlayerModelPart.CAPE);
            if (!capeOn || (fact != null && fact.elytra())) {
                return CapeBook.none();
            }
//? if dev_catalog {
            /*if (LabyWear.showing()) {
                return LabyWear.choice();
            }
*///?}
            TextureSlot slot = cape == OwnerCape.get() ? LOCAL : PREVIEW;
            return CapeBook.local(cape, slot.sync(cape.png));
        }
        PlayerFacts.Fact fact = PlayerFacts.byUuid(id);
        if (fact != null && (fact.elytra() || !fact.capePart())) {
            return CapeBook.none();
        }
        return CapeBook.other(id, vanillaCape, true);
    }

    private static WaveCloth.Motion lean(WaveCloth.Motion motion, PlayerFacts.Fact fact) {
        if (fact == null) {
            return motion;
        }
        return motion;
    }

    private static void rememberGravity(PoseStack.Pose pose, UUID id, PlayerFacts.Fact fact) {
        if (!ClientSettings.get().cloth.computeGravityVector() || id == null) {
            return;
        }
        Matrix4f matrix = pose.pose();
        float ux = matrix.m10();
        float uy = matrix.m11();
        float uz = matrix.m12();
        float yaw = fact == null ? 0f : (float) Math.toRadians(fact.yRot());
        float c = (float) Math.cos(yaw);
        float s = (float) Math.sin(yaw);
        ClothSim.gravity(id, ux * c + uz * s, uy, -ux * s + uz * c);
    }
}
