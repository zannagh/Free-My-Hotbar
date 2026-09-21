package io.github.zannagh.freemyhotbar.fabric;

import java.util.List;

import io.github.zannagh.freemyhotbar.client.FreeMyHotbarClient;
import io.github.zannagh.freemyhotbar.client.FreeMyHotbarKeys;
import io.github.zannagh.freemyhotbar.client.HotbarEvictor;
import io.github.zannagh.freemyhotbar.client.ServerModNotice;
import io.github.zannagh.freemyhotbar.client.ServerModPresence;
import io.github.zannagh.freemyhotbar.slot.SlotBlock;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

public class FreeMyHotbarClientFabric implements ClientModInitializer {

    /**
     * Ticks to wait before declaring the server mod-less. The server's {@code minecraft:register}
     * packet can arrive slightly after JOIN, so latching ABSENT immediately would race the
     * handshake.
     */
    private static final int PRESENCE_GRACE_TICKS = 60;

    /**
     * Volatile because it is written from two threads. Fabric fires
     * {@code C2SPlayChannelEvents.REGISTER} from {@code ClientPlayNetworkAddon}'s
     * {@code onCustomPayload} handling, which runs on the netty I/O thread (the vanilla
     * {@code ensureRunningOnSameThread} hand-off happens later), while {@link #tickPresenceGrace()}
     * reads and writes it on the client thread.
     */
    private static volatile int graceTicks;

    @Override
    public void onInitializeClient() {
        FreeMyHotbarClient.init();

        KeyBindingHelper.registerKeyBinding(FreeMyHotbarKeys.OPEN_SCREEN);
        ClientTickEvents.END_CLIENT_TICK.register(FreeMyHotbarClientFabric::onClientTick);

        FreeMyHotbarClient.setSyncSender(FreeMyHotbarClientFabric::sendBlocks);

        registerPresenceEvents();

        // Resend the current slots on join so a modded server learns them after connect.
        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> sendBlocks(FreeMyHotbarClient.config().slots()));
    }

    /** Resolves and maintains {@link ServerModPresence} for the lifetime of a connection. */
    private static void registerPresenceEvents() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            graceTicks = 0;
            ServerModPresence.reset();
            ServerModNotice.reset();
            HotbarEvictor.reset();
        });

        // The registration can arrive after JOIN, and a server may drop the channel later on.
        // C2SPlayChannelEvents is the event pair to use HERE: on a client it fires when the SERVER
        // announces which channels it can receive, i.e. which channels this client may send on.
        // S2CPlayChannelEvents is a different class that exists in the same artifact - it is the
        // mirrored, server-side view - not a later rename of this one. Swapping them silently
        // registers a listener that never fires on a client.
        C2SPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> {
            if (channels.contains(FreeMyHotbarChannels.LOCKED_SLOTS)) {
                graceTicks = 0;
                ServerModPresence.set(ServerModPresence.State.PRESENT);
                // The JOIN send was a no-op while the channel was still unknown; catch up now.
                sendBlocks(FreeMyHotbarClient.config().slots());
            }
        });
        C2SPlayChannelEvents.UNREGISTER.register((handler, sender, client, channels) -> {
            if (!channels.contains(FreeMyHotbarChannels.LOCKED_SLOTS)) {
                return;
            }
            // Same singleplayer short-circuit as onJoin: the integrated server runs the mixin, so
            // the fallback must stay off even if the channel goes away mid-session.
            if (client != null && client.hasSingleplayerServer()) {
                return;
            }
            ServerModPresence.set(ServerModPresence.State.ABSENT);
        });
    }

    private static void onJoin(Minecraft client) {
        ServerModNotice.reset();
        // The integrated server runs the mixin, so the client-side fallback must stay off.
        if (client != null && client.hasSingleplayerServer()) {
            graceTicks = 0;
            ServerModPresence.set(ServerModPresence.State.PRESENT);
            return;
        }
        if (ClientPlayNetworking.canSend(FreeMyHotbarChannels.LOCKED_SLOTS)) {
            graceTicks = 0;
            ServerModPresence.set(ServerModPresence.State.PRESENT);
            return;
        }
        ServerModPresence.set(ServerModPresence.State.UNKNOWN);
        graceTicks = PRESENCE_GRACE_TICKS;
    }

    private static void onClientTick(Minecraft client) {
        tickPresenceGrace();
        FreeMyHotbarKeys.handleTick(client);
        ServerModNotice.tick(client);
        HotbarEvictor.tick(client);
    }

    /** Latches ABSENT once the grace period elapses without the channel showing up. */
    private static void tickPresenceGrace() {
        if (graceTicks <= 0) {
            return;
        }
        graceTicks--;
        if (graceTicks > 0) {
            return;
        }
        if (ClientPlayNetworking.canSend(FreeMyHotbarChannels.LOCKED_SLOTS)) {
            ServerModPresence.set(ServerModPresence.State.PRESENT);
        } else {
            ServerModPresence.set(ServerModPresence.State.ABSENT);
        }
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
