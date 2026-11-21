package com.instrumentalist.krs.hacks.features.player.phase;

import com.instrumentalist.krs.events.guards.BlockEventCollisionGuard;
import com.instrumentalist.krs.utils.IMinecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Block collision test shared by the phase modes, ignoring shapes spoofed by BlockEvent. */
public final class PhaseCollision implements IMinecraft {

    private PhaseCollision() {
    }

    public static boolean intersects(LocalPlayer player, AABB boundingBox) {
        if (mc.level == null)
            return false;

        int minX = Mth.floor(boundingBox.minX);
        int maxX = Mth.floor(boundingBox.maxX);
        int minY = Math.max(mc.level.getMinY(), Mth.floor(boundingBox.minY));
        int maxY = Math.min(mc.level.getMaxY() - 1, Mth.floor(boundingBox.maxY));
        int minZ = Mth.floor(boundingBox.minZ);
        int maxZ = Mth.floor(boundingBox.maxZ);
        CollisionContext context = CollisionContext.of(player);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    var blockState = mc.level.getBlockState(blockPos);
                    if (blockState.isAir())
                        continue;

                    var shape = BlockEventCollisionGuard.getOriginalCollisionShape(
                            blockState,
                            mc.level,
                            blockPos,
                            context
                    );
                    for (AABB localBox : shape.toAabbs()) {
                        if (localBox.move(blockPos).intersects(boundingBox))
                            return true;
                    }
                }
            }
        }

        return false;
    }
}
