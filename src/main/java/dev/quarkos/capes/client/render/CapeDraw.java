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
import org.joml.Quaternionf;

import java.util.List;
import java.util.UUID;
//? if <1.21.2 {
/*import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
*///?}
//? if >=1.21.11 {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//?}

/** One draw path for the world and the picker preview. */
public final class CapeDraw {
    private static final float SHOULDER_Z = 2f / 16f;
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

    //? if <1.21.2 {
    /*private record LegacyCapeAngles(float lean, float flap, float sway) {
        private static final LegacyCapeAngles RESTING = new LegacyCapeAngles(0f, 0f, 0f);
    }

    private static LegacyCapeAngles legacyCapeAngles(AbstractClientPlayer player, float partial) {
        // These versions compute the cape angles directly in CapeLayer.
        double dx = Mth.lerp(partial, player.xCloakO, player.xCloak) - Mth.lerp(partial, player.xo, player.getX());
        double dy = Mth.lerp(partial, player.yCloakO, player.yCloak) - Mth.lerp(partial, player.yo, player.getY());
        double dz = Mth.lerp(partial, player.zCloakO, player.zCloak) - Mth.lerp(partial, player.zo, player.getZ());
        float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot) * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yaw), cos = -Mth.cos(yaw);
        float lean = Mth.clamp((float) (dx * sin + dz * cos) * 100f, 0f, 150f);
        float sway = Mth.clamp((float) (dx * cos - dz * sin) * 100f, -20f, 20f);
        float bob = Mth.lerp(partial, player.oBob, player.bob);
        float flap = Mth.clamp((float) dy * 10f, -6f, 32f)
                + Mth.sin(Mth.lerp(partial, player.walkDistO, player.walkDist) * 6f) * 32f * bob;
        if (player.isCrouching()) flap += 25f;
        return new LegacyCapeAngles(lean, flap, sway);
    }

    public static float legacyRigidPitch(AbstractClientPlayer player, float partial) {
        LegacyCapeAngles angles = legacyCapeAngles(player, partial);
        return rigidPitch(player.getUUID(), angles.lean(), angles.flap(), angles.sway(), player.isFallFlying());
    }

    public static void emit(PoseStack pose, VertexConsumer consumer, int light, AbstractClientPlayer player,
                            CapeBook.Choice choice, boolean preview, float partial) {
        LegacyCapeAngles angles = preview || choice.kind() == CapeBook.Kind.CLOTH
                ? LegacyCapeAngles.RESTING : legacyCapeAngles(player, partial);
        emit(pose, consumer, light, player.getUUID(), choice, preview, partial,
                angles.lean(), angles.flap(), angles.sway(), player.isFallFlying());
    }
    *///?}

    //? if <1.21.11 {
    /*public static void emit(PoseStack pose, VertexConsumer consumer, int light, UUID id, CapeBook.Choice choice,
                            boolean preview, float partial, float lean, float flap, float sway, boolean flying) {
        List<CapeGeometry.Quad> quads = geometry(pose.last(), id, choice, preview, partial);
        if (!quads.isEmpty() && choice.kind() != CapeBook.Kind.CLOTH) {
            rotateRigid(pose, id, lean, flap, sway, flying, preview);
        }
        draw(quads, pose.last(), consumer, light, id, choice, preview);
    }
    *///?}

