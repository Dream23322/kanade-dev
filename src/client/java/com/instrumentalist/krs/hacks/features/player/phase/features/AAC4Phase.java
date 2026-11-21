package com.instrumentalist.krs.hacks.features.player.phase.features;

import com.instrumentalist.krs.hacks.features.player.Phase;
import com.instrumentalist.krs.hacks.features.player.phase.PhaseEvent;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class AAC4Phase implements PhaseEvent {

    @Override
    public String getName() {
        return "AAC 4";
    }

    @Override
    public void onEnable() {
        if (mc.player == null) return;
        Phase.requestDisable();
    }

    @Override
    public void onDisable() {
        var player = mc.player;
        if (player == null) return;

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        boolean horizontalCollision = player.horizontalCollision;
        PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(x, y - 0.00000001, z, false, horizontalCollision));
        PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(x, y - 1, z, false, horizontalCollision));
    }
}
