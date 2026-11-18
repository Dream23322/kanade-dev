package com.instrumentalist.krs.hacks.features.combat;

import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FakeLagControlPacketTest {
    @Test
    void teleportAcknowledgementDropsTheQueueInsteadOfBeingQueued() {
        ServerboundAcceptTeleportationPacket packet =
                new ServerboundAcceptTeleportationPacket(1, 0.0d, 64.0d, 0.0d, 0.0f, 0.0f);

        assertEquals(FakeLagQueue.ControlAction.DROP_QUEUE, FakeLag.controlAction(packet));
    }

    @Test
    void transactionResponseUsesTheNormalQueueLikeLiquidBounce() {
        assertEquals(FakeLagQueue.ControlAction.NONE, FakeLag.controlAction(new ServerboundPongPacket(1)));
    }

    @Test
    void ordinaryMovementKeepsNormalQueueRules() {
        ServerboundMovePlayerPacket.Pos packet =
                new ServerboundMovePlayerPacket.Pos(0.0d, 64.0d, 0.0d, true, false);

        assertEquals(FakeLagQueue.ControlAction.NONE, FakeLag.controlAction(packet));
    }
}
