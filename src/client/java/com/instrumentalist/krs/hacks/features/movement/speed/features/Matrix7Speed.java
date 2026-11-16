package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;

/**
 * LiquidBounce's Matrix-7: a fixed jump height with a strafe on the jump tick, and a
 * strafe whenever the air motion drops below the threshold.
 */
public class Matrix7Speed extends SpeedBHop {

    @Override
    public String getName() {
        return "Matrix-7";
    }

    @Override
    protected float jumpMotionY() {
        return 0.419652f;
    }

    @Override
    protected void onSpeedUpdate(LocalPlayer player, boolean jumped) {
        if (!MovementUtil.isMoving()) return;

        if (player.onGround() && jumped)
            MovementUtil.strafe(MovementUtil.getSpeed());
        else if (!player.onGround() && MovementUtil.getSpeedSquared() < 0.04)
            MovementUtil.strafe(MovementUtil.getSpeed());
    }
}
