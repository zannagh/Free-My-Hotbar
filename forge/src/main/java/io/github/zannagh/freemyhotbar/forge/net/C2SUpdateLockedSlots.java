package io.github.zannagh.freemyhotbar.forge.net;

import java.util.ArrayList;
import java.util.List;

import io.github.zannagh.freemyhotbar.slot.SlotBlock;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Client-to-server packet carrying the player's slots and their blocked state. The server stores
 * them per-player so the common inventory mixin can honour the blocked slots during item pickup.
 */
public final class C2SUpdateLockedSlots {

    /** The slots and whether each is blocked. */
    public final List<SlotBlock> slots;

    public C2SUpdateLockedSlots(List<SlotBlock> slots) {
        this.slots = slots;
    }

    /**
     * Reads a packet from the network buffer.
     *
     * @param buf the incoming buffer.
     */
    public C2SUpdateLockedSlots(FriendlyByteBuf buf) {
        this(readSlots(buf));
    }

    private static List<SlotBlock> readSlots(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > 64) {
            return new ArrayList<>();
        }
        List<SlotBlock> slots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int slotId = buf.readVarInt();
            boolean blocked = buf.readBoolean();
            if (slotId >= 0 && slotId < SlotBlock.HOTBAR_SLOT_COUNT) {
                slots.add(new SlotBlock(slotId, blocked));
            }
        }
        return slots;
    }

    /**
     * Writes this packet to the network buffer.
     *
     * @param buf the outgoing buffer.
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(slots.size());
        for (SlotBlock slot : slots) {
            buf.writeVarInt(slot.slotId());
            buf.writeBoolean(slot.blocked());
        }
    }
}
