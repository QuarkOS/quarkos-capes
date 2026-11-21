package dev.quarkos.capes.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.quarkos.capes.DevFlags;
import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.local.ClientSettings;
import dev.quarkos.capes.client.local.OwnerCape;
import dev.quarkos.capes.sim.CapeGeometry;
import dev.quarkos.capes.sim.CapeLift;
import dev.quarkos.capes.sim.PreviewCloth;
import dev.quarkos.capes.sim.StripFrames;
import dev.quarkos.capes.sim.WaveCloth;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
    public static final String TRY_ON_TEXTURE = "try-on";
    private static final float SHOULDER_Z = 2f / 16f;
    private static final TextureSlot LOCAL = new TextureSlot("local");
    private static final TextureSlot PREVIEW = new TextureSlot("preview");
    private static final List<CapeGeometry.Quad> FLAT = CapeGeometry.flat(0, 1, false);
    private static final ThreadLocal<Quaternionf> ROTATION = ThreadLocal.withInitial(Quaternionf::new);
    private static final ThreadLocal<WaveCloth.State> PREVIEW_CLOTH = ThreadLocal.withInitial(WaveCloth.State::new);
    private static final boolean DEFAULT_SMOOTHING = !DevFlags.HARNESS
            || !"false".equalsIgnoreCase(System.getProperty("quarkos_capes.smooth"));
    private static boolean smoothing = DEFAULT_SMOOTHING;
    private record TryOn(LocalPlayer player, CameraType previousCamera, CapeBook.Choice choice) { }
    private static TryOn tryOn;

    private CapeDraw() {
    }

    public static void syncLocal() {
        LOCAL.sync(OwnerCape.get().png);
    }

    public static boolean tryOn(Minecraft client, OwnerCape cape) {
        if (client.player == null) return false;
        tickTryOn(client);
        var texture = CapeTextures.upload(TRY_ON_TEXTURE, cape.png, false, false);
        if (texture == null) return false;
        tryOn = new TryOn(client.player, client.options.getCameraType(), CapeBook.local(cape, texture));
        client.setScreen(null);
        McBridge.thirdPerson(client.options);
        return true;
    }

    public static void tickTryOn(Minecraft client) {
        if (tryOn == null || (client.player == tryOn.player() && client.screen == null)) return;
        if (client.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
            client.options.setCameraType(tryOn.previousCamera());
        }
        tryOn = null;
        CapeTextures.clear(TRY_ON_TEXTURE);
    }

    public static void harnessSmoothing(Boolean enabled) {
        if (DevFlags.HARNESS) smoothing = enabled == null ? DEFAULT_SMOOTHING : enabled;
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
        return rigidPitch(PlayerFacts.byUuid(player.getUUID()), partial,
                angles.lean(), angles.flap(), angles.sway(), player.isFallFlying());
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
        int frame = animationFrame(choice);
        List<CapeGeometry.Quad> quads = geometry(pose.last(), id, choice, preview, partial, frame);
        if (quads.isEmpty()) return;
        if (choice.kind() != CapeBook.Kind.CLOTH) {
            float pitch = preview ? WaveCloth.REST_ANGLE_DEGREES : rigidPitch(PlayerFacts.byUuid(id), partial, lean, flap, sway, flying);
            rotateRigid(pose, pitch, preview ? 0f : sway);
        }
        draw(quads, pose.last(), consumer, light, id, choice, preview, frame);
    }
    *///?}

    //? if >=1.21.11 {
    public static void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state,
                              UUID id, CapeBook.Choice choice, boolean preview) {
        boolean cloth = choice.kind() == CapeBook.Kind.CLOTH;
        PlayerFacts.Fact fact = preview ? null : PlayerFacts.byUuid(id);
        float partial = preview ? 1f : McBridge.playerPartialTick();
        if (cloth && fact != null && !smoothing) {
            partial = state.ageInTicks - fact.tickAge();
        }
        int frame = animationFrame(choice);
        List<CapeGeometry.Quad> quads = geometry(pose.last(), id, choice, preview, partial, frame);
        if (quads.isEmpty()) return;

        float pitch = preview || cloth ? WaveCloth.REST_ANGLE_DEGREES
                : rigidPitch(fact, partial, state.capeLean, state.capeFlap, state.capeLean2, state.isFallFlying);
        float sway = preview ? 0f : state.capeLean2;
        if (!cloth) rotateRigid(pose, pitch, sway);
        collector.submitCustomGeometry(pose, McBridge.cutout(choice.texture(), choice.kind()), (entry, consumer) -> {
            draw(quads, entry, consumer, preview ? 15728880 : light, id, choice, preview, frame);
            if (DevFlags.HARNESS && !preview && !CapeRenderPass.shadow()) {
                if (cloth) dev.quarkos.capes.client.harness.CapeMotionCapture.cloth(id, quads);
                else dev.quarkos.capes.client.harness.CapeMotionCapture.rigid(id, choice.kind(), pitch, sway);
            }
        });
    }
    //?}

    /** Vanilla adds half of capeLean to the resting pitch. */
    public static float rigidLean(PlayerFacts.Fact fact, float vanillaLean, boolean flying, float partial) {
        if (fact == null || flying) return vanillaLean;
        float lift = smoothing ? fact.capeLift().at(partial) : CapeLift.target(fact.x() - fact.px(), fact.z() - fact.pz());
        return 2f * (lift - WaveCloth.REST_ANGLE_DEGREES);
    }

    /** Limit the combined pitch and sway in the extracted vanilla pose. */
    public static float boundedFlap(float lean, float flap, float sway, boolean flying) {
        if (flying) return flap;
        float basePitch = WaveCloth.REST_ANGLE_DEGREES + lean / 2f;
        return Math.min(flap, WaveCloth.maxPitchDegrees(sway / 2f) - basePitch);
    }

    private static float rigidPitch(PlayerFacts.Fact fact, float partial, float vanillaLean, float flap, float sway, boolean flying) {
        float lean = rigidLean(fact, vanillaLean, flying, partial);
        return WaveCloth.REST_ANGLE_DEGREES + lean / 2f + boundedFlap(lean, flap, sway, flying);
    }

    public static Quaternionf rigidRotation(Quaternionf rotation, float pitch, float sway) {
        float pitchRadians = (float) Math.toRadians(pitch);
        float rollRadians = (float) Math.toRadians(sway / 2f);
        // The vanilla model starts with a 180-degree yaw on its cape part.
        return rotation.rotationX(pitchRadians).rotateZ(rollRadians).rotateY(-rollRadians);
    }

    private static void rotateRigid(PoseStack pose, float pitch, float sway) {
        Quaternionf rotation = rigidRotation(ROTATION.get(), pitch, sway);
        pose.translate(0f, 0f, SHOULDER_Z);
        //? if >=26.1 {
        /*pose.rotate(rotation);
        *///?} else {
        pose.mulPose(rotation);
        //?}
        pose.translate(0f, 0f, -SHOULDER_Z);
    }

    private static int animationFrame(CapeBook.Choice choice) {
        return choice.animate() && choice.frames() > 1 ? StripFrames.index(System.currentTimeMillis(), choice.delays()) : 0;
    }

    private static List<CapeGeometry.Quad> geometry(PoseStack.Pose pose, UUID id, CapeBook.Choice choice,
                                                    boolean preview, float partial, int frame) {
        if (choice.kind() == CapeBook.Kind.NONE || choice.texture() == null) return List.of();
        if (choice.kind() == CapeBook.Kind.MODEL) {
            return CapeGeometry.model(choice.modelJson(), 0, 1, choice.frameWidth(), choice.frameHeight(), false);
        }
        if (choice.kind() == CapeBook.Kind.CLOTH) {
            WaveCloth.Settings settings = ClientSettings.get().cloth;
            if (!preview) {
                rememberGravity(pose, id);
                ClothSim.markDrawn(id);
            }
            WaveCloth.State state;
            WaveCloth.ClassicPose classic = null;
            if (preview) {
                state = PREVIEW_CLOTH.get();
                PreviewCloth.pose(state, settings, WaveCloth.Motion.idle(System.nanoTime() / 1_000_000L));
            } else {
                state = ClothSim.state(id);
                if (!smoothing && settings.movement() == WaveCloth.Movement.CLASSIC) {
                    PlayerFacts.Fact fact = PlayerFacts.byUuid(id);
                    WaveCloth.Motion motion = fact == null ? WaveCloth.Motion.still() : ClothSim.motion(fact);
                    classic = WaveCloth.classic(settings, motion, state);
                }
            }
            return CapeGeometry.cloth(
                    state,
                    settings,
                    classic == null ? null : classic.pitch(),
                    classic == null ? null : classic.roll(),
                    frame,
                    choice.frames(),
                    choice.vertical(),
                    settings.mesh() == WaveCloth.Mesh.BLOCKY,
                    preview ? 1f : partial
            );
        }
        return FLAT;
    }

    private static void draw(List<CapeGeometry.Quad> quads, PoseStack.Pose pose, VertexConsumer consumer,
                             int light, UUID id, CapeBook.Choice choice, boolean preview, int frame) {
        float scaleU = 1f, scaleV = 1f, offsetU = 0f, offsetV = 0f;
        // Rigid geometry uses UVs for one frame. Only the sampled part of the texture changes.
        if (choice.kind() != CapeBook.Kind.CLOTH && choice.frames() > 1) {
            float scale = 1f / choice.frames();
            float offset = frame * scale;
            if (choice.vertical()) {
                scaleV = scale;
                offsetV = offset;
            } else {
                scaleU = scale;
                offsetU = offset;
            }
        }
        for (int i = 0; i < quads.size(); i++) {
            McBridge.quad(consumer, pose, light, quads.get(i), offsetU, offsetV, scaleU, scaleV);
        }
        CapeBook.drawn(id, choice.texture(), preview, frame);
    }

    public static CapeBook.Choice resolve(UUID id, boolean local, Identifier vanillaCape) {
        return resolve(id, local, vanillaCape, CapeRenderPass.guiDepth > 0 || CapeBook.renderingPreview);
    }

    public static CapeBook.Choice resolve(UUID id, boolean local, Identifier vanillaCape, boolean preview) {
        if (local) {
            Minecraft client = Minecraft.getInstance();
            PlayerFacts.Fact fact = PlayerFacts.byUuid(id);
            // The synced customization byte starts at 0, so isModelPartShown(CAPE) is false
            // until the server applies client settings. Options are the local player's choice.
            boolean capeOn = client.options.isModelPartEnabled(PlayerModelPart.CAPE);
            if (!capeOn || (fact != null && fact.elytra())) {
                return CapeBook.none();
            }
            if (!preview && tryOn != null && client.player == tryOn.player()) return tryOn.choice();
//? if dev_catalog {
            /*if (LabyWear.showing()) {
                return LabyWear.choice();
            }
*///?}
            OwnerCape worn = OwnerCape.get();
            OwnerCape cape = preview && CapeBook.preview && CapeBook.draft != null ? CapeBook.draft : worn;
            TextureSlot slot = cape == worn ? LOCAL : PREVIEW;
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
