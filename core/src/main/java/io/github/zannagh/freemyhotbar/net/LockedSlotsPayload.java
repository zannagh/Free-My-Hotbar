package io.github.zannagh.freemyhotbar.net;

import java.util.List;

import io.github.zannagh.freemyhotbar.slot.SlotBlock;

/**
 * Wire payload carrying the client's hotbar slots and their blocked state to the server.
 *
 * <p>A wrapper class rather than a bare {@code List<SlotBlock>}: eunomia's {@code PayloadCodec}
 * serializes the payload as gzip(JSON) of the declared payload class, and a named object with one
 * {@code slots} field leaves room for additional fields later without breaking the format.
 *
 * <p>Deliberately NOT a record: Gson instantiates the payload reflectively (no no-arg constructor
 * is required, it allocates the object and writes the fields), so {@link #slots()} must tolerate a
 * {@code null} field from a payload whose JSON omitted it. The {@link SlotBlock} elements ARE
 * records and round-trip fine — Gson has had record support since 2.10, the version Minecraft
 * 1.20.1 ships (see {@code FmhPacketsTest} in {@code :smoke}, which proves the round-trip through
 * the real codec).
 */
public final class LockedSlotsPayload {

    private final List<SlotBlock> slots;

    /**
     * Creates a payload from the given slots.
     *
     * @param slots the slots with their blocked state; null is treated as empty.
     */
    public LockedSlotsPayload(List<SlotBlock> slots) {
        this.slots = slots != null ? List.copyOf(slots) : List.of();
    }

    /**
     * Returns the slots carried by this payload.
     *
     * @return the slots, never null — an empty list when the decoded payload had none.
     */
    public List<SlotBlock> slots() {
        return slots != null ? slots : List.of();
    }
}
