package com.instrumentalist.krs.hacks.features.player.phase.features;

import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.features.player.phase.PhaseCollision;
import com.instrumentalist.krs.hacks.features.player.phase.PhaseEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class VanillaPhase implements PhaseEvent {
    private static final double SERVER_COLLISION_EPSILON = 1.0E-5;
    private static final double ENTRY_FRACTION = 0.75;
    private static final double CONTACT_SEARCH_DISTANCE = 0.25;
    private static final int CONTACT_SEARCH_ITERATIONS = 24;
    private static final double SCAN_STEP = 0.03125;
    private static final double MAX_DISTANCE = 8.0;
    private static final int PENDING_TICKS = 10;
    private static final int CORRECTION_COOLDOWN_TICKS = 5;

    private boolean clipping = false;
    private int pendingTicks = 0;
    private int correctionCooldown = 0;

    @Override
    public String getName() {
        return "Vanilla";
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isPassenger() || player.isSpectator()) {
            reset();
            return;
        }

        if (correctionCooldown > 0) {
            correctionCooldown--;
            return;
        }

        if (clipping) {
            if (--pendingTicks <= 0)
                reset();
            return;
        }

        if (!player.horizontalCollision || !MovementUtil.isMoving())
            return;

        Vec3 movementDirection = MovementUtil.getMovementVector();
        if (movementDirection.horizontalDistanceSqr() == 0.0)
            return;

        Path path = findPath(player, movementDirection);
        if (path == null)
            return;

        boolean onGround = player.onGround();
        PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(
                path.boundaryPosition.x,
                path.boundaryPosition.y,
                path.boundaryPosition.z,
                onGround,
                player.horizontalCollision
        ));
        PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(
                path.entryPosition.x,
                path.entryPosition.y,
                path.entryPosition.z,
                onGround,
                true
        ));
        PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(
                path.exitPosition.x,
                path.exitPosition.y,
                path.exitPosition.z,
                onGround,
                false
        ));

        MovementUtil.stopMoving();
        player.setPos(path.exitPosition.x, path.exitPosition.y, path.exitPosition.z);
        clipping = true;
        pendingTicks = PENDING_TICKS;
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (clipping && event.packet instanceof ClientboundPlayerPositionPacket) {
            reset();
            correctionCooldown = CORRECTION_COOLDOWN_TICKS;
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

    private Path findPath(LocalPlayer player, Vec3 movementDirection) {
        AABB playerBox = player.getBoundingBox();
        Vec3 playerPosition = player.position();

        if (PhaseCollision.intersects(player, playerBox)) {
            Vec3 exitPosition = findExit(player, playerBox, playerPosition, movementDirection, 0.0);
            return exitPosition == null ? null : new Path(playerPosition, playerPosition, exitPosition);
        }

        Boundary exactBoundary = findBoundary(player, playerBox, movementDirection);
        Boundary deflatedBoundary = findBoundary(
                player,
                playerBox.deflate(SERVER_COLLISION_EPSILON),
                movementDirection
        );
        if (exactBoundary == null || deflatedBoundary == null)
            return null;

        double entryWindow = deflatedBoundary.collidingDistance - exactBoundary.collidingDistance;
        if (entryWindow <= 0.0)
            return null;

        double entryDistance = exactBoundary.collidingDistance + entryWindow * ENTRY_FRACTION;
        AABB entryBox = moveBox(playerBox, movementDirection, entryDistance);

        if (!PhaseCollision.intersects(player, entryBox)
                || PhaseCollision.intersects(player, entryBox.deflate(SERVER_COLLISION_EPSILON)))
            return null;

        Vec3 exitPosition = findExit(
                player,
                playerBox,
                playerPosition,
                movementDirection,
                exactBoundary.collidingDistance
        );
        if (exitPosition == null)
            return null;

        Vec3 boundaryPosition = playerPosition.add(movementDirection.scale(exactBoundary.clearDistance));
        Vec3 entryPosition = playerPosition.add(movementDirection.scale(entryDistance));
        return new Path(boundaryPosition, entryPosition, exitPosition);
    }

    private Boundary findBoundary(LocalPlayer player, AABB playerBox, Vec3 movementDirection) {
        double clearDistance = 0.0;
        double collidingDistance = SCAN_STEP;

        while (collidingDistance <= CONTACT_SEARCH_DISTANCE
                && !PhaseCollision.intersects(player, moveBox(playerBox, movementDirection, collidingDistance))) {
            clearDistance = collidingDistance;
            collidingDistance += SCAN_STEP;
        }

        if (collidingDistance > CONTACT_SEARCH_DISTANCE)
            return null;

        for (int i = 0; i < CONTACT_SEARCH_ITERATIONS; i++) {
            double middle = (clearDistance + collidingDistance) * 0.5;
            if (PhaseCollision.intersects(player, moveBox(playerBox, movementDirection, middle)))
                collidingDistance = middle;
            else
                clearDistance = middle;
        }

        return new Boundary(clearDistance, collidingDistance);
    }

    private Vec3 findExit(LocalPlayer player, AABB playerBox, Vec3 playerPosition,
                          Vec3 movementDirection, double startDistance) {
        double firstDistance = Math.max(SCAN_STEP, startDistance + SCAN_STEP);
        for (double distance = firstDistance; distance <= MAX_DISTANCE; distance += SCAN_STEP) {
            AABB candidateBox = moveBox(playerBox, movementDirection, distance);
            if (!PhaseCollision.intersects(player, candidateBox))
                return playerPosition.add(movementDirection.scale(distance));
        }

        return null;
    }

    private AABB moveBox(AABB box, Vec3 direction, double distance) {
        return box.move(direction.x * distance, 0.0, direction.z * distance);
    }

    private void reset() {
        clipping = false;
        pendingTicks = 0;
        correctionCooldown = 0;
    }

    private record Boundary(double clearDistance, double collidingDistance) {
    }

    private record Path(Vec3 boundaryPosition, Vec3 entryPosition, Vec3 exitPosition) {
    }
}
