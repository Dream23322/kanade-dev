package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;

/**
 * LiquidBounce's HylexLowHop: a lowered jump and small speed top-ups while airborne.
 */
public class HylexLowHopSpeed extends SpeedBHop {

    @Override
    public String getName() {
        return "HylexLowHop";
    }

    @Override
    protected float jumpMotionY() {
        return 0.33f;
    }

    @Override
    protected void onSpeedUpdate(LocalPlayer player, boolean jumped) {
        if (player.onGround()) {
            if (MovementUtil.isMoving() && MovementUtil.getSpeed() < 0.32)
                MovementUtil.scaleVelocityXZ(1.1);

            return;
        }

        int airTicks = MovementUtil.fallTicks;

        if (airTicks == 9 && MovementUtil.getSpeed() < 0.29)
            MovementUtil.scaleVelocityXZ(1.007);

        if (airTicks == 1 && MovementUtil.getSpeed() < 0.20)
            MovementUtil.scaleVelocityXZ(1.01);

        if (player.getDeltaMovement().y > 0 && airTicks <= 2 && MovementUtil.getSpeed() < 0.2)
            MovementUtil.scaleVelocityXZ(1.02);
    }
}
