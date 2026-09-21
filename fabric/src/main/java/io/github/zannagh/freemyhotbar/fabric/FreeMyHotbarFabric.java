package io.github.zannagh.freemyhotbar.fabric;

import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import io.github.zannagh.freemyhotbar.FreeMyHotbarNetworking;
import net.fabricmc.api.ModInitializer;

public class FreeMyHotbarFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        FreeMyHotbar.init();
        // Runs on both physical sides, which is exactly where the locked-slots handler has to be
        // registered — see FreeMyHotbarNetworking.
        FreeMyHotbarNetworking.init();
    }
}
