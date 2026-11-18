package dev.quarkos.capes.client.mixin;

import dev.quarkos.capes.client.screen.CapeScreens;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class CapePauseButtonMixin extends Screen {
    protected CapePauseButtonMixin(Component title) { super(title); }

    @Inject(method = "createPauseMenu()V", at = @At("RETURN"))
    private void quarkosPauseButton(CallbackInfo info) {
        if (minecraft.player == null) return;
        Component label = Component.translatable("text.quarkos_capes.title");
        int buttonWidth = Math.min(font.width(label) + 16, width / 3);
        for (int y : new int[]{8, height - 28}) {
            for (int x : new int[]{8, width - buttonWidth - 8}) {
                if (quarkosCornerFree(x, y, buttonWidth)) {
                    addRenderableWidget(Button.builder(label, button -> minecraft.setScreen(CapeScreens.open(this)))
                            .bounds(x, y, buttonWidth, 20).build());
                    return;
                }
            }
        }
    }

    @Unique
    private boolean quarkosCornerFree(int x, int y, int buttonWidth) {
        for (var child : children()) {
            if (child instanceof AbstractWidget widget && widget.visible
                    && x < widget.getX() + widget.getWidth() + 4 && x + buttonWidth + 4 > widget.getX()
                    && y < widget.getY() + widget.getHeight() + 4 && y + 24 > widget.getY()) return false;
        }
        return true;
    }
}
