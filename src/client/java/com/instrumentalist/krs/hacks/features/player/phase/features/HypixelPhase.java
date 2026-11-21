package com.instrumentalist.krs.hacks.features.player.phase.features;

import com.instrumentalist.krs.events.features.BlockEvent;
import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.features.player.phase.PhaseEvent;
import com.instrumentalist.krs.utils.math.TickTimer;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

public class HypixelPhase implements PhaseEvent {
    private boolean clipping = false;
    private final TickTimer phaseTimer = new TickTimer();
    private Float cachedDirection = null;

    @Override
    public String getName() {
        return "Hypixel";
    }

    @Override
    public void onBlock(BlockEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (clipping && event.blockPos.getY() != player.blockPosition().below().getY())
            event.cancel();
    }

    @Override
    public void onMotion(MotionEvent event) {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) return;

        if (!player.horizontalCollision && !clipping) return;

        phaseTimer.update();

        if (phaseTimer.hasTimePassed(3)) {
            if (phaseTimer.hasTimePassed(20)) {
                clipping = false;
                phaseTimer.reset();
                cachedDirection = null;
            }
        } else if (phaseTimer.hasTimePassed(1)) {
            float direction = cachedDirection == null ? (float) Math.toRadians((MovementUtil.getPlayerDirection() % 360 + 360) % 360.0) : cachedDirection;
            double sin = Mth.sin(direction);
            double cos = Mth.cos(direction);

            double xPos = event.x - sin * -0.25;
            double zPos = event.z + cos * -0.25;
            Double closestSurfaceY = null;

            var box = player.getBoundingBox();
            double playerX = box.getCenter().x - sin * -0.25;
            double playerZ = box.getCenter().z + cos * -0.25;
            double playerFeetY = box.minY;

            for (int y = (int) playerFeetY; y >= Math.max((int) playerFeetY - 10, level.getMinY()); y--) {
                BlockPos blockPos = new BlockPos((int) Math.floor(playerX), y, (int) Math.floor(playerZ));
                var blockState = level.getBlockState(blockPos);

                if (!blockState.isAir()) {
                    var shape = blockState.getCollisionShape(level, blockPos);
                    double blockMaxY = Double.NEGATIVE_INFINITY;
                    for (var collisionBox : shape.toAabbs()) {
                        if (collisionBox.maxY > blockMaxY)
                            blockMaxY = collisionBox.maxY;
                    }
                    if (blockMaxY == Double.NEGATIVE_INFINITY)
                        blockMaxY = 1.0;
                    double surfaceY = y + blockMaxY;

                    if (surfaceY <= playerFeetY) {
                        closestSurfaceY = surfaceY;
                        break;
                    }
                }
            }

            if (phaseTimer.hasTimePassed(2)) {
                if (closestSurfaceY != null) {
                    event.onGround = true;
                    event.x = xPos;
                    event.y = closestSurfaceY - 0.07;
                    event.z = zPos;
                }
                phaseTimer.update();
            } else if (closestSurfaceY != null) {
                MovementUtil.stopMoving();
                clipping = true;
                if (cachedDirection == null)
                    cachedDirection = direction;
            }
        }
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onWorld(WorldEvent event) {
        reset();
    }

    private void reset() {
        clipping = false;
        phaseTimer.reset();
        cachedDirection = null;
    }
}
