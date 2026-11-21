package com.instrumentalist.krs.hacks.features.player.phase.features;

import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.hacks.features.player.Phase;
import com.instrumentalist.krs.hacks.features.player.phase.PhaseEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;

public class WatchdogLegacyPhase implements PhaseEvent {

    @Override
    public String getName() {
        return "Watchdog Legacy";
    }

    @Override
    public void onMotion(MotionEvent event) {
        if (event.post || mc.level == null) return;

        var player = mc.player;
        if (player == null) return;

        if (player.isShiftKeyDown()) {
            player.setPos(player.getX(), player.getY() - 4.0, player.getZ());
            Phase.requestDisable();
            return;
        }

        if (!MovementUtil.isMoving()) return;

        double direction = Math.toRadians(MovementUtil.getPlayerDirection());
        double sin = -Math.sin(direction);
        double cos = Math.cos(direction);

        if (player.horizontalCollision) {
            double endX = 0.0;
            double endZ = 0.0;
            for (double d = 0.0; d <= 1.0; d += 0.02) {
                double x = sin * d;
                double z = cos * d;
                if (!mc.level.noCollision(player, player.getBoundingBox().move(x, 0.0, z))) {
                    endX = x;
                    endZ = z;
                    player.setPos(player.getX() + x * 0.001, player.getY(), player.getZ() + z * 0.001);
                }
            }
            player.setPos(player.getX() + endX * 0.01, player.getY(), player.getZ() + endZ * 0.01);
        }

        boolean inWall = false;
        for (double d = 0.0; d <= 0.1; d += 0.02) {
            if (!mc.level.noCollision(player, player.getBoundingBox().move(sin * d, 0.0, cos * d))) {
                inWall = true;
                break;
            }
        }

        if (inWall && player.onGround())
            player.setPos(player.getX() + sin * 0.2873, player.getY(), player.getZ() + cos * 0.2873);
    }

}
