package io.github.zannagh.freemyhotbar.fabric;

import io.github.zannagh.freemyhotbar.client.FreeMyHotbarClient;
import io.github.zannagh.freemyhotbar.client.FreeMyHotbarKeys;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

/**
 * Fabric client glue. Only the two things Fabric alone can do live here — registering the keybind
 * and subscribing to the client tick. Config, slot syncing and the per-connection lifecycle are
 * loader-agnostic and handled by {@link FreeMyHotbarClient}.
 */
public class FreeMyHotbarClientFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FreeMyHotbarClient.init();

        KeyBindingHelper.registerKeyBinding(FreeMyHotbarKeys.OPEN_SCREEN);
        ClientTickEvents.END_CLIENT_TICK.register(FreeMyHotbarClient::clientTick);
    }
}
