package io.github.zannagh.freemyhotbar.fallback;

import java.util.Arrays;

/**
 * Per-connection bookkeeping for the client-side eviction of locked hotbar slots: how long a
 * pickup stays interesting, how soon after a click the next one may follow, how often the same
 * unchanged stack has already been clicked, and how often a slot's contents have been thrown on the
 * ground (which, unlike a quick-move, the player can simply pick back up).
 *
 * <p>Minecraft-free on purpose so the whole state machine can be unit-tested. Instances are used
 * from the client thread only and are not synchronized.
 */
public final class EvictionTracker {

    /**
     * How long a pickup keeps the evictor armed, in client ticks. Generous because the movement
     * gate can keep the queue closed for a while - a sprinting player evicts nothing.
     */
    public static final int PICKUP_WINDOW_TICKS = 200;

    /**
     * Ticks to wait after a flush before clicking again, so the server's slot broadcast has landed
     * and the next pass reads the real state rather than the pre-click one.
     */
    public static final int FLUSH_COOLDOWN_TICKS = 5;

    /** Signature value used for an empty slot; no real stack can produce it. */
    private static final int EMPTY_SIGNATURE = 0;

    private final int[] attempts;
    private final int[] signatures;
    private final int[] dropSignatures;
    private final int[] dropCooldowns;
    private final int[] dropCounts;

    private int pickupTicks;
    private int cooldownTicks;

    /**
     * Creates a tracker for a fixed number of hotbar slots.
     *
     * @param slotCount the number of slots to track; negative values are clamped to zero.
     */
    public EvictionTracker(int slotCount) {
        int size = Math.max(0, slotCount);
        attempts = new int[size];
        signatures = new int[size];
        dropSignatures = new int[size];
        dropCooldowns = new int[size];
        dropCounts = new int[size];
    }

    /** Arms the evictor because the local player just picked something up. */
    public void notePickup() {
        pickupTicks = PICKUP_WINDOW_TICKS;
    }

    /** Advances the pickup window, the flush cooldown and every per-slot drop cooldown. */
    public void tick() {
        if (pickupTicks > 0) {
            pickupTicks--;
        }
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
        for (int slot = 0; slot < dropCooldowns.length; slot++) {
            if (dropCooldowns[slot] > 0) {
                dropCooldowns[slot]--;
            }
        }
    }

    /**
     * Returns whether a recent pickup still makes eviction worth attempting.
     *
     * @return true while the pickup window is open.
     */
    public boolean pending() {
        return pickupTicks > 0;
    }

    /**
     * Returns whether a flush may run right now.
     *
     * @return true when the pickup window is open and the post-click cooldown has elapsed.
     */
    public boolean ready() {
        return pending() && cooldownTicks == 0;
    }

    /** Records that a flush just ran and starts the cooldown. */
    public void noteFlush() {
        cooldownTicks = FLUSH_COOLDOWN_TICKS;
    }

    /** Closes the pickup window, e.g. once every locked slot is empty again. */
    public void clearPending() {
        pickupTicks = 0;
    }

    /**
     * Returns whether the given slot may be clicked again, resetting the attempt count first when
     * the slot's content changed since the last attempt.
     *
     * @param slot the hotbar slot index.
     * @param signature a value identifying the current stack (item plus count).
     * @return true when another click is allowed.
     */
    public boolean mayAttempt(int slot, int signature) {
        if (!inRange(slot)) {
            return false;
        }
        if (signatures[slot] != signature) {
            signatures[slot] = signature;
            attempts[slot] = 0;
        }
        return attempts[slot] < EvictionPolicy.MAX_ATTEMPTS_PER_SLOT;
    }

    /**
     * Counts one click against the slot's retry budget.
     *
     * @param slot the hotbar slot index.
     */
    public void recordAttempt(int slot) {
        if (inRange(slot)) {
            attempts[slot]++;
        }
    }

    /**
     * Clears the retry budget of a slot that is empty again. The drop budget deliberately survives:
     * a thrown stack empties the slot, and forgetting that here is what would re-open the
     * drop/pickup loop the drop budget exists to close.
     *
     * @param slot the hotbar slot index.
     */
    public void noteEmpty(int slot) {
        if (inRange(slot)) {
            attempts[slot] = 0;
            signatures[slot] = EMPTY_SIGNATURE;
        }
    }

    /**
     * Returns whether the given stack may be thrown out of the given slot right now.
     *
     * <p>Two independent bounds, both needed because a thrown stack comes back: the same signature
     * may not be dropped from the same slot again until {@link EvictionPolicy#DROP_REPEAT_COOLDOWN_TICKS}
     * has elapsed, and a slot may only be dropped from
     * {@link EvictionPolicy#MAX_DROPS_PER_SLOT} times per connection at all.
     *
     * @param slot the hotbar slot index.
     * @param signature a value identifying the current stack (item plus count).
     * @return true when throwing the stack is allowed.
     */
    public boolean mayDrop(int slot, int signature) {
        if (!inRange(slot)) {
            return false;
        }
        if (dropCounts[slot] >= EvictionPolicy.MAX_DROPS_PER_SLOT) {
            return false;
        }
        return dropSignatures[slot] != signature || dropCooldowns[slot] == 0;
    }

    /**
     * Counts one throw against the slot's drop budget and arms its repeat cooldown.
     *
     * @param slot the hotbar slot index.
     * @param signature a value identifying the stack being thrown.
     */
    public void recordDrop(int slot, int signature) {
        if (!inRange(slot)) {
            return;
        }
        dropSignatures[slot] = signature;
        dropCooldowns[slot] = EvictionPolicy.DROP_REPEAT_COOLDOWN_TICKS;
        dropCounts[slot]++;
    }

    /**
     * Returns whether the slot has used up its per-connection drop budget for good.
     *
     * @param slot the hotbar slot index.
     * @return true when no further throw will ever be attempted for this slot on this connection.
     */
    public boolean dropsExhausted(int slot) {
        return inRange(slot) && dropCounts[slot] >= EvictionPolicy.MAX_DROPS_PER_SLOT;
    }

    /** Drops all state; call when the player or the connection goes away. */
    public void reset() {
        pickupTicks = 0;
        cooldownTicks = 0;
        Arrays.fill(attempts, 0);
        Arrays.fill(signatures, EMPTY_SIGNATURE);
        Arrays.fill(dropSignatures, EMPTY_SIGNATURE);
        Arrays.fill(dropCooldowns, 0);
        Arrays.fill(dropCounts, 0);
    }

    private boolean inRange(int slot) {
        return slot >= 0 && slot < attempts.length;
    }
}
