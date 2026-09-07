package io.github.zannagh.freemyhotbar.forge.net;

import io.github.zannagh.freemyhotbar.slot.SlotLockState;
import net.minecraft.resources.ResourceLocation;
import java.util.function.Supplier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Forge networking for Free My Hotbar. Builds an OPTIONAL {@link SimpleChannel} so a server without
 * the mod does not reject a client that has it, and registers the single C2S mask-update packet.
 * The channel and packet live in the common (both-sides) source set; the client-only send is driven
 * from the client glue.
 */
public final class ForgeNetworking {

    /** Forge mod id / channel namespace (underscored form; the resource id uses dashes elsewhere). */
    public static final String MOD_ID = "free_my_hotbar";

    private static final String PROTOCOL_VERSION = "1";

    private static final int MASK_BITS = 0x1FF;

    /**
     * The optional simple channel. {@link NetworkRegistry#acceptMissingOr(String)} makes both sides
     * tolerate the channel being absent on the remote, so the mod stays install-optional.
     */
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION))
            .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION))
            .simpleChannel();

    private ForgeNetworking() {
    }

    /** Registers the mod's packets on the channel. Call once during mod construction. */
    public static void register() {
        CHANNEL.messageBuilder(C2SUpdateLockedSlots.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((msg, buf) -> msg.encode(buf))
                .decoder(C2SUpdateLockedSlots::new)
                .consumerMainThread(ForgeNetworking::handleUpdateLockedSlots)
                .add();
    }

    /**
     * Sends the player's current lock mask to the server. Safe on modless servers: the optional
     * channel keeps the connection valid and a server lacking the channel simply ignores the payload.
     *
     * @param mask the 9-bit hotbar lock mask.
     */
    public static void sendMask(int mask) {
        CHANNEL.send(PacketDistributor.SERVER.noArg(), new C2SUpdateLockedSlots(mask & MASK_BITS));
    }

    private static void handleUpdateLockedSlots(
            C2SUpdateLockedSlots msg,
            Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ServerPlayer sender = ctx.getSender();
        if (sender != null) {
            SlotLockState.set(sender.getUUID(), msg.mask & MASK_BITS);
        }
        ctx.setPacketHandled(true);
    }
}
