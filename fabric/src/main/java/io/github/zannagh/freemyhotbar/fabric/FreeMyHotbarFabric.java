package io.github.zannagh.freemyhotbar.fabric;

import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import net.fabricmc.api.ModInitializer;

public class FreeMyHotbarFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        FreeMyHotbar.init();
    }
}
