package com.instrumentalist.krs.hacks.features.player.phase.features;

import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.features.player.Phase;
import com.instrumentalist.krs.hacks.features.player.phase.PhaseCollision;
import com.instrumentalist.krs.hacks.features.player.phase.PhaseEvent;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * NoCheatPlus phase. NCP compares every move against its own horizontal limit and sets the player
 * back on a single large step, while in-block (passable) violations are only latched after several
 * consecutive moves. So the mode slows the client timer (which spaces the move packets out), walks
 * into the block in sub-threshold steps, and only then crosses to the first collision-free position,
 * aborting into a cooldown whenever the server sends a setback.
 */
public class NCPPhase implements PhaseEvent {

    private static final double SCAN_STEP = 0.03125;
    private static final double WALL_PROBE_DISTANCE = 0.06;

    private enum State {
        IDLE,
        STEPPING,
        CROSSING,
        COOLDOWN
    }

    private State state = State.IDLE;
    private Vec3 direction = Vec3.ZERO;
    private int steppingTicks;
    private int cooldownTicks;
    /** True while this mode is the one slowing the client timer. */
    private boolean timerSlowed;

    @Override
    public String getName() {
        return "NCP";
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            reset();
            return;
        }

        switch (state) {
            case IDLE -> start(player);
            case STEPPING -> step(player);
            case CROSSING -> cross(player);
            case COOLDOWN -> {
                if (--cooldownTicks <= 0)
                    state = State.IDLE;
            }
        }
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (!(event.packet instanceof ClientboundPlayerPositionPacket))
            return;

        if (state != State.STEPPING && state != State.CROSSING)
            return;

        abort();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onWorld(WorldEvent event) {
        reset();
    }

    private void start(LocalPlayer player) {
        if (!MovementUtil.isMoving())
            return;

        Vec3 dir = currentDirection(player);
        if (!hasWallAhead(player, dir))
            return;

        direction = dir;
        MovementUtil.stopMoving();
        TimerUtil.timerSpeed = Phase.ncpTimer.get();
        timerSlowed = true;
        steppingTicks = 0;
        state = State.STEPPING;
    }

    private void step(LocalPlayer player) {
        MovementUtil.stopMoving();

        double step = Phase.ncpStep.get();
        player.setPos(
                player.getX() + direction.x * step,
                player.getY(),
                player.getZ() + direction.z * step
        );

        if (++steppingTicks >= Phase.ncpWarmup.get())
            state = State.CROSSING;
    }

    private void cross(LocalPlayer player) {
        AABB box = player.getBoundingBox();
        Vec3 start = player.position();
        double step = Phase.ncpStep.get();

        for (double distance = step; distance <= Phase.ncpCrossDistance.get(); distance += SCAN_STEP) {
            double offsetX = direction.x * distance;
            double offsetZ = direction.z * distance;

            if (PhaseCollision.intersects(player, box.move(offsetX, 0.0, offsetZ)))
                continue;

            double x = start.x + offsetX;
            double y = start.y;
            double z = start.z + offsetZ;
            player.setPos(x, y, z);
            PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(x, y, z, player.onGround(), false));
            finish();
            return;
        }

        abort();
    }

    /** Direction is always a unit vector: the movement vector, or the camera yaw when configured. */
    private Vec3 currentDirection(LocalPlayer player) {
        if ("camera".equalsIgnoreCase(Phase.ncpDirection.get()))
            return MovementUtil.getHorizontalDirectionVector(player.getYRot());

        return MovementUtil.getMovementVector();
    }

    private boolean hasWallAhead(LocalPlayer player, Vec3 dir) {
        if (player.horizontalCollision)
            return true;

        AABB probe = player.getBoundingBox()
                .move(dir.x * WALL_PROBE_DISTANCE, 0.0, dir.z * WALL_PROBE_DISTANCE);
        return !mc.level.noCollision(player, probe);
    }

    /** Ends a successful phase; with Keep Timer the slowdown is intentionally left in place. */
    private void finish() {
        if (!Phase.ncpKeepTimer.get())
            restoreTimer();

        enterCooldown();
    }

    private void abort() {
        restoreTimer();
        enterCooldown();
    }

    private void enterCooldown() {
        direction = Vec3.ZERO;
        steppingTicks = 0;
        cooldownTicks = Phase.ncpCooldown.get();
        state = State.COOLDOWN;
    }

    /** Restores the timer whenever this mode slowed it, including on disable and world change. */
    private void restoreTimer() {
        if (!timerSlowed)
            return;

        TimerUtil.reset();
        timerSlowed = false;
    }

    private void reset() {
        restoreTimer();
        direction = Vec3.ZERO;
        steppingTicks = 0;
        cooldownTicks = 0;
        state = State.IDLE;
    }
}
