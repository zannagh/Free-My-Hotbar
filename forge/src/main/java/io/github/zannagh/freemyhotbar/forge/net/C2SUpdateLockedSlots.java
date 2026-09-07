package io.github.zannagh.freemyhotbar.forge.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client-to-server packet carrying the player's 9-bit hotbar lock mask (bits 0-8). The server stores
 * it per-player so the common inventory mixin can honour the locked slots during item pickup.
 */
public final class C2SUpdateLockedSlots {

    /** Low-9-bit mask of locked hotbar slots. */
    public final int mask;

    public C2SUpdateLockedSlots(int mask) {
        this.mask = mask;
    }

    /**
     * Reads a packet from the network buffer.
     *
     * @param buf the incoming buffer.
     */
    public C2SUpdateLockedSlots(FriendlyByteBuf buf) {
        this(buf.readVarInt());
    }

    /**
     * Writes this packet to the network buffer.
     *
     * @param buf the outgoing buffer.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(mask);
    }
}
