package com.instrumentalist.krs.hacks.features.movement.fly.features;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyEvent;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * Jumps and freezes about a block up until the server sets back, then flies freely for a while.
 */
public class FlyRewoundSetback implements FlyEvent {

    private enum Stage {
        JUMP, FREEZE, FLY
    }

    private Stage stage = Stage.JUMP;
    private long flightStart;

    @Override
    public String getName() {
        return "Rewound Setback";
    }

    public void reset() {
        stage = Stage.JUMP;
        flightStart = 0L;
    }

    public void disable() {
        MovementUtil.stopXZ();
        TimerUtil.reset();
        reset();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        // The fourth air tick starts at 0.42 + 0.33 + 0.25, about one block up.
        if (stage == Stage.JUMP && mc.player.onGround())
            mc.player.input.makeJump();
    }

    @Override
    public void onMotion(MotionEvent event) {
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        switch (stage) {
            case JUMP -> {
                if (MovementUtil.fallTicks >= 3)
                    stage = Stage.FREEZE;
            }
            case FREEZE -> {
            }
            case FLY -> {
                TimerUtil.timerSpeed = FlyModule.rewoundSetbackTimer.get();

                if (System.currentTimeMillis() - flightStart >= FlyModule.rewoundSetbackDuration.get()) {
                    FlyModule flyModule = ModuleManager.getModule(FlyModule.class);
                    if (flyModule != null)
                        flyModule.setState(false);
                }
            }
        }
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (mc.player == null || event.type != MoverType.SELF) return;

        switch (stage) {
            case JUMP -> {
            }
            case FREEZE -> {
                event.movement = Vec3.ZERO;
                mc.player.setDeltaMovement(Vec3.ZERO);
            }
            case FLY -> {
                Input keys = mc.player.input.keyPresses;
                double speed = FlyModule.rewoundSetbackSpeed.get();
                double y = keys.jump() && !keys.shift() ? speed : keys.shift() && !keys.jump() ? -speed : 0.0;
                Vec3 strafe = MovementUtil.getMovementVector(speed);
                Vec3 motion = new Vec3(strafe.x, y, strafe.z);

                event.movement = motion;
                mc.player.setDeltaMovement(motion);
            }
        }
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (stage == Stage.FREEZE && event.packet instanceof ServerboundMovePlayerPacket)
            event.cancel();
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (stage == Stage.FREEZE && event.packet instanceof ClientboundPlayerPositionPacket) {
            stage = Stage.FLY;
            flightStart = System.currentTimeMillis();
        }
    }

    @Override
    public void onBlock(BlockEvent event) {
    }
}
