package smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.zannagh.freemyhotbar.presence.PresenceState;
import io.github.zannagh.freemyhotbar.presence.ServerPresenceResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the presence derivation. The dangerous case is the middle one: a server running
 * the networking library WITHOUT this mod answers the handshake but handles none of its packets,
 * and must read as ABSENT so the client-side fallback arms.
 */
@DisplayName("ServerPresenceResolver")
class ServerPresenceResolverTest {

    @Test
    @DisplayName("an unresolved handshake is UNKNOWN")
    void unresolvedIsUnknown() {
        assertEquals(PresenceState.UNKNOWN, ServerPresenceResolver.resolve(false, false, false));
        assertEquals(PresenceState.UNKNOWN, ServerPresenceResolver.resolve(false, true, false));
    }

    @Test
    @DisplayName("a resolved server that handles the channel is PRESENT")
    void supportedChannelIsPresent() {
        assertEquals(PresenceState.PRESENT, ServerPresenceResolver.resolve(true, true, false));
    }

    @Test
    @DisplayName("a resolved server that does NOT handle the channel is ABSENT")
    void unsupportedChannelIsAbsent() {
        assertEquals(PresenceState.ABSENT, ServerPresenceResolver.resolve(true, false, false));
    }

    @Test
    @DisplayName("singleplayer short-circuits to PRESENT regardless of the handshake")
    void singleplayerIsAlwaysPresent() {
        assertEquals(PresenceState.PRESENT, ServerPresenceResolver.resolve(false, false, true));
        assertEquals(PresenceState.PRESENT, ServerPresenceResolver.resolve(true, false, true));
    }
}
