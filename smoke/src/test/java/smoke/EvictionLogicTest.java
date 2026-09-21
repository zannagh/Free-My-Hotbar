package smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.zannagh.freemyhotbar.config.FallbackMode;
import io.github.zannagh.freemyhotbar.fallback.EvictionAction;
import io.github.zannagh.freemyhotbar.fallback.EvictionPolicy;
import io.github.zannagh.freemyhotbar.fallback.EvictionTracker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the Minecraft-free half of the client-side fallback: the movement gate, the mode
 * branching and the retry/cooldown bookkeeping that keeps a full inventory from turning into an
 * endless click loop.
 */
@DisplayName("Client fallback eviction logic")
class EvictionLogicTest {

    private static final int SLOTS = 9;

    @Test
    @DisplayName("the movement gate opens only when the player is completely still")
    void movementGateOnlyWhenStill() {
        assertTrue(EvictionPolicy.movementGateOpen(false, false, false));
        assertFalse(EvictionPolicy.movementGateOpen(true, false, false), "sprinting must close the gate");
        assertFalse(EvictionPolicy.movementGateOpen(false, true, false), "sneaking must close the gate");
        assertFalse(EvictionPolicy.movementGateOpen(false, false, true), "movement input must close the gate");
    }

    @Test
    @DisplayName("evictImmediately bypasses the movement gate and nothing else")
    void evictImmediatelyBypassesTheGate() {
        // Off (the default): the gate decides, exactly as before.
        assertTrue(EvictionPolicy.clickGateOpen(false, false, false, false));
        assertFalse(EvictionPolicy.clickGateOpen(false, true, false, false), "sprinting must still block");
        assertFalse(EvictionPolicy.clickGateOpen(false, false, true, false), "sneaking must still block");
        assertFalse(EvictionPolicy.clickGateOpen(false, false, false, true), "movement input must still block");

        // On: the gate is skipped whatever the player is doing.
        assertTrue(EvictionPolicy.clickGateOpen(true, true, true, true));
        assertTrue(EvictionPolicy.clickGateOpen(true, false, false, false));
    }

    @Test
    @DisplayName("OFF never acts, MOVE only relocates, MOVE_OR_DROP falls back to throwing")
    void modeBranching() {
        assertEquals(EvictionAction.NONE, EvictionPolicy.decide(FallbackMode.OFF, true));
        assertEquals(EvictionAction.NONE, EvictionPolicy.decide(null, true));

        assertEquals(EvictionAction.MOVE, EvictionPolicy.decide(FallbackMode.MOVE, true));
        assertEquals(EvictionAction.NONE, EvictionPolicy.decide(FallbackMode.MOVE, false),
                "MOVE must leave the item alone when there is nowhere to put it");

        assertEquals(EvictionAction.MOVE, EvictionPolicy.decide(FallbackMode.MOVE_OR_DROP, true),
                "MOVE_OR_DROP must still prefer moving over throwing");
        assertEquals(EvictionAction.DROP, EvictionPolicy.decide(FallbackMode.MOVE_OR_DROP, false));
    }

    @Test
    @DisplayName("the pickup window arms, decays and can be cleared early")
    void pickupWindow() {
        EvictionTracker tracker = new EvictionTracker(SLOTS);
        assertFalse(tracker.pending(), "a fresh tracker must be idle");

        tracker.notePickup();
        assertTrue(tracker.pending());
        assertTrue(tracker.ready(), "no flush has run yet, so there is no cooldown");

        tracker.noteFlush();
        assertFalse(tracker.ready(), "a flush must be followed by a cooldown");
        for (int i = 0; i < EvictionTracker.FLUSH_COOLDOWN_TICKS; i++) {
            tracker.tick();
        }
        assertTrue(tracker.ready());

        tracker.clearPending();
        assertFalse(tracker.pending());
    }

    @Test
    @DisplayName("the pickup window expires so a permanently moving player stops queueing forever")
    void pickupWindowExpires() {
        EvictionTracker tracker = new EvictionTracker(SLOTS);
        tracker.notePickup();
        for (int i = 0; i < EvictionTracker.PICKUP_WINDOW_TICKS; i++) {
            tracker.tick();
        }
        assertFalse(tracker.pending());
    }

    @Test
    @DisplayName("attempts on an unchanged stack are capped, and reset when the stack changes")
    void retryCapping() {
        EvictionTracker tracker = new EvictionTracker(SLOTS);
        int signature = 1234;

        for (int i = 0; i < EvictionPolicy.MAX_ATTEMPTS_PER_SLOT; i++) {
            assertTrue(tracker.mayAttempt(3, signature), "attempt " + i + " must be allowed");
            tracker.recordAttempt(3);
        }
        assertFalse(tracker.mayAttempt(3, signature), "the same unchanged stack must stop being clicked");

        assertTrue(tracker.mayAttempt(3, signature + 1), "a changed stack means progress: retry it");
    }

