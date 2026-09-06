package io.github.zannagh.freemyhotbar.fabric;

import io.github.zannagh.freemyhotbar.client.FreeMyHotbarClient;
import net.fabricmc.api.ClientModInitializer;

public class FreeMyHotbarClientFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FreeMyHotbarClient.init();
    }
}
