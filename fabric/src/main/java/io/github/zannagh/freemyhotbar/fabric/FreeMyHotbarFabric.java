package io.github.zannagh.freemyhotbar.fabric;

import java.util.ArrayList;
import java.util.List;

import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import io.github.zannagh.freemyhotbar.slot.SlotBlock;
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
                    int count = buf.readVarInt();
                    if (count < 0 || count > 64) {
                        return;
                    }
                    List<SlotBlock> slots = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        int slotId = buf.readVarInt();
                        boolean blocked = buf.readBoolean();
                        if (slotId >= 0 && slotId < SlotBlock.HOTBAR_SLOT_COUNT) {
                            slots.add(new SlotBlock(slotId, blocked));
                        }
                    }
                    server.execute(() -> SlotLockState.set(player.getUUID(), slots));
                });

        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> SlotLockState.remove(handler.getPlayer().getUUID()));
    }
}
