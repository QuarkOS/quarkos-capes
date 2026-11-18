package dev.quarkos.capes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.quarkos.capes.client.render.CapeRenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
//? if <1.21.6 {
/*import net.minecraft.client.gui.screens.inventory.InventoryScreen;

@Mixin(InventoryScreen.class)
*///?} else {
//? if >=1.21.11 {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
//?}
//? if >=26.1 {
/*import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

@Mixin(PictureInPictureRenderer.class)
*///?} else {
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.gui.render.state.pip.GuiEntityRenderState;
@Mixin(GuiEntityRenderer.class)
//?}
//?}
public class CapeGuiMixin {
    //? if <1.21.2 {
    /*@org.spongepowered.asm.mixin.injection.Redirect(method = "renderEntityInInventory", require = 0, at = @org.spongepowered.asm.mixin.injection.At(
            value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;runAsFancy(Ljava/lang/Runnable;)V"))
    private static void quarkosGui(Runnable render) {
        CapeRenderPass.guiDepth++;
        try { com.mojang.blaze3d.systems.RenderSystem.runAsFancy(render); }
        finally { CapeRenderPass.guiDepth--; }
    }
    *///?} else if <1.21.6 {
    /*@org.spongepowered.asm.mixin.injection.Inject(method = "renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;FFFLorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private static void quarkosGuiBegin(net.minecraft.client.gui.GuiGraphics graphics, float x, float y, float size, org.joml.Vector3f translation, org.joml.Quaternionf pose, org.joml.Quaternionf camera, net.minecraft.world.entity.LivingEntity entity, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        CapeRenderPass.guiDepth++;
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "renderEntityInInventory(Lnet/minecraft/client/gui/GuiGraphics;FFFLorg/joml/Vector3f;Lorg/joml/Quaternionf;Lorg/joml/Quaternionf;Lnet/minecraft/world/entity/LivingEntity;)V", at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
    private static void quarkosGuiEnd(net.minecraft.client.gui.GuiGraphics graphics, float x, float y, float size, org.joml.Vector3f translation, org.joml.Quaternionf pose, org.joml.Quaternionf camera, net.minecraft.world.entity.LivingEntity entity, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        CapeRenderPass.guiDepth--;
    }
    *///?} else if <1.21.11 {
    /*@Inject(method = "renderToTexture(Lnet/minecraft/client/gui/render/state/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
    private void quarkosGuiBegin(GuiEntityRenderState state, PoseStack pose, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        CapeRenderPass.guiDepth++;
    }

    @Inject(method = "renderToTexture(Lnet/minecraft/client/gui/render/state/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
    private void quarkosGuiEnd(GuiEntityRenderState state, PoseStack pose, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        CapeRenderPass.guiDepth--;
    }
    *///?} else if >=26.1 {
    /*// The feature buffers are built after GuiEntityRenderer.renderToTexture returns.
    @WrapMethod(method = "prepare(Lnet/minecraft/client/renderer/state/gui/pip/PictureInPictureRenderState;Lnet/minecraft/client/renderer/state/gui/GuiRenderState;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;I)V")
    private void quarkosGui(PictureInPictureRenderState state, GuiRenderState gui,
                            FeatureRenderDispatcher features, int scale, Operation<Void> original) {
        if (!(state instanceof GuiEntityRenderState)) {
            original.call(state, gui, features, scale);
            return;
        }
        CapeRenderPass.guiDepth++;
        try { original.call(state, gui, features, scale); }
        finally { CapeRenderPass.guiDepth--; }
    }
    *///?} else {
    @WrapMethod(method = "renderToTexture(Lnet/minecraft/client/gui/render/state/pip/GuiEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V")
    private void quarkosGui(GuiEntityRenderState state, PoseStack pose, Operation<Void> original) {
        CapeRenderPass.guiDepth++;
        try { original.call(state, pose); }
        finally { CapeRenderPass.guiDepth--; }
    }
    //?}
}
