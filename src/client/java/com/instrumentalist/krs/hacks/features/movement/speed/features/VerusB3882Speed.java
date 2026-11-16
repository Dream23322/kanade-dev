package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.events.features.PlayerMoveEvent;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * LiquidBounce's Verus-b3882: a small jump boost, moved-direction strafe on every
 * client move and a permanent 2x timer.
 */
public class VerusB3882Speed extends SpeedBHop {

    @Override
    public String getName() {
        return "Verus-b3882";
    }

    @Override
    protected void onSpeedUpdate(LocalPlayer player, boolean jumped) {
        if (jumped)
            MovementUtil.scaleVelocityXZ(1.1);

        TimerUtil.timerSpeed = 2.0f;
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (mc.player == null || event.type != MoverType.SELF || !MovementUtil.isMoving()) return;

        double speed = Math.sqrt(event.movement.x * event.movement.x + event.movement.z * event.movement.z);
        Vec3 direction = MovementUtil.getHorizontalDirectionVector(MovementUtil.getPlayerDirection());
        event.movement = new Vec3(direction.x * speed, event.movement.y, direction.z * speed);
    }
}
