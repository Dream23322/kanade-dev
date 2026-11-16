package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;

/**
 * LiquidBounce's HylexGround: after a few ground ticks, the existing ground motion is
 * multiplied. Only works without the Speed effect.
 */
public class HylexGroundSpeed extends SpeedBHop {

    private int groundTicks;

    @Override
    public String getName() {
        return "HylexGround";
    }

    @Override
    protected boolean autoJump() {
        return false;
    }

    @Override
    protected void onSpeedUpdate(LocalPlayer player, boolean jumped) {
        if (!player.onGround()) {
            groundTicks = 0;
            return;
        }

        groundTicks++;
        if (groundTicks <= 5) return;
        if (player.hurtTime >= 1) return;
        if (MovementUtil.getSpeedAmplifier() >= 1) return;

        boolean strafing = mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
        MovementUtil.scaleVelocityXZ(strafing ? 1.214 : 1.2174);
    }
}
