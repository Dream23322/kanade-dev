package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.utils.move.InputUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.player.ClientInput;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class Velocity extends Module {
    @Setting
    private final ListValue mode = new ListValue("Mode", new String[]{"Standard", "Hypixel NCP", "Jump Reset"}, "Standard")
            .describe("Method used to reduce knockback.");

    @Setting
    private final FloatValue reduce = new FloatValue("Reduce", 100f, 0f, 100f, "%", this::isStandardMode)
            .describe("How much of the knockback is removed in Standard mode.");

    @Setting
    private final BooleanValue onlyWhileFighting = new BooleanValue("Only While Fighting", false, this::isStandardMode)
            .describe("Only reduces knockback while a fight is in progress.");

    @Setting
    private final FloatValue jumpResetChance = new FloatValue("Jump Reset Chance", 100f, 0f, 100f, "%", this::isJumpResetMode)
            .describe("Chance to jump reset on each hit.");

    @Setting
    private final ListValue jumpResetCooldown = new ListValue("Jump Reset Cooldown", new String[]{"Delay", "Received Hits", "None"}, "Delay", this::isJumpResetMode)
            .describe("What has to happen before the next jump reset.");

    @Setting
    private final IntValue jumpResetTicksUntilJump = new IntValue("Jump Reset Until Jump", 2, 0, 20, "t", () -> isJumpResetMode() && jumpResetCooldown.get().equalsIgnoreCase("delay"))
            .describe("Ticks to wait before jumping.");

    @Setting
    private final IntValue jumpResetHitsUntilJump = new IntValue("Jump Reset Hits Until Jump", 2, 0, 10, "x", () -> isJumpResetMode() && jumpResetCooldown.get().equalsIgnoreCase("received hits"))
            .describe("Hits to take before jumping again.");

    private int jumpResetLimitUntilJump = 0;
    private boolean jumpResetFallDamage = false;

    @Override
    public String description() {
        return "Reduces or removes the knockback you take";
    }

    public Velocity() {
        super("Velocity", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onDisable() {
        resetJumpReset();
    }

    @Override
    public void onEnable() {
        resetJumpReset();
    }

    public static void hookJumpResetInput(ClientInput input) {
        Velocity velocity = ModuleManager.getModule(Velocity.class);
        if (velocity == null || !velocity.tempEnabled)
            return;

        velocity.handleJumpResetInput(input);
    }

    private void handleJumpResetInput(ClientInput input) {
        var player = mc.player;
        if (input == null || player == null || player.input != input || !isJumpResetMode()) return;

        if (player.hurtTime != 9
                || !player.onGround()
                || !player.isSprinting()
                || jumpResetFallDamage
                || !isJumpResetCooldownOver()
                || !rollJumpResetChance()) {
            updateJumpResetLimit();
            return;
        }

        InputUtil.setJumping(input, true);
        jumpResetLimitUntilJump = 0;
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (event.packet instanceof ClientboundSetEntityMotionPacket motionPacket && motionPacket.id() == player.getId()
                && shouldDropKnockback(motionPacket.movement()))
            event.cancel();
    }

    public static Optional<Vec3> hookExplosionKnockback(Optional<Vec3> knockback) {
        Velocity velocity = ModuleManager.getModule(Velocity.class);
        if (velocity == null || !velocity.tempEnabled || mc.player == null || knockback.isEmpty())
            return knockback;

        return velocity.shouldDropKnockback(knockback.get()) ? Optional.empty() : knockback;
    }

    private boolean shouldDropKnockback(Vec3 motion) {
        var player = mc.player;
        switch (mode.get().toLowerCase(Locale.ROOT)) {
            case "standard" -> {
                if (onlyWhileFighting.get() && !isFighting())
                    return false;

                double keep = 1.0D - Mth.clamp(reduce.get() / 100.0F, 0.0F, 1.0F);
                if (keep >= 1.0D)
                    return false;
                if (keep > 0.0D)
                    player.setDeltaMovement(player.getDeltaMovement().add(motion.scale(keep)));
                return true;
            }
            case "hypixel ncp" -> {
                if (ModuleManager.getModuleState(FlyModule.class))
                    return false;

                if (!ModuleManager.getModuleState(SpeedModule.class) || MovementUtil.fallTicks >= 10 || player.onGround())
                    MovementUtil.setVelocityY(motion.y);
                return true;
            }
            case "jump reset" -> jumpResetFallDamage = motion.x == 0.0 && motion.z == 0.0 && motion.y < 0.0;
        }

        return false;
    }

    private boolean isJumpResetMode() {
        return mode.get().equalsIgnoreCase("jump reset");
    }

    private boolean isStandardMode() {
        return mode.get().equalsIgnoreCase("standard");
    }

    private boolean isFighting() {
        var player = mc.player;
        if (player == null)
            return false;

        if (player.hurtTime > 0 || KillAura.closestEntity != null)
            return true;

        SilentAura silentAura = ModuleManager.getModule(SilentAura.class);
        return silentAura != null
                && ModuleManager.getModuleState(SilentAura.class)
                && silentAura.getCurrentTarget() != null;
    }

    private void resetJumpReset() {
        jumpResetLimitUntilJump = 0;
        jumpResetFallDamage = false;
    }

    private boolean rollJumpResetChance() {
        float chance = jumpResetChance.get();
        return chance >= 100f || chance > 0f && ThreadLocalRandom.current().nextFloat() * 100.0f < chance;
    }

    private boolean isJumpResetCooldownOver() {
        if (jumpResetCooldown.get().equalsIgnoreCase("received hits"))
            return jumpResetLimitUntilJump >= jumpResetHitsUntilJump.get();

        if (jumpResetCooldown.get().equalsIgnoreCase("delay"))
            return jumpResetLimitUntilJump >= jumpResetTicksUntilJump.get();

        return true;
    }

    private void updateJumpResetLimit() {
        var player = mc.player;
        if (player == null) return;

        if (jumpResetCooldown.get().equalsIgnoreCase("received hits")) {
            if (player.hurtTime == 9)
                jumpResetLimitUntilJump++;
            return;
        }

        if (jumpResetCooldown.get().equalsIgnoreCase("delay"))
            jumpResetLimitUntilJump++;
    }
}
