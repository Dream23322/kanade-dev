package com.instrumentalist.krs.utils.packet;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.function.Consumer;

public final class MovePlayerPacketFixer {
    private static Object currentConnection;
    private static boolean positionSentThisTick;

    private MovePlayerPacketFixer() {
    }

    public static void beforeSend(Object connection, Packet<?> packet, Consumer<Packet<?>> sendRaw) {
        if (connection != currentConnection) {
            currentConnection = connection;
            positionSentThisTick = false;
        }

        if (packet instanceof ServerboundClientTickEndPacket) {
            positionSentThisTick = false;
            return;
        }

        // The teleport acknowledgement must go out immediately; closing the tick first can make
        // Grim treat the teleport as ignored. The teleport resets the position state anyway.
        if (packet instanceof ServerboundAcceptTeleportationPacket) {
            positionSentThisTick = false;
            return;
        }

        if (!(packet instanceof ServerboundMovePlayerPacket movePacket) || !movePacket.hasPosition())
            return;

        if (positionSentThisTick)
            sendRaw.accept(ServerboundClientTickEndPacket.INSTANCE);

        positionSentThisTick = true;
    }
}