    @Test
    @DisplayName("an emptied slot gets its retry budget back and other slots are independent")
    void emptyingAndIsolation() {
        EvictionTracker tracker = new EvictionTracker(SLOTS);
        int signature = 77;
        for (int i = 0; i < EvictionPolicy.MAX_ATTEMPTS_PER_SLOT; i++) {
            tracker.mayAttempt(0, signature);
            tracker.recordAttempt(0);
        }
        assertFalse(tracker.mayAttempt(0, signature));
        assertTrue(tracker.mayAttempt(1, signature), "slot 1 must keep its own budget");

        tracker.noteEmpty(0);
        assertTrue(tracker.mayAttempt(0, signature), "an emptied slot starts over");
    }

    @Test
    @DisplayName("out-of-range slots are refused rather than throwing")
    void outOfRangeSlots() {
        EvictionTracker tracker = new EvictionTracker(SLOTS);
        assertFalse(tracker.mayAttempt(-1, 1));
        assertFalse(tracker.mayAttempt(SLOTS, 1));
        tracker.recordAttempt(-1);
        tracker.noteEmpty(SLOTS);
    }

    @Test
    @DisplayName("the same stack may not be re-dropped into the same slot until the cooldown elapses")
    void droppingTheSameStackIsCooledDown() {
        EvictionTracker tracker = new EvictionTracker(SLOTS);
        int signature = 4242;

        assertTrue(tracker.mayDrop(4, signature), "the first drop is always allowed");
        tracker.recordDrop(4, signature);

        // The throw empties the slot; the retry budget resets, but the drop budget must not.
        tracker.noteEmpty(4);
        assertFalse(tracker.mayDrop(4, signature),
                "the stack the player just picked back up must not be thrown again straight away");
        assertTrue(tracker.mayDrop(5, signature), "other slots keep their own drop budget");

        for (int i = 0; i < EvictionPolicy.DROP_REPEAT_COOLDOWN_TICKS - 1; i++) {
            tracker.tick();
        }
        assertFalse(tracker.mayDrop(4, signature), "one tick short of the cooldown is still refused");
        tracker.tick();
        assertTrue(tracker.mayDrop(4, signature), "after the cooldown the slot may be drained again");
    }

    @Test
    @DisplayName("a different stack is droppable at once, but the per-connection cap still bites")
    void dropBudgetIsCappedPerConnection() {
        EvictionTracker tracker = new EvictionTracker(SLOTS);

        for (int i = 0; i < EvictionPolicy.MAX_DROPS_PER_SLOT; i++) {
            // A changing signature defeats the cooldown, which is exactly the drop/pickup loop.
            assertTrue(tracker.mayDrop(0, i), "drop " + i + " must be allowed");
            assertFalse(tracker.dropsExhausted(0));
            tracker.recordDrop(0, i);
        }

        assertTrue(tracker.dropsExhausted(0), "the per-connection cap must be reported as reached");
        assertFalse(tracker.mayDrop(0, 999), "no signature may get past the cap");
        for (int i = 0; i < EvictionPolicy.DROP_REPEAT_COOLDOWN_TICKS * 2; i++) {
            tracker.tick();
        }
        assertFalse(tracker.mayDrop(0, 999), "the cap is per connection, not a cooldown");

        tracker.reset();
        assertFalse(tracker.dropsExhausted(0), "a new connection starts over");
        assertTrue(tracker.mayDrop(0, 999));
    }

    @Test
    @DisplayName("out-of-range slots are refused by the drop budget too")
    void outOfRangeDrops() {
        EvictionTracker tracker = new EvictionTracker(SLOTS);
        assertFalse(tracker.mayDrop(-1, 1));
        assertFalse(tracker.mayDrop(SLOTS, 1));
        assertFalse(tracker.dropsExhausted(SLOTS));
        tracker.recordDrop(-1, 1);
    }

    @Test
    @DisplayName("reset clears both the window and every retry budget")
    void resetClearsEverything() {
        EvictionTracker tracker = new EvictionTracker(SLOTS);
        tracker.notePickup();
        tracker.noteFlush();
        for (int i = 0; i < EvictionPolicy.MAX_ATTEMPTS_PER_SLOT; i++) {
            tracker.mayAttempt(2, 5);
            tracker.recordAttempt(2);
        }

        tracker.reset();

        assertFalse(tracker.pending());
        assertFalse(tracker.ready());
        assertTrue(tracker.mayAttempt(2, 5));
    }
}
