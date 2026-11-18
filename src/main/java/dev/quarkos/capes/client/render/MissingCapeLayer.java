package dev.quarkos.capes.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.mixin.LivingEntityRendererAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;
//? if <1.21.11 {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?} else {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
//?}
//? if <1.21.2 {
/*import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
*///?} else if <1.21.11 {
/*import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
*///?}

/**
 * Draws the selected cape when another mod has taken the vanilla cape layer off the renderer.
 * While that layer is still there, {@link dev.quarkos.capes.client.mixin.CapeLayerMixin} owns the draw.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class MissingCapeLayer extends RenderLayer {
    private final LivingEntityRenderer owner;

    public MissingCapeLayer(LivingEntityRenderer owner) {
        super(owner);
        this.owner = owner;
    }

    private boolean vanillaCapePresent() {
        for (Object layer : ((LivingEntityRendererAccess) owner).quarkos$layers()) {
            if (layer instanceof CapeLayer) {
                return true;
            }
        }
        return false;
    }

    //? if <1.21.2 {
    /*@Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, Entity entity, float limbSwing, float limbSwingAmount, float partial, float age, float yaw, float pitch) {
        if (vanillaCapePresent() || !(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        if (player.isInvisible() || player.isSpectator()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        boolean local = client.player == player;
        //? if <1.21 {
        Identifier vanilla = player.getCloakTextureLocation();
        //?} else {
        Identifier vanilla = player.getSkin() == null ? null : player.getSkin().capeTexture();
        //?}
        CapeBook.Choice choice = CapeDraw.resolve(player.getUUID(), local, vanilla);
        if (local) {
            CapeLayerWatch.local(choice.texture());
        }
        if (choice.kind() == CapeBook.Kind.NONE || choice.texture() == null) {
            return;
        }
        pose.pushPose();
        pose.translate(0.0F, player.isCrouching() ? 0.2F : 0.0F,
                player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty() ? 0.0F : 0.0625F);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(player.isCrouching() ? 31.0F : 6.0F));
        boolean preview = CapeRenderPass.guiDepth > 0 || CapeBook.renderingPreview;
        var consumer = buffers.getBuffer(McBridge.cutout(choice.texture(), choice.kind()));
        CapeDraw.emit(pose.last(), consumer, preview ? 15728880 : light, player.getUUID(), choice, preview);
        pose.popPose();
    }
    *///?} else if <1.21.11 {
    /*@Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, EntityRenderState state, float yRot, float xRot) {
        if (vanillaCapePresent() || !(state instanceof PlayerRenderState player)) {
            return;
        }
        if (player.isInvisible || player.isSpectator) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        boolean local = client.player != null && client.player.getId() == player.id;
        PlayerFacts.Fact fact = PlayerFacts.byEntity(player.id);
        java.util.UUID id = fact != null ? fact.id() : (local ? client.player.getUUID() : null);
        if (id == null) {
            if (local) {
                CapeLayerWatch.local(null);
            }
            return;
        }
        Identifier vanilla = player.skin == null ? null : player.skin.capeTexture();
        CapeBook.Choice choice = CapeDraw.resolve(id, local, vanilla);
        if (local) {
            CapeLayerWatch.local(choice.texture());
        }
        if (choice.kind() == CapeBook.Kind.NONE || choice.texture() == null) {
            return;
        }
        boolean preview = CapeRenderPass.guiDepth > 0 || CapeBook.renderingPreview;
        int bright = preview ? 15728880 : light;
        pose.pushPose();
        pose.translate(0.0F, player.isCrouching ? 0.2F : 0.0F, player.chestEquipment.isEmpty() ? 0.0F : 0.0625F);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(player.isCrouching ? 31.0F : 6.0F));
        var consumer = buffers.getBuffer(McBridge.cutout(choice.texture(), choice.kind()));
        CapeDraw.emit(pose.last(), consumer, bright, id, choice, preview);
        pose.popPose();
    }
    *///?} else {
    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, EntityRenderState state, float yRot, float xRot) {
        if (vanillaCapePresent() || !(state instanceof AvatarRenderState avatar)) {
            return;
        }
        if (avatar.isInvisible || avatar.isSpectator) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        boolean local = client.player != null && client.player.getId() == avatar.id;
        PlayerFacts.Fact fact = PlayerFacts.byEntity(avatar.id);
        java.util.UUID id = fact != null ? fact.id() : (local ? client.player.getUUID() : null);
        if (id == null) {
            if (local) {
                CapeLayerWatch.local(null);
            }
            return;
        }
        Identifier vanilla = avatar.skin != null && avatar.skin.cape() != null ? avatar.skin.cape().texturePath() : null;
        CapeBook.Choice choice = CapeDraw.resolve(id, local, vanilla);
        if (local) {
            CapeLayerWatch.local(choice.texture());
        }
        if (choice.kind() == CapeBook.Kind.NONE || choice.texture() == null) {
            return;
        }
        boolean preview = CapeRenderPass.guiDepth > 0 || avatar == CapeBook.previewState;
        int bright = preview ? 15728880 : light;
        java.util.UUID drawn = id;
        pose.pushPose();
        pose.translate(0.0F, avatar.isCrouching ? 0.2F : 0.0F, avatar.chestEquipment.isEmpty() ? 0.0F : 0.0625F);
        //? if >=26.1 {
        /*pose.rotateDegrees(com.mojang.math.Axis.XP, avatar.isCrouching ? 31.0F : 6.0F);
        *///?} else {
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(avatar.isCrouching ? 31.0F : 6.0F));
        //?}
        collector.submitCustomGeometry(pose, McBridge.cutout(choice.texture(), choice.kind()), (entry, consumer) ->
                CapeDraw.emit(entry, consumer, bright, drawn, choice, preview));
        pose.popPose();
    }
    //?}
}
