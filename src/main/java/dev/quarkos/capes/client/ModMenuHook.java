//? if fabric {
package dev.quarkos.capes.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.quarkos.capes.client.screen.CapeScreen;

// Optional. Mod Menu is not required to open the same Cloth screen with O.
public final class ModMenuHook implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return CapeScreen::create;
    }
}
//?}
