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

    /**
     * The largest slot list this mod ever sends, and therefore the largest one a server accepts.
     *
     * <p>One entry per hotbar slot is all a legitimate client can have to say. The hand-rolled
     * decoder this format replaced enforced its own cardinality bound while reading the wire;
     * gzip(JSON) has none, so the bound has to be re-applied on the decoded payload before anything
     * walks it (see {@code FmhPackets}).
     */
    public static final int MAX_SLOTS = SlotBlock.HOTBAR_SLOT_COUNT;

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

    /**
     * Returns whether this payload's cardinality is plausible for a real client.
     *
     * @return true when it carries at most {@link #MAX_SLOTS} entries.
     */
    public boolean withinBounds() {
        return slots().size() <= MAX_SLOTS;
    }
}