    //? if >=1.21.11 {
    public static void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state,
                              UUID id, CapeBook.Choice choice, boolean preview) {
        float partial = 1f;
        if (!preview && choice.kind() == CapeBook.Kind.CLOTH) {
            PlayerFacts.Fact fact = PlayerFacts.byUuid(id);
            partial = fact == null ? McBridge.partialTick() : state.ageInTicks - fact.tickAge();
        }
        List<CapeGeometry.Quad> quads = geometry(pose.last(), id, choice, preview, partial);
        if (quads.isEmpty()) return;

        Quaternionf rotation = choice.kind() == CapeBook.Kind.CLOTH ? null
                : rotateRigid(pose, id, state.capeLean, state.capeFlap, state.capeLean2, state.isFallFlying, preview);
        collector.submitCustomGeometry(pose, McBridge.cutout(choice.texture(), choice.kind()), (entry, consumer) -> {
            draw(quads, entry, consumer, preview ? 15728880 : light, id, choice, preview);
            if (dev.quarkos.capes.DevFlags.HARNESS && !preview && !CapeRenderPass.shadow()) {
                if (rotation == null) dev.quarkos.capes.client.harness.CapeMotionCapture.cloth(id, quads);
                else dev.quarkos.capes.client.harness.CapeMotionCapture.rigid(id, choice.kind(), rotation);
            }
        });
    }
    //?}

    /** Vanilla adds half of capeLean to the resting pitch. */
    public static float rigidLean(PlayerFacts.Fact fact, float vanillaLean, boolean flying) {
        if (fact == null || flying) return vanillaLean;
        float speed = (float) Math.hypot(fact.x() - fact.px(), fact.z() - fact.pz());
        return 2f * (WaveCloth.targetAngleDegrees(speed) - WaveCloth.REST_ANGLE_DEGREES);
    }

    /** Limit the combined pitch and sway in the extracted vanilla pose. */
    public static float boundedFlap(float lean, float flap, float sway, boolean flying) {
        if (flying) return flap;
        float basePitch = WaveCloth.REST_ANGLE_DEGREES + lean / 2f;
        return Math.min(flap, WaveCloth.maxPitchDegrees(sway / 2f) - basePitch);
    }

    private static float rigidPitch(UUID id, float vanillaLean, float flap, float sway, boolean flying) {
        float lean = rigidLean(PlayerFacts.byUuid(id), vanillaLean, flying);
        return WaveCloth.REST_ANGLE_DEGREES + lean / 2f + boundedFlap(lean, flap, sway, flying);
    }

    private static Quaternionf rotateRigid(PoseStack pose, UUID id, float lean, float flap, float sway, boolean flying, boolean preview) {
        float pitch = WaveCloth.REST_ANGLE_DEGREES;
        float roll = 0f;
        if (!preview) {
            pitch = rigidPitch(id, lean, flap, sway, flying);
            roll = sway / 2f;
        }
        float pitchRadians = (float) Math.toRadians(pitch);
        float rollRadians = (float) Math.toRadians(roll);
        // The vanilla model starts with a 180-degree yaw on its cape part.
        var rotation = new Quaternionf().rotationX(pitchRadians).rotateZ(rollRadians).rotateY(-rollRadians);
        pose.translate(0f, 0f, SHOULDER_Z);
        //? if >=26.1 {
        /*pose.rotate(rotation);
        *///?} else {
        pose.mulPose(rotation);
        //?}
        pose.translate(0f, 0f, -SHOULDER_Z);
        return rotation;
    }

    private static List<CapeGeometry.Quad> geometry(PoseStack.Pose pose, UUID id, CapeBook.Choice choice, boolean preview, float partial) {
        if (choice.kind() == CapeBook.Kind.NONE || choice.texture() == null) return List.of();
        int frames = Math.max(1, choice.frames());
        int frame = 0;
        if (choice.animate() && frames > 1) {
            frame = StripFrames.index(System.currentTimeMillis(), choice.delays());
        }
        if (choice.kind() == CapeBook.Kind.MODEL) {
            return CapeGeometry.model(choice.modelJson(), frame, frames, choice.frameWidth(), choice.frameHeight(), choice.vertical());
        }
        if (choice.kind() == CapeBook.Kind.CLOTH) {
            WaveCloth.Settings settings = ClientSettings.get().cloth;
            PlayerFacts.Fact fact = preview ? null : PlayerFacts.byUuid(id);
            if (!preview) {
                rememberGravity(pose, id);
                ClothSim.markDrawn(id);
            }
            WaveCloth.Motion motion = preview ? WaveCloth.Motion.idle(System.nanoTime() / 1_000_000L)
                    : fact == null ? WaveCloth.Motion.still() : ClothSim.motion(fact);
            WaveCloth.State state = preview ? dev.quarkos.capes.sim.PreviewCloth.state(settings, motion) : ClothSim.state(id);
            WaveCloth.ClassicPose classic = settings.movement() == WaveCloth.Movement.CLASSIC
                    ? WaveCloth.classic(settings, motion, state) : null;
            return CapeGeometry.cloth(
                    state,
                    settings,
                    classic == null ? null : classic.pitch(),
                    classic == null ? null : classic.roll(),
                    frame,
                    frames,
                    choice.vertical(),
                    settings.mesh() == WaveCloth.Mesh.BLOCKY,
                    preview ? 1f : partial
            );
        }
        return CapeGeometry.flat(frame, frames, choice.vertical());
    }

    private static void draw(List<CapeGeometry.Quad> quads, PoseStack.Pose pose, VertexConsumer consumer,
                             int light, UUID id, CapeBook.Choice choice, boolean preview) {
        for (CapeGeometry.Quad quad : quads) {
            McBridge.quad(consumer, pose, light, quad);
        }
        if (!quads.isEmpty()) CapeBook.drawn(id, choice.texture(), preview);
    }

    public static CapeBook.Choice resolve(UUID id, boolean local, Identifier vanillaCape) {
        return resolve(id, local, vanillaCape, CapeRenderPass.guiDepth > 0 || CapeBook.renderingPreview);
    }

    public static CapeBook.Choice resolve(UUID id, boolean local, Identifier vanillaCape, boolean preview) {
        if (local) {
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

    private static void rememberGravity(PoseStack.Pose pose, UUID id) {
        if (!ClientSettings.get().cloth.computeGravityVector() || id == null) {
            return;
        }
        Matrix4f matrix = pose.pose();
        // Project world down onto the model axes. The sim uses negative Y for down.
        float x = -matrix.m01(), y = matrix.m11(), z = -matrix.m21();
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length > 0f) ClothSim.gravity(id, x / length, y / length, z / length);
    }
}
