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
    private static byte[] localPng;

    private CapeDraw() {
    }

    public static void syncLocal() {
        OwnerCape cape = CapeBook.preview && CapeBook.draft != null ? CapeBook.draft : OwnerCape.get();
        if (cape.png == localPng && (cape.png.length == 0 || CapeTextures.get("local") != null)) {
            return;
        }
        localPng = cape.png;
        if (cape.png.length == 0) { CapeTextures.clear("local"); return; }
        if (CapeTextures.upload("local", cape.png, false, false) == null) {
            CapeTextures.clear("local");
        }
    }

    public static void emit(PoseStack.Pose pose, VertexConsumer consumer, int light, UUID id, CapeBook.Choice choice) {
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
            WaveCloth.Motion motion = fact == null ? WaveCloth.Motion.still() : ClothSim.motion(fact);
            rememberGravity(pose, id, fact);
            WaveCloth.State state = ClothSim.state(id);
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
                    //? if <1.21.11 {
                    /*Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)
                    *///?} else {
                    Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false)
                    //?}
            );
            ClothSim.markDrawn(id);
        } else {
            quads = CapeGeometry.flat(frame, frames, choice.vertical());
        }
        for (CapeGeometry.Quad quad : quads) {
            McBridge.quad(consumer, pose, light, quad);
        }
        CapeBook.drawn(id);
    }

    public static CapeBook.Choice resolve(UUID id, boolean local, Identifier vanillaCape) {
        if (local) {
            syncLocal();
            OwnerCape cape = CapeBook.preview && CapeBook.draft != null ? CapeBook.draft : OwnerCape.get();
            CapeTextures.Image image = CapeTextures.get("local");
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
            return CapeBook.local(cape, image);
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
