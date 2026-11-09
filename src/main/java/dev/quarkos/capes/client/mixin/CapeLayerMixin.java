package dev.quarkos.capes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.quarkos.capes.client.McBridge;
import dev.quarkos.capes.client.render.CapeBook;
import dev.quarkos.capes.client.render.CapeDraw;
import dev.quarkos.capes.client.render.CapeLayerWatch;
import dev.quarkos.capes.client.render.PlayerFacts;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if <1.21.11 {
/*import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
*///?} else {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
//?}

@Mixin(CapeLayer.class)
public class CapeLayerMixin {
    //? if <1.21.11 {
    /*@Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void quarkosCapes(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partial, float age, float yaw, float pitch, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        CapeLayerWatch.textureOverride = null;
        if (dev.quarkos.capes.client.render.CapeCompatibility.otherRenderer()) return;
        boolean local = client.player == player;
        if (player.isInvisible() || player.isSpectator() || player.isSleeping()
                || player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(net.minecraft.world.item.Items.ELYTRA)
                || !(local ? client.options.isModelPartEnabled(PlayerModelPart.CAPE) : player.isModelPartShown(PlayerModelPart.CAPE))) {
            if (local) CapeLayerWatch.local(null);
            ci.cancel(); return;
        }
        Identifier vanilla = player.getSkin() == null ? null : player.getSkin().capeTexture();
        CapeBook.Choice choice = CapeDraw.resolve(player.getUUID(), local, vanilla);
        CapeLayerWatch.textureOverride = null;
        if (local) {
            CapeLayerWatch.local(choice.texture());
        }
        if (choice.kind() == CapeBook.Kind.NONE || choice.texture() == null) {
            return;
        }
        /^ A single flat frame uses vanilla animation and placement. ^/
        if (choice.kind() == CapeBook.Kind.QUAD && choice.frames() <= 1) {
            CapeBook.drawn(player.getUUID());
            CapeLayerWatch.textureOverride = choice.texture();
            return;
        }
        pose.pushPose();
        ((CapeLayer) (Object) this).getParentModel().body.translateAndRotate(pose);
        pose.translate(0.0F, 0.0F, player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty() ? 0.0F : 0.0625F);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(6.0F));
        var consumer = buffers.getBuffer(McBridge.cutout(choice.texture()));
        CapeDraw.emit(pose.last(), consumer, CapeBook.renderingPreview ? 15728880 : light, player.getUUID(), choice, CapeBook.renderingPreview);
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

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/PlayerSkin;capeTexture()Lnet/minecraft/resources/Identifier;"))
    private Identifier quarkosCapeTexture(net.minecraft.client.resources.PlayerSkin skin) {
        Identifier ours = CapeLayerWatch.textureOverride;
        return ours != null ? ours : skin.capeTexture();
    }
    *///?} else {
    @org.spongepowered.asm.mixin.Shadow
    private boolean hasLayer(net.minecraft.world.item.ItemStack stack, net.minecraft.client.resources.model.EquipmentClientInfo.LayerType type) {
        throw new AssertionError();
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void quarkosCapes(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (dev.quarkos.capes.client.render.CapeCompatibility.otherRenderer()) return;
        boolean local = client.player != null && client.player.getId() == state.id;
        if (state.isInvisible || state.isSpectator || state.hasPose(net.minecraft.world.entity.Pose.SLEEPING)
                || hasLayer(state.chestEquipment, net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.WINGS)
                || !(local ? client.options.isModelPartEnabled(PlayerModelPart.CAPE) : state.showCape)) {
            if (local) CapeLayerWatch.local(null);
            ci.cancel(); return;
        }
        PlayerFacts.Fact fact = PlayerFacts.byEntity(state.id);
        java.util.UUID id = fact != null ? fact.id() : (local ? client.player.getUUID() : null);
        if (id == null) {
            if (local) {
                CapeLayerWatch.local(null);
            }
            return;
        }
        Identifier vanilla = state.skin != null && state.skin.cape() != null ? state.skin.cape().texturePath() : null;
        CapeBook.Choice choice = CapeDraw.resolve(id, local, vanilla);
        if (local) {
            CapeLayerWatch.local(choice.texture());
        }
        if (choice.kind() == CapeBook.Kind.NONE || choice.texture() == null) {
            return;
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
            CapeBook.drawn(id);
            return;
        }
        boolean preview = state == CapeBook.previewState;
        int bright = preview ? 15728880 : light;
        java.util.UUID drawn = id;
        pose.pushPose();
        if (hasLayer(state.chestEquipment, net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.HUMANOID))
            pose.translate(0.0F, -0.053125F, 0.06875F);
        ((CapeLayer) (Object) this).getParentModel().body.translateAndRotate(pose);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(6.0F));
        collector.submitCustomGeometry(pose, McBridge.cutout(choice.texture()), (entry, consumer) ->
                CapeDraw.emit(entry, consumer, bright, drawn, choice, preview));
        pose.popPose();
        ci.cancel();
    }
    //?}
}
