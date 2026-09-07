package io.github.zannagh.freemyhotbar.fabric;

import io.github.zannagh.freemyhotbar.client.FreeMyHotbarClient;
import io.github.zannagh.freemyhotbar.client.FreeMyHotbarKeys;
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

        FreeMyHotbarClient.setSyncSender(FreeMyHotbarClientFabric::sendMask);

        // Resend the current mask on join so a modded server learns it after connect.
        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> sendMask(FreeMyHotbarClient.config().mask()));
    }

    /**
     * Sends the given lock mask to the server as a single varint, but only when the server
     * has registered the channel (graceful no-op on vanilla or mod-less servers).
     *
     * @param mask the 9-bit hotbar lock mask.
     */
    private static void sendMask(int mask) {
        if (!ClientPlayNetworking.canSend(FreeMyHotbarChannels.LOCKED_SLOTS)) {
            return;
        }
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(mask);
        ClientPlayNetworking.send(FreeMyHotbarChannels.LOCKED_SLOTS, buf);
    }
}
