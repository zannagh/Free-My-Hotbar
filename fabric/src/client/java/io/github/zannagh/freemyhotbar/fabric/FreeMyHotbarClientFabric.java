package io.github.zannagh.freemyhotbar.fabric;

import java.util.List;

import io.github.zannagh.freemyhotbar.client.FreeMyHotbarClient;
import io.github.zannagh.freemyhotbar.client.FreeMyHotbarKeys;
import io.github.zannagh.freemyhotbar.slot.SlotBlock;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

public class FreeMyHotbarClientFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FreeMyHotbarClient.init();

        KeyBindingHelper.registerKeyBinding(FreeMyHotbarKeys.OPEN_SCREEN);
        ClientTickEvents.END_CLIENT_TICK.register(FreeMyHotbarKeys::handleTick);

        FreeMyHotbarClient.setSyncSender(FreeMyHotbarClientFabric::sendBlocks);

        // Resend the current slots on join so a modded server learns them after connect.
        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> sendBlocks(FreeMyHotbarClient.config().slots()));
    }

    /**
     * Sends the given slots to the server, but only when the server has registered the channel
     * (graceful no-op on vanilla or mod-less servers).
     *
     * @param slots the slots with their blocked state.
     */
    private static void sendBlocks(List<SlotBlock> slots) {
        if (!ClientPlayNetworking.canSend(FreeMyHotbarChannels.LOCKED_SLOTS)) {
            return;
        }
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(slots.size());
        for (SlotBlock slot : slots) {
            buf.writeVarInt(slot.slotId());
            buf.writeBoolean(slot.blocked());
        }
        ClientPlayNetworking.send(FreeMyHotbarChannels.LOCKED_SLOTS, buf);
    }
}
