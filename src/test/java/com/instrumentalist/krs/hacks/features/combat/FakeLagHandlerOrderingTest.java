package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.events.features.SendPacketEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives FakeLag's actual SendPacketEvent handler. The test only overrides the live-world
 * readiness and lag eligibility checks; packet classification, cancellation, queueing, flushing,
 * and teleport handling all run through FakeLag.onSendPacket.
 */
class FakeLagHandlerOrderingTest {
    private final List<Packet<?>> delivered = new ArrayList<>();
    private final FakeLag fakeLag = new TestFakeLag(delivered::add);

    private static ServerboundMovePlayerPacket.Pos movement(double x) {
        return new ServerboundMovePlayerPacket.Pos(x, 64.0d, 0.0d, true, false);
    }

    private static ServerboundPlayerActionPacket action() {
        return new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ZERO,
                Direction.DOWN
        );
    }

    private void send(Packet<?> packet) {
        SendPacketEvent event = new SendPacketEvent(packet);
        fakeLag.onSendPacket(event);
        if (!event.isCancelled())
            delivered.add(event.packet);
    }

    @Test
    void handlerHoldsMovementTickEndAndPongThenFlushesThemBeforeAction() {
        Packet<?> movement = movement(1.0d);
        Packet<?> tickEnd = ServerboundClientTickEndPacket.INSTANCE;
        Packet<?> pong = new ServerboundPongPacket(1);
        Packet<?> action = action();

        send(movement);
        send(tickEnd);
        send(pong);

        assertTrue(delivered.isEmpty(), "held packets must not reach the connection");

        send(action);

        assertEquals(4, delivered.size());
        assertSame(movement, delivered.get(0));
        assertSame(tickEnd, delivered.get(1));
        assertSame(pong, delivered.get(2));
        assertSame(action, delivered.get(3));
    }

    @Test
    void handlerDropsHeldPacketsAndPassesTeleportAcknowledgementAlone() {
        send(movement(1.0d));
        send(ServerboundClientTickEndPacket.INSTANCE);
        send(new ServerboundPongPacket(2));

        Packet<?> acknowledgement =
                new ServerboundAcceptTeleportationPacket(7, 1.0d, 64.0d, 0.0d, 0.0f, 0.0f);
        send(acknowledgement);

        assertEquals(1, delivered.size());
        assertSame(acknowledgement, delivered.get(0));
    }

    private static final class TestFakeLag extends FakeLag {
        private TestFakeLag(Consumer<Packet<?>> queuedPacketSender) {
            super(queuedPacketSender);
        }

        @Override
        boolean isReady() {
            return true;
        }

        @Override
        boolean canLag() {
            return true;
        }

        @Override
        boolean shouldQueue() {
            return true;
        }
    }
}
