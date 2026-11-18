package dev.quarkos.capes.client.mixin;

import dev.quarkos.capes.client.screen.CapeScreens;
import dev.quarkos.capes.client.screen.ui.CapeGridLayout.Rect;
import dev.quarkos.capes.client.screen.ui.CapeTheme;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class CapeTitleButtonMixin extends Screen {
    protected CapeTitleButtonMixin(Component title) { super(title); }

    @Inject(method = "init()V", at = @At("RETURN"))
    private void quarkosCapesButton(CallbackInfo info) {
        var area = new Rect(8, 8, Math.min(108, width / 3), CapeTheme.CONTROL);
        addRenderableWidget(CapeTheme.button(CapeTheme.text("title"), area, CapeTheme.Face.BUTTON,
                () -> minecraft.setScreen(CapeScreens.open(this))));
    }
}
