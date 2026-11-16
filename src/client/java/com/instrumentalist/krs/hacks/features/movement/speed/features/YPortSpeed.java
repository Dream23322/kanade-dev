package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;

/**
 * LiquidBounce's YPort: a normal jump with the configured strafe, then the fall is
 * fast-forwarded to keep the server's movement check busy.
 */
public class YPortSpeed extends SpeedBHop {

    @Override
    public String getName() {
        return "YPort";
    }

    @Override
    protected void onSpeedUpdate(LocalPlayer player, boolean jumped) {
        if (!MovementUtil.isMoving()) return;

        if (jumped)
            MovementUtil.strafe(SpeedModule.yportSpeed.get());
        else if (!player.onGround())
            MovementUtil.setVelocityY(-1.0);
    }
}
