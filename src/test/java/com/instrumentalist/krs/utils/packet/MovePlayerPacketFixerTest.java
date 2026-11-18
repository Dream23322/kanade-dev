package com.instrumentalist.krs.utils.packet;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovePlayerPacketFixerTest {
    private final Object connection = new Object();
    private final List<Packet<?>> sent = new ArrayList<>();

    private void beforeSend(Packet<?> packet) {
        MovePlayerPacketFixer.beforeSend(connection, packet, sent::add);
    }

    private static ServerboundMovePlayerPacket.Pos position(double x) {
        return new ServerboundMovePlayerPacket.Pos(x, 64.0d, 0.0d, true, false);
    }

    @Test
    void insertsClientTickEndBetweenTwoPositionPackets() {
        beforeSend(position(1.0d));
        assertTrue(sent.isEmpty(), "the first position starts the move tick");

        beforeSend(position(2.0d));

        assertEquals(1, sent.size());
        assertSame(ServerboundClientTickEndPacket.INSTANCE, sent.getFirst());
    }

    @Test
    void clientTickEndResetsThePositionBoundary() {
        beforeSend(position(1.0d));
        beforeSend(ServerboundClientTickEndPacket.INSTANCE);
        beforeSend(position(2.0d));

        assertTrue(sent.isEmpty(), "a real tick end already closed the previous position");

        beforeSend(position(3.0d));
        assertEquals(1, sent.size());
        assertSame(ServerboundClientTickEndPacket.INSTANCE, sent.getFirst());
    }

    @Test
    void teleportAcknowledgementResetsWithoutAnInsertedTickEnd() {
        beforeSend(position(1.0d));
        beforeSend(new ServerboundAcceptTeleportationPacket(7, 1.0d, 64.0d, 0.0d, 0.0f, 0.0f));
        beforeSend(position(2.0d));

        assertTrue(sent.isEmpty(),
                "the acknowledgement must not be preceded by a synthetic tick end or leave a stale position flag");
    }
}
