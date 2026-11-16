package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.utils.move.InputUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;

/**
 * LiquidBounce's Vulcan-2.8.6: a timed sequence of strafe speeds after each jump.
 */
public class Vulcan286Speed extends SpeedBHop {

    private int ticksSinceJump = -1;

    @Override
    public String getName() {
        return "Vulcan-2.8.6";
    }

    @Override
    protected void onSpeedUpdate(LocalPlayer player, boolean jumped) {
        if (jumped) {
            ticksSinceJump = 0;
            return;
        }

        if (ticksSinceJump < 0) return;

        ticksSinceJump++;
        int speedLevel = MovementUtil.getSpeedAmplifier();
        boolean goingSideways = InputUtil.leftImpulse(player.input) != 0f;

        switch (ticksSinceJump) {
            case 1 -> MovementUtil.strafe((float) (goingSideways
                    ? 0.3345
                    : 0.3355 * (1 + speedLevel * 0.3819)));
            case 2 -> {
                if (player.isSprinting())
                    MovementUtil.strafe((float) (goingSideways
                            ? 0.3235
                            : 0.3284 * (1 + speedLevel * 0.355)));
            }
            case 4 -> MovementUtil.setVelocityY(-0.376);
            case 6 -> {
                if (player.fallDistance > 0.298)
                    MovementUtil.strafe(0.298f);

                ticksSinceJump = -1;
            }
        }
    }
}
