package dev.quarkos.capes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.render.CapeDraw;
import dev.quarkos.capes.client.render.CapeLayerWatch;
import dev.quarkos.capes.client.render.CapeRenderPass;
import dev.quarkos.capes.client.render.PlayerFacts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <1.21.11 {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?} else {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
//?}
//? if <1.21.2 {
/*import net.minecraft.client.player.AbstractClientPlayer;
*///?} else if <1.21.11 {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.resources.PlayerSkin;
*///?}

@Mixin(CapeLayer.class)
public class CapeLayerMixin {
    //? if <1.21.2 {
    /*@Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void quarkosCapes(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partial, float age, float yaw, float pitch, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        CapeLayerWatch.textureOverride = null;
        boolean local = client.player == player;
        if (!CapeRenderPass.allowed(CapeRenderPass.guiDepth > 0 || CapeBook.renderingPreview,
                client.getCameraEntity() == player, client.options.getCameraType().isFirstPerson(), CapeRenderPass.shadow())) {
            ci.cancel(); return;
        }
        if (player.isInvisible() || player.isSpectator() || player.isSleeping()
                || player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.ELYTRA)
                || !(local ? client.options.isModelPartEnabled(PlayerModelPart.CAPE) : player.isModelPartShown(PlayerModelPart.CAPE))) {
            if (local) CapeLayerWatch.hidden();
            ci.cancel(); return;
        }
        //? if <1.21 {
        Identifier vanilla = player.getCloakTextureLocation();
        //?} else {
        Identifier vanilla = player.getSkin() == null ? null : player.getSkin().capeTexture();
        //?}
        CapeBook.Choice choice = CapeDraw.resolve(player.getUUID(), local, vanilla);
        if (choice.kind() == CapeBook.Kind.NONE || choice.texture() == null) {
            if (local) CapeLayerWatch.hidden();
            ci.cancel(); return;
        }
        if (choice.kind() == CapeBook.Kind.QUAD && choice.frames() <= 1) {
            CapeLayerWatch.textureOverride = choice.texture();
            return;
        }
        boolean preview = CapeRenderPass.guiDepth > 0 || CapeBook.renderingPreview;
        pose.pushPose();
        ((CapeLayer) (Object) this).getParentModel().body.translateAndRotate(pose);
        pose.translate(0.0F, 0.0F, player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty() ? 0.0F : 0.0625F);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(6.0F));
        var consumer = buffers.getBuffer(McBridge.cutout(choice.texture(), choice.kind()));
        CapeDraw.emit(pose.last(), consumer, preview ? 15728880 : light, player.getUUID(), choice, preview);
        pose.popPose();
        ci.cancel();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void quarkosCapesClear(CallbackInfo ci) {
        CapeLayerWatch.textureOverride = null;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isModelPartShown(Lnet/minecraft/world/entity/player/PlayerModelPart;)Z"))
    private boolean quarkosCapePart(AbstractClientPlayer player, PlayerModelPart part) {
        if (part == PlayerModelPart.CAPE && CapeLayerWatch.textureOverride != null) {
            return true;
        }
        return player.isModelPartShown(part);
    }

    //? if <1.21 {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;getCloakTextureLocation()Lnet/minecraft/resources/Identifier;"))
    private Identifier quarkosCapeTexture(AbstractClientPlayer player) {
        Identifier ours = CapeLayerWatch.textureOverride;
        return ours != null ? ours : player.getCloakTextureLocation();
    }
    //?} else {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/PlayerSkin;capeTexture()Lnet/minecraft/resources/Identifier;"))
    private Identifier quarkosCapeTexture(net.minecraft.client.resources.PlayerSkin skin) {
        Identifier ours = CapeLayerWatch.textureOverride;
        return ours != null ? ours : skin.capeTexture();
    }
    //?}
    *///?} else if <1.21.11 {
    /*@Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void quarkosCapes(PoseStack pose, MultiBufferSource buffers, int light, PlayerRenderState state, float yRot, float xRot, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        boolean local = client.player != null && client.player.getId() == state.id;
        boolean preview = CapeRenderPass.guiDepth > 0 || CapeBook.renderingPreview;
        if (!CapeRenderPass.allowed(CapeRenderPass.guiDepth > 0 || preview,
                client.getCameraEntity() != null && client.getCameraEntity().getId() == state.id,
                client.options.getCameraType().isFirstPerson(), CapeRenderPass.shadow())) {
            ci.cancel(); return;
        }
        if (state.isInvisible || state.isSpectator
                || state.chestEquipment.is(net.minecraft.world.item.Items.ELYTRA)
                || !(local ? client.options.isModelPartEnabled(PlayerModelPart.CAPE) : state.showCape)) {
            if (local) CapeLayerWatch.hidden();
            ci.cancel(); return;
        }
        PlayerFacts.Fact fact = PlayerFacts.byEntity(state.id);
        java.util.UUID id = fact != null ? fact.id() : (local ? client.player.getUUID() : null);
        if (id == null) {
            if (local) CapeLayerWatch.hidden();
            return;
        }
        Identifier vanilla = state.skin == null ? null : state.skin.capeTexture();
        CapeBook.Choice choice = CapeDraw.resolve(id, local, vanilla);
        if (choice.kind() == CapeBook.Kind.NONE || choice.texture() == null) {
            if (local) CapeLayerWatch.hidden();
            ci.cancel(); return;
        }
        if (choice.kind() == CapeBook.Kind.QUAD && choice.frames() <= 1 && state.skin != null) {
            state.showCape = true;
            Identifier cape = choice.texture();
            state.skin = new PlayerSkin(state.skin.texture(), state.skin.textureUrl(), cape, state.skin.elytraTexture(), state.skin.model(), state.skin.secure());
            return;
        }
        int bright = preview ? 15728880 : light;
        pose.pushPose();
        ((CapeLayer) (Object) this).getParentModel().body.translateAndRotate(pose);
        pose.translate(0.0F, 0.0F, state.chestEquipment.isEmpty() ? 0.0F : 0.0625F);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(6.0F));
        var consumer = buffers.getBuffer(McBridge.cutout(choice.texture(), choice.kind()));
        CapeDraw.emit(pose.last(), consumer, bright, id, choice, preview);
        pose.popPose();
        ci.cancel();
    }
    *///?} else {
    @org.spongepowered.asm.mixin.Shadow
    private boolean hasLayer(net.minecraft.world.item.ItemStack stack, net.minecraft.client.resources.model.EquipmentClientInfo.LayerType type) {
        throw new AssertionError();
    }

    //? if >=26.1 {
    /*@Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    *///?} else {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    //?}
    private void quarkosCapes(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        boolean local = client.player != null && client.player.getId() == state.id;
        boolean preview = CapeRenderPass.guiDepth > 0 || state == CapeBook.previewState;
        if (!CapeRenderPass.allowed(CapeRenderPass.guiDepth > 0 || preview,
                client.getCameraEntity() != null && client.getCameraEntity().getId() == state.id,
                client.options.getCameraType().isFirstPerson(), CapeRenderPass.shadow())) {
            ci.cancel(); return;
        }
        if (state.isInvisible || state.isSpectator || state.hasPose(net.minecraft.world.entity.Pose.SLEEPING)
                || hasLayer(state.chestEquipment, net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.WINGS)
                || !(local ? client.options.isModelPartEnabled(PlayerModelPart.CAPE) : state.showCape)) {
            if (local) CapeLayerWatch.hidden();
            ci.cancel(); return;
        }
        PlayerFacts.Fact fact = PlayerFacts.byEntity(state.id);
        java.util.UUID id = fact != null ? fact.id() : (local ? client.player.getUUID() : null);
        if (id == null) {
            if (local) CapeLayerWatch.hidden();
            return;
        }
        Identifier vanilla = state.skin != null && state.skin.cape() != null ? state.skin.cape().texturePath() : null;
        CapeBook.Choice choice = CapeDraw.resolve(id, local, vanilla);
        if (choice.kind() == CapeBook.Kind.NONE || choice.texture() == null) {
            if (local) CapeLayerWatch.hidden();
            ci.cancel(); return;
        }
        if (choice.kind() == CapeBook.Kind.QUAD && choice.frames() <= 1 && state.skin != null) {
            state.showCape = true;
            Identifier cape = choice.texture();
            state.skin = new PlayerSkin(
                    state.skin.body(),
                    new ClientAsset.ResourceTexture(cape, cape),
                    state.skin.elytra(),
                    state.skin.model(),
                    state.skin.secure()
            );
            return;
        }
        int bright = preview ? 15728880 : light;
        java.util.UUID drawn = id;
        pose.pushPose();
        if (hasLayer(state.chestEquipment, net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.HUMANOID)) {
            pose.translate(0.0F, -0.053125F, 0.06875F);
        }
        ((CapeLayer) (Object) this).getParentModel().body.translateAndRotate(pose);
        //? if >=26.1 {
        /*pose.rotateDegrees(com.mojang.math.Axis.XP, 6.0F);
        *///?} else {
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(6.0F));
        //?}
        collector.submitCustomGeometry(pose, McBridge.cutout(choice.texture(), choice.kind()), (entry, consumer) ->
                CapeDraw.emit(entry, consumer, bright, drawn, choice, preview));
        pose.popPose();
        ci.cancel();
    }
    //?}
}
