package io.github.zannagh.freemyhotbar.fabric;

import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import io.github.zannagh.freemyhotbar.slot.SlotLockState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class FreeMyHotbarFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        FreeMyHotbar.init();
        registerNetworking();
    }

    private void registerNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(
                FreeMyHotbarChannels.LOCKED_SLOTS,
                (server, player, handler, buf, responseSender) -> {
                    int mask = buf.readVarInt();
                    server.execute(() -> SlotLockState.set(player.getUUID(), mask));
                });

        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> SlotLockState.remove(handler.getPlayer().getUUID()));
    }
}
