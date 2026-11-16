package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

/**
 * LiquidBounce's Vulcan-2.8.8: a large strafe sequence after the jump plus forced
 * on-ground movement packets while descending.
 */
public class Vulcan288Speed extends SpeedBHop {

    private int ticksSinceJump = -1;

    @Override
    public String getName() {
        return "Vulcan-2.8.8";
    }

    @Override
    protected void onSpeedUpdate(LocalPlayer player, boolean jumped) {
        boolean hasSpeed = MovementUtil.getSpeedAmplifier() != 0;

        if (jumped) {
            ticksSinceJump = 0;
            MovementUtil.strafe(hasSpeed ? 0.771f : 0.5f);
        } else if (ticksSinceJump >= 0) {
            ticksSinceJump++;

            switch (ticksSinceJump) {
                case 1 -> MovementUtil.strafe(hasSpeed ? 0.605f : 0.31f);
                case 2 -> {
                    MovementUtil.strafe(hasSpeed ? 0.57f : 0.29f);
                    MovementUtil.setVelocityY(hasSpeed ? -0.5 : -0.37);
                }
                case 3 -> MovementUtil.strafe(hasSpeed ? 0.595f : 0.27f);
                case 4 -> {
                    MovementUtil.strafe(hasSpeed ? 0.595f : 0.28f);
                    ticksSinceJump = -1;
                }
            }
        }

        if (!player.onGround() && Math.abs(player.fallDistance) > 0 && hasSpeed)
            MovementUtil.scaleVelocityXZ(1.055);
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (event.packet instanceof ServerboundMovePlayerPacket packet && player.getDeltaMovement().y < 0)
            packet.onGround = true;
    }
}
