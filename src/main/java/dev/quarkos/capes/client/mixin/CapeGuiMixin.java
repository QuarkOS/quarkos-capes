package dev.quarkos.capes.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.quarkos.capes.client.render.CapeRenderPass;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21.11 {
/*import net.minecraft.client.gui.screens.inventory.InventoryScreen;

@Mixin(InventoryScreen.class)
*///?} else {
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.gui.render.state.pip.GuiEntityRenderState;

@Mixin(GuiEntityRenderer.class)
//?}
public class CapeGuiMixin {
    //? if <1.21.11 {
    /*@org.spongepowered.asm.mixin.injection.Redirect(method = "renderEntityInInventory", at = @org.spongepowered.asm.mixin.injection.At(
            value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;runAsFancy(Ljava/lang/Runnable;)V"))
    private static void quarkosGui(Runnable render) {
        CapeRenderPass.guiDepth++;
        try { com.mojang.blaze3d.systems.RenderSystem.runAsFancy(render); }
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
