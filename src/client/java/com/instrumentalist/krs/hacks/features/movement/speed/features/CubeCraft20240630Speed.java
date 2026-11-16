package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.MoverType;

/**
 * LiquidBounce's CubeCraft-2024-06-30 (Sentinel): a repeated fake fall-damage
 * teleport sequence, followed by a burst of speed after being hurt.
 */
public class CubeCraft20240630Speed implements SpeedEvent {

    private boolean hasBeenHurt;
    private boolean adjusted;
    private int reboostTicks;

    @Override
    public String getName() {
        return "CubeCraft-2024-06-30";
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || !MovementUtil.isMoving()) return;

        if (reboostTicks > 0) {
            reboostTicks--;
            return;
        }

        boost(player);
        reboostTicks = SpeedModule.cubeCraftReboostTicks.get();
    }

    @Override
    public void onMotion(MotionEvent event) {
    }

    @Override
    public void onTick(TickEvent event) {
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (player.hurtTime > 0 && !hasBeenHurt) {
            hasBeenHurt = true;
            adjusted = true;
        }

        if (!hasBeenHurt && !adjusted) return;

        if (event.type == MoverType.SELF && MovementUtil.isMoving())
            MovementUtil.strafe(SpeedModule.cubeCraftSpeed.get(), 1.0);
    }

    private void boost(LocalPlayer player) {
        PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(
                player.getX(), player.getY(), player.getZ(), false, false));
        PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(
                player.getX(), player.getY() + 3.25, player.getZ(), false, false));
        PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(
                player.getX(), player.getY(), player.getZ(), false, false));
        PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(
                player.getX(), player.getY(), player.getZ(), true, false));
    }
}
