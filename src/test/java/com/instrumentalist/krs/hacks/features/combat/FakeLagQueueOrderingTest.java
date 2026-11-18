package com.instrumentalist.krs.hacks.features.combat;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FakeLagQueueOrderingTest {
    private final List<Packet<?>> sent = new ArrayList<>();
    private final FakeLagQueue<Packet<?>> queue = new FakeLagQueue<>(32, sent::add);

    private static ServerboundMovePlayerPacket.Pos movement(double x) {
        return new ServerboundMovePlayerPacket.Pos(x, 64.0d, 0.0d, true, false);
    }

    private void passControl(Packet<?> packet) {
        queue.handleControl(FakeLag.controlAction(packet), packet, sent::add);
    }

    @Test
    void pongIsHeldWithMovementAndClientTickEndLikeLiquidBounce() {
        ServerboundMovePlayerPacket.Pos movement = movement(1.0d);
        ServerboundPongPacket pong = new ServerboundPongPacket(1);

        queue.enqueue(movement, 1L);
        queue.enqueue(ServerboundClientTickEndPacket.INSTANCE, 2L);
        queue.enqueue(pong, 3L);
        queue.flush();

        assertEquals(3, sent.size());
        assertSame(movement, sent.get(0));
        assertSame(ServerboundClientTickEndPacket.INSTANCE, sent.get(1));
        assertSame(pong, sent.get(2));
    }

    @Test
    void attackFlushesHeldTicksAndLeavesCurrentTickQueuedLikeLiquidBounce() {
        ServerboundMovePlayerPacket.Pos movement1 = movement(1.0d);
        ServerboundMovePlayerPacket.Pos movement2 = movement(2.0d);

        queue.enqueue(movement1, 1L);
        queue.enqueue(ServerboundClientTickEndPacket.INSTANCE, 2L);

        queue.flush();
        queue.enqueue(movement2, 3L);
        queue.enqueue(ServerboundClientTickEndPacket.INSTANCE, 4L);
        queue.flush();

        assertEquals(4, sent.size());
        assertSame(movement1, sent.get(0));
        assertSame(ServerboundClientTickEndPacket.INSTANCE, sent.get(1));
        assertSame(movement2, sent.get(2));
        assertSame(ServerboundClientTickEndPacket.INSTANCE, sent.get(3));
    }

    @Test
    void teleportAcknowledgementDropsHeldPacketsAndSendsAckAlone() {
        ServerboundAcceptTeleportationPacket ack =
                new ServerboundAcceptTeleportationPacket(7, 1.0d, 64.0d, 0.0d, 0.0f, 0.0f);

        queue.enqueue(movement(1.0d), 1L);
        queue.enqueue(ServerboundClientTickEndPacket.INSTANCE, 2L);
        queue.enqueue(new ServerboundPongPacket(2), 3L);

        passControl(ack);

        assertEquals(List.of(ack), sent);
        assertFalse(queue.hasQueued());
    }

    @Test
    void tickEndsAreHeldWithTheirMovementInsteadOfBeingPassedEmpty() {
        queue.enqueue(movement(1.0d), 1L);
        queue.enqueue(ServerboundClientTickEndPacket.INSTANCE, 2L);

        assertTrue(queue.hasQueued());
        assertEquals(1L, queue.firstTimestampNanos());
        assertFalse(queue.isFlushing());
    }
}
