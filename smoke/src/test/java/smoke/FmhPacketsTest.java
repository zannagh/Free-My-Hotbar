package smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.zannagh.eunomia.networking.comms.CommunicationManager;
import de.zannagh.eunomia.networking.packets.PacketType;
import de.zannagh.eunomia.networking.serialization.PayloadCodec;
import io.github.zannagh.freemyhotbar.FreeMyHotbar;
import io.github.zannagh.freemyhotbar.net.FmhPackets;
import io.github.zannagh.freemyhotbar.net.LockedSlotsPayload;
import io.github.zannagh.freemyhotbar.slot.SlotBlock;
import io.github.zannagh.freemyhotbar.slot.SlotLockState;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Plain-JVM tests for the mod's eunomia packet wiring: the channel key, the gzip(JSON) wire
 * round-trip and the server-side handler. Runs against the real eunomia-core, no Minecraft.
 */
@DisplayName("FmhPackets wire contract")
class FmhPacketsTest {

    /**
     * The channel key as it was first shipped. It is the handshake token a server advertises and
     * the client reads back to decide whether locks are enforced server-side, so changing it
     * breaks every already-released build in both directions. Pinned as a literal on purpose: if
     * a refactor moves the namespace or the path, this test — not a player — finds out.
     */
    private static final String CHANNEL_KEY = "free-my-hotbar:locked_slots";

    @BeforeAll
    static void registerHandler() {
        FmhPackets.register();
    }

    @Test
    @DisplayName("locked-slots channel key is stable")
    void channelKeyIsStable() {
        assertEquals(CHANNEL_KEY, FmhPackets.LOCKED_SLOTS.channelKey());
        assertEquals(FreeMyHotbar.MOD_ID, FmhPackets.LOCKED_SLOTS.namespace());
        assertEquals("locked_slots", FmhPackets.LOCKED_SLOTS.path());
    }

    @Test
    @DisplayName("registration advertises the channel to connecting clients")
    void registrationAdvertisesChannel() {
        assertTrue(CommunicationManager.serverHandlerChannels().contains(CHANNEL_KEY));
        PacketType<?> byKey = CommunicationManager.type(CHANNEL_KEY);
        assertEquals(FmhPackets.LOCKED_SLOTS, byKey);
    }

    /**
     * {@link SlotBlock} is a record and the wire format is gzip(JSON) produced by Gson. Records
     * have no no-arg constructor, which is the classic Gson trap; Gson has supported them since
     * 2.10, the version Minecraft 1.20.1 ships. This proves it against the real codec rather than
     * assuming it.
     */
    @Test
    @DisplayName("payload of SlotBlock records round-trips through the payload codec")
    void payloadRoundTrips() {
        LockedSlotsPayload sent = new LockedSlotsPayload(List.of(
                new SlotBlock(0, true),
                new SlotBlock(3, false),
                new SlotBlock(8, true)));

        byte[] encoded = PayloadCodec.encode(sent, true);
        LockedSlotsPayload received = PayloadCodec.decode(encoded, LockedSlotsPayload.class);

        assertEquals(sent.slots(), received.slots());
    }

    @Test
    @DisplayName("a payload whose slots field is missing decodes to an empty list")
    void missingSlotsFieldDecodesEmpty() {
        LockedSlotsPayload received = PayloadCodec.decode(gzip("{}"), LockedSlotsPayload.class);

        assertTrue(received.slots().isEmpty());
    }

    @Test
    @DisplayName("the server handler stores the sender's blocked slots")
    void handlerStoresBlockedSlots() {
        UUID sender = UUID.randomUUID();
        SlotLockState.remove(sender);
        byte[] encoded = PayloadCodec.encode(
                new LockedSlotsPayload(List.of(new SlotBlock(2, true), new SlotBlock(5, false))),
                true);

        boolean dispatched = CommunicationManager.dispatchServerboundRaw(
                CHANNEL_KEY, encoded, new TestServerContext(sender));

        assertTrue(dispatched);
        assertEquals(Set.of(new SlotBlock(2, true)), SlotLockState.blocked(sender));
        SlotLockState.remove(sender);
    }

    /**
     * A hostile payload must not escape the dispatch as an exception: {@code SlotBlock}'s compact
     * constructor rejects a negative slot id, and eunomia has to absorb that rather than let it
     * reach the network thread.
     */
    @Test
    @DisplayName("a payload with an out-of-range slot id is rejected, not thrown")
    void hostilePayloadIsRejected() {
        UUID sender = UUID.randomUUID();
        byte[] hostile = gzip("{\"slots\":[{\"slotId\":-5,\"blocked\":true}]}");

        boolean dispatched = CommunicationManager.dispatchServerboundRaw(
                CHANNEL_KEY, hostile, new TestServerContext(sender));

        assertFalse(dispatched);
        assertTrue(SlotLockState.blocked(sender).isEmpty());
    }

    private static byte[] gzip(String json) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** Minimal {@code ServerContext} standing in for a real connection. */
    private record TestServerContext(UUID sender) implements de.zannagh.eunomia.networking.packets.ServerContext {

        @Override
        public UUID senderId() {
            return sender;
        }

        @Override
        public String senderName() {
            return "test-player";
        }

        @Override
        public <T> void reply(PacketType<T> type, T payload) {
            throw new UnsupportedOperationException("no replies in this test");
        }
    }
}
