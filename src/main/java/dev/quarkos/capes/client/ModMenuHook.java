//? if fabric {
package dev.quarkos.capes.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.quarkos.capes.client.screen.CapeScreens;

public final class ModMenuHook implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return CapeScreens::open;
    }
}
//?}
