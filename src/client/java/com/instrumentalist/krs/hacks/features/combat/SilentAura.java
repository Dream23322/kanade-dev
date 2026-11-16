package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.PerspectiveUtil;
import com.instrumentalist.krs.utils.entity.CombatTargets;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.math.RandomUtil;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.render.PathLines;
import com.instrumentalist.krs.utils.render.TargetBoxRenderer;
import com.instrumentalist.krs.utils.rotation.HumanAim;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Tachibana-style Silent Aura.
 *
 * <p>This module deliberately does not use the silent server-rotation pipeline.
 * It moves the player's real yaw and pitch with a humanised aim curve while a
 * crosshair triggerbot fires at the target. Freelook detaches the camera from
 * the body during combat so the aim remains visually silent while fighting.</p>
 */
public class SilentAura extends Module {
    private static final Object TARGET_LINE = new Object();
    private static final int DEFAULT_SWITCH_COOLDOWN = 10;

    private static boolean freeLooking;
    private static boolean cameraAnchored;
    private static float cameraYaw;
    private static float cameraPitch;
    private static float cameraAnchorYaw;

    @Setting
    private final ListValue page = new ListValue(
            "Page",
            new String[]{"Aim", "Trigger", "FreeLook", "Render"},
            "Aim"
    ).describe("Silent Aura settings page.");

    // ===================== AIM =====================

    @Setting
    private final FloatValue speed = new FloatValue("Aim Speed", 1.5f, 1f, 10f, "", () -> isPage("Aim"))
            .describe("How fast the aim pulls toward the target.");

    @Setting
    private final FloatValue speedRandom = new FloatValue("Speed Random", 25f, 0f, 50f, "%", () -> isPage("Aim"))
            .describe("Per-frame variation in aim speed.");

    @Setting
    private final FloatValue fov = new FloatValue("Aim FOV", 180f, 5f, 360f, "deg", () -> isPage("Aim"))
            .describe("Only acquire targets within this many degrees of your view.");

    @Setting
    private final FloatValue range = new FloatValue("Range", 6f, 1f, 12f, "m", () -> isPage("Aim"))
            .describe("Maximum distance used to acquire a target.");

    @Setting
    private final FloatValue noise = new FloatValue("Aim Noise", 0.3f, 0f, 1f, "", () -> isPage("Aim"))
            .describe("Small human jitter added to the aim path.");

    @Setting
    private final ListValue aimPoint = new ListValue(
            "Aim Point",
            new String[]{"Head", "Chest", "Feet", "Best"},
            "Best",
            () -> isPage("Aim")
    ).describe("Point on the target used for the camera aim.");

    @Setting
    private final FloatValue aimMargin = new FloatValue("Aim Margin", 0.1f, 0f, 0.5f, "",
            () -> isPage("Aim") && isAimPoint("Best"))
            .describe("Padding from the hitbox edge when using Best aim point.");

    @Setting
    private final FloatValue spread = new FloatValue("Aim Spread", 0.3f, 0f, 1f, "", () -> isPage("Aim"))
            .describe("Deterministic random offset added to the aim point.");

    @Setting
    private final BooleanValue weaponOnly = new BooleanValue("Weapon Only", false, () -> isPage("Aim"))
            .describe("Only aim while holding a sword or axe.");

    @Setting
    private final FloatValue prediction = new FloatValue("Prediction", 0.5f, 0f, 1f, "", () -> isPage("Aim"))
            .describe("Ticks of target movement to lead.");

    @Setting
    private final BooleanValue reaction = new BooleanValue("Reaction", false, () -> isPage("Aim"))
            .describe("Add a reaction delay when switching targets.");

    @Setting
    private final IntValue reactMin = new IntValue("React Min", 3, 2, 10, "t",
            () -> isPage("Aim") && reaction.get())
            .describe("Minimum reaction delay in ticks.");

    @Setting
    private final IntValue reactMax = new IntValue("React Max", 6, 2, 10, "t",
            () -> isPage("Aim") && reaction.get())
            .describe("Maximum reaction delay in ticks.");

    @Setting
    private final BooleanValue fitts = new BooleanValue("Fitts Law", true, () -> isPage("Aim"))
            .describe("Scale aim speed by target distance and size.");

    @Setting
    private final FloatValue wristArc = new FloatValue("Wrist Arc", 0.8f, 0f, 1f, "", () -> isPage("Aim"))
            .describe("Simulates a natural wrist-pivot arc.");

    @Setting
    private final FloatValue gcdAcc = new FloatValue("GCD Acc", 1f, 0.5f, 1f, "", () -> isPage("Aim"))
            .describe("How strictly aim deltas snap to the mouse grid.");

    @Setting
    private final BooleanValue saccade = new BooleanValue("Saccade", true, () -> isPage("Aim"))
            .describe("Micro-tremors while tracking a target.");

    @Setting
    private final BooleanValue gcdFix = new BooleanValue("GCD Fix", false, () -> isPage("Aim"))
            .describe("Slow the aim while the attack cooldown is low.");

    @Setting
    private final BooleanValue farAim = new BooleanValue("Far Aim", false, () -> isPage("Aim"))
            .describe("Aim off-centre on distant targets to avoid a locked-on look.");

    @Setting
    private final FloatValue farAimRange = new FloatValue("Far Aim At", 4f, 3.5f, 16f, "m",
            () -> isPage("Aim") && farAim.get())
            .describe("Start offsetting when the target is farther than this.");

    @Setting
    private final FloatValue farAimOffset = new FloatValue("Far Aim Offset", 0.5f, 0.1f, 3f, "m",
            () -> isPage("Aim") && farAim.get())
            .describe("How far off-centre to aim on distant targets.");

    @Setting
    private final BooleanValue throughWalls = new BooleanValue("Through Walls", true, () -> isPage("Aim"))
            .describe("Acquire targets without a clear line of sight.");

    @Setting
    private final BooleanValue onlyPlayers = new BooleanValue("Only Players", true, () -> isPage("Aim"))
            .describe("Only target players.");

    // ===================== TRIGGER =====================

    @Setting
    private final BooleanValue pauseOnManual = new BooleanValue("Pause On Manual", true, () -> isPage("Trigger"))
            .describe("Stop aiming and triggering while the attack key is held.");

    @Setting
    private final IntValue triggerMinDelay = new IntValue("Min Delay", 1, 0, 9, "t", () -> isPage("Trigger"))
            .describe("Minimum ticks between trigger attacks.");

    @Setting
    private final IntValue triggerMaxDelay = new IntValue("Max Delay", 3, 0, 9, "t", () -> isPage("Trigger"))
            .describe("Maximum ticks between trigger attacks.");

    @Setting
    private final BooleanValue triggerGcd = new BooleanValue("GCD Gate", true, () -> isPage("Trigger"))
            .describe("Only fire when the attack cooldown is above 92%.");

    @Setting
    private final BooleanValue triggerCrits = new BooleanValue("Crits", true, () -> isPage("Trigger"))
            .describe("Wait for a falling critical hit while airborne.");

    @Setting
    private final FloatValue triggerMinFall = new FloatValue("Min Fall", 0.001f, 0.001f, 0.5f, "m",
            () -> isPage("Trigger") && triggerCrits.get())
            .describe("Minimum fall distance for a critical hit.");

    @Setting
    private final IntValue triggerCritStick = new IntValue("Crits Short Tick", 3, 0, 10, "t",
            () -> isPage("Trigger") && triggerCrits.get())
            .describe("Attack early when only a short time is left in the air.");

    @Setting
    private final BooleanValue triggerAllowAir = new BooleanValue("Allow Air", true, () -> isPage("Trigger"))
            .describe("Allow attacks while airborne.");

    @Setting
    private final BooleanValue triggerWeaponOnly = new BooleanValue("Require Weapon", true, () -> isPage("Trigger"))
            .describe("Only trigger while holding a sword or axe.");

    @Setting
    private final FloatValue triggerRange = new FloatValue("Trigger Range", 4f, 2f, 6f, "m", () -> isPage("Trigger"))
            .describe("Maximum distance at which the trigger may attack.");

    @Setting
    private final BooleanValue respectShield = new BooleanValue("Respect Shield", true, () -> isPage("Trigger"))
            .describe("Do not hit enemies blocking with a shield; react after they lower it.");

    @Setting
    private final BooleanValue respectOwnShield = new BooleanValue("Respect Own Shield", true, () -> isPage("Trigger"))
            .describe("Do not attack while blocking with your own shield.");

    @Setting
    private final BooleanValue respectItemUse = new BooleanValue("Respect Item Use", true, () -> isPage("Trigger"))
            .describe("Do not attack while eating, drinking, blocking, bowing or throwing.");

    @Setting
    private final ListValue itemUseMode = new ListValue(
            "Detect",
            new String[]{"Using", "Using + Key", "Throwable"},
            "Using",
            () -> isPage("Trigger") && respectItemUse.get()
    ).describe("How item use is detected before the trigger pauses.");

    @Setting
    private final IntValue shieldReactMin = new IntValue("Shield React Min", 3, 2, 10, "t",
            () -> isPage("Trigger") && respectShield.get())
            .describe("Minimum reaction ticks after the enemy stops shielding.");

    @Setting
    private final IntValue shieldReactMax = new IntValue("Shield React Max", 8, 2, 15, "t",
            () -> isPage("Trigger") && respectShield.get())
            .describe("Maximum reaction ticks after the enemy stops shielding.");

    // ===================== FREELOOK =====================

    @Setting
    private final FloatValue freeLookYawLimit = new FloatValue("Yaw Limit", 0f, 0f, 180f, "deg",
            () -> isPage("FreeLook"))
            .describe("Maximum horizontal camera offset. 0 is unlimited.");

    @Setting
    private final FloatValue freeLookPitchLimit = new FloatValue("Pitch Limit", 90f, 0f, 90f, "deg",
            () -> isPage("FreeLook"))
            .describe("Maximum vertical camera offset.");

    @Setting
    private final BooleanValue freeLookAutoF5 = new BooleanValue("Auto F5", true, () -> isPage("FreeLook"))
            .describe("Switch to third person while combat freelook is active.");

    @Setting
    private final IntValue freeLookCooldown = new IntValue("Combat Cooldown", 40, 1, 200, "t",
            () -> isPage("FreeLook"))
            .describe("Ticks freelook stays active after the last combat action.");

    // ===================== RENDER =====================

    @Setting
    private final BooleanValue targetDisplay = new BooleanValue("Target Display", true, () -> isPage("Render"))
            .describe("Draw an indicator around the current target.");

    @Setting
    private final ListValue targetStyle = new ListValue(
            "Style",
            new String[]{"Full", "Box", "Line"},
            "Full",
            () -> isPage("Render") && targetDisplay.get()
    ).describe("Shape of the target indicator.");

    @Setting
    private final BooleanValue targetAimLine = new BooleanValue("Aim Line", true,
            () -> isPage("Render") && targetDisplay.get() && !isTargetStyle("Box"))
            .describe("Draw the line from your eyes to the aim point.");

    @Setting
    private final ListValue targetColor = new ListValue(
            "Color",
            new String[]{"Red", "Green", "Blue", "Yellow", "Purple", "Cyan", "White"},
            "Red",
            () -> isPage("Render") && targetDisplay.get()
    ).describe("Color of the target indicator.");

    private final HumanAim.Engagement engagement = new HumanAim.Engagement();
    private final Random spreadRandom = new Random();
    private final HumanAim.Settings aimSettings = new HumanAim.Settings() {
        @Override
        public float speed() {
            return speed.get();
        }

        @Override
        public float speedRandom() {
            return speedRandom.get();
        }

        @Override
        public boolean reaction() {
            return reaction.get();
        }

        @Override
        public int reactMin() {
            return reactMin.get();
        }

        @Override
        public int reactMax() {
            return reactMax.get();
        }

        @Override
        public boolean fitts() {
            return fitts.get();
        }

        @Override
        public float arc() {
            return wristArc.get();
        }

        @Override
        public float noise() {
            return noise.get();
        }

        @Override
        public boolean saccade() {
            return saccade.get();
        }

        @Override
        public float gcdAcc() {
            return gcdAcc.get();
        }

        @Override
        public boolean gcdEnabled() {
            return gcdFix.get();
        }
    };

    private LivingEntity currentTarget;
    private LivingEntity renderTarget;
    private int switchCooldown;
    private int lastSwitchTick = -1;
    private int preAttackTicks = -1;
    private int ticksInAir;
    private int waitingShieldTarget = -1;
    private int shieldReactTicks;
    private int combatTicks = Integer.MAX_VALUE;

    @Override
    public String description() {
        return "Camera aim assist, triggerbot and combat freelook";
    }

    public SilentAura() {
        super("Silent Aura", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return "Tachibana";
    }

    @Override
    public void onEnable() {
        resetCombatState();
        combatTicks = Integer.MAX_VALUE;
        freeLooking = false;
        cameraAnchored = false;
        PerspectiveUtil.release(this);
        PathLines.hide(this, TARGET_LINE);
    }

    @Override
    public void onDisable() {
        resetCombatState();
        combatTicks = Integer.MAX_VALUE;
        freeLooking = false;
        cameraAnchored = false;
        PerspectiveUtil.release(this);
        PathLines.hide(this, TARGET_LINE);
    }

    @Override
    public void onWorld(WorldEvent event) {
        releaseTarget();
        resetFreeLook();
        PathLines.hide(this, TARGET_LINE);
    }

    @Override
    public void onTick(TickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            releaseTarget();
            resetFreeLook();
            return;
        }

        if (combatTicks < Integer.MAX_VALUE)
            combatTicks++;

        updateFreeLook(player);
        updateTrigger(player);
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null)
            return;

        LivingEntity target = aimAt(player);
        if (target == null && targetDisplay.get())
            target = findTarget(player);
        renderTarget = target;

        if (targetDisplay.get() && target != null && targetAimLine.get() && !isTargetStyle("Box")) {
            Vec3 aimPoint = getAimPoint(target, player);
            PathLines.show(this, TARGET_LINE, player.getEyePosition(), List.of(aimPoint), targetColorValue());
        } else {
            PathLines.hide(this, TARGET_LINE);
        }
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        LivingEntity target = renderTarget;
        if (!targetDisplay.get()
                || target == null
                || target.isRemoved()
                || !target.isAlive()
                || isTargetStyle("Line")
                || Client.nanoVgManager == null
                || mc.gui == null
                || mc.gui.hud.isHidden())
            return;

        Vec3 position = com.instrumentalist.krs.utils.render.RenderUtil.INSTANCE.getLerpedPos(target, event.tickDelta);
        Color color = targetColorValue();
        Client.nanoVgManager.loadWorldAnchored(vg -> TargetBoxRenderer.render(target, position, vg, true, color));
    }

    // ===================== AIM =====================

    private LivingEntity aimAt(LocalPlayer player) {
        if (mc.gui.screen() != null
                || player.isSpectator()
                || moduleConflict()
                || (pauseOnManual.get() && mc.options.keyAttack.isDown())
                || (weaponOnly.get() && !hasWeapon(player))
                || (gcdFix.get() && player.getAttackStrengthScale(0f) < 0.5f))
            return null;

        LivingEntity target = findTarget(player);
        if (target == null) {
            engagement.residualYaw = 0f;
            engagement.residualPitch = 0f;
            return null;
        }

        HumanAim.resetEngagement(engagement, target, aimSettings);
        HumanAim.driftEngagement(engagement);
        HumanAim.updatePrediction(engagement, target.getEyePosition());

        Vec3 eye = player.getEyePosition();
        Vec3 aimPosition = HumanAim.predictedAimPosition(
                getAimPoint(target, player),
                engagement.targetVelocity,
                prediction.get()
        );

        if (farAim.get() && eye.distanceTo(target.getEyePosition()) > farAimRange.get()) {
            double offset = farAimOffset.get();
            boolean right = (target.getId() & 1) == 0;
            aimPosition = aimPosition.add(right ? offset : -offset, 0d, 0d);
        }

        float targetYaw = HumanAim.yawTo(eye, aimPosition);
        float targetPitch = HumanAim.pitchTo(eye, aimPosition);
        double blockDistance = eye.distanceTo(target.getEyePosition());
        double angularWidth = Math.toDegrees(Math.atan(target.getBbWidth() / Math.max(blockDistance, 0.1d)));

        float[] aim = HumanAim.computeDelta(
                engagement,
                player,
                targetYaw,
                targetPitch,
                blockDistance,
                angularWidth,
                speed.get(),
                aimSettings
        );

        if (aim[0] != 0f || aim[1] != 0f) {
            float oldYaw = player.getYRot();
            float oldPitch = player.getXRot();
            float newYaw = HumanAim.snapYaw(Mth.wrapDegrees(oldYaw + aim[0]));
            float newPitch = Mth.clamp(HumanAim.snapPitch(oldPitch + aim[1]), -89.5f, 89.5f);

            player.setYRot(newYaw);
            player.setXRot(newPitch);
            player.yRotO += Mth.wrapDegrees(newYaw - oldYaw);
            player.xRotO += newPitch - oldPitch;
        }

        combatTicks = 0;
        return target;
    }

    private LivingEntity findTarget(LocalPlayer player) {
        LivingEntity best = findBestTarget(player);

        if (currentTarget == null) {
            setTarget(best);
            return currentTarget;
        }

        if (!isValidTarget(currentTarget, player, true)) {
            currentTarget = null;
            setTarget(best);
            return currentTarget;
        }

        if (best == currentTarget) {
            switchCooldown = DEFAULT_SWITCH_COOLDOWN;
            return currentTarget;
        }

        double holdRange = range.get() * 2.0d;
        if (best != null && player.distanceTo(currentTarget) > holdRange) {
            setTarget(best);
            return currentTarget;
        }

        tickSwitchCooldown(player);
        if (best != null && switchCooldown <= 0) {
            setTarget(best);
            return currentTarget;
        }

        if (best == null && switchCooldown <= 0) {
            currentTarget = null;
            return null;
        }

        return currentTarget;
    }

    private LivingEntity findBestTarget(LocalPlayer player) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        float maxAngle = fov.get() / 2f;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)
                    || !CombatTargets.isEnemy(entity, onlyPlayers.get()))
                continue;

            double distance = player.distanceTo(living);
            if (distance > range.get() || distance >= bestDistance)
                continue;

            if (maxAngle < 180f && angleTo(player, living) > maxAngle)
                continue;

            if (!throughWalls.get() && !player.hasLineOfSight(living))
                continue;

            bestDistance = distance;
            best = living;
        }

        return best;
    }

    private void setTarget(LivingEntity target) {
        if (currentTarget != target)
            engagement.targetId = -1;

        currentTarget = target;
        switchCooldown = DEFAULT_SWITCH_COOLDOWN;
    }

    private void tickSwitchCooldown(LocalPlayer player) {
        if (lastSwitchTick == player.tickCount)
            return;

        lastSwitchTick = player.tickCount;
        if (switchCooldown > 0)
            switchCooldown--;
    }

    private boolean isValidTarget(LivingEntity target, LocalPlayer player, boolean useHoldRange) {
        if (target == null
                || target.isRemoved()
                || !target.isAlive()
                || !CombatTargets.isEnemy(target, onlyPlayers.get()))
            return false;

        double limit = range.get() * (useHoldRange ? 2.0d : 1.0d);
        return player.distanceTo(target) <= limit;
    }

    private float angleTo(LocalPlayer player, LivingEntity target) {
        Vec3 look = player.getLookAngle();
        Vec3 direction = target.getEyePosition().subtract(player.getEyePosition());
        if (direction.lengthSqr() < 1.0E-6d)
            return 0f;

        double dot = look.normalize().dot(direction.normalize());
        return (float) Math.toDegrees(Math.acos(Mth.clamp(dot, -1d, 1d)));
    }

    private Vec3 getAimPoint(LivingEntity target, LocalPlayer player) {
        var box = target.getBoundingBox();
        double spreadAmount = spread.get();
        double offsetX = 0d;
        double offsetZ = 0d;

        if (spreadAmount > 0d) {
            spreadRandom.setSeed(target.getId() * 31L + player.tickCount / 5L);
            offsetX = (spreadRandom.nextDouble() - 0.5d) * spreadAmount * target.getBbWidth();
            offsetZ = (spreadRandom.nextDouble() - 0.5d) * spreadAmount * target.getBbWidth();
        }

        double aimY;
        if (isAimPoint("Best")) {
            double margin = aimMargin.get();
            double bottom = box.minY + margin;
            double top = box.maxY - margin;
            if (top <= bottom) {
                bottom = box.minY + 0.05d;
                top = box.maxY - 0.05d;
            }

            double ratio = Mth.clamp((player.getEyeY() - bottom) / Math.max(1.0E-4d, top - bottom), 0d, 1d);
            aimY = bottom + ratio * (top - bottom);
        } else if (isAimPoint("Head")) {
            aimY = target.getEyePosition().y;
        } else if (isAimPoint("Chest")) {
            aimY = box.minY + (box.maxY - box.minY) * 0.75d;
        } else {
            aimY = box.minY + 0.1d;
        }

        return new Vec3(target.getX() + offsetX, aimY, target.getZ() + offsetZ);
    }

    // ===================== TRIGGER =====================

    private void updateTrigger(LocalPlayer player) {
        if (mc.gui.screen() != null
                || player.isSpectator()
                || mc.gameMode.getPlayerMode() == GameType.SPECTATOR
                || moduleConflict()
                || (pauseOnManual.get() && mc.options.keyAttack.isDown()))
            return;

        if (player.onGround())
            ticksInAir = 0;
        else
            ticksInAir++;

        if (triggerWeaponOnly.get() && !hasWeapon(player))
            return;
        if (respectOwnShield.get() && isBlockingWithShield(player))
            return;
        if (isUserBusy(player))
            return;

        if (triggerGcd.get() && player.getAttackStrengthScale(0f) < 0.92f)
            return;

        if (preAttackTicks < 0)
            preAttackTicks = randomRangeSafe(triggerMinDelay.get(), triggerMaxDelay.get());
        if (preAttackTicks > 0) {
            preAttackTicks--;
            return;
        }

        if (!triggerAllowAir.get() && !player.onGround())
            return;
        if (triggerCrits.get() && !player.onGround() && !shouldCrit(player))
            return;

        LivingEntity target = crosshairTarget();
        if (target == null) {
            waitingShieldTarget = -1;
            shieldReactTicks = 0;
            return;
        }

        if (!CombatTargets.isEnemy(target, onlyPlayers.get())
                || player.distanceTo(target) > triggerRange.get())
            return;

        if (isBlockingWithShield(target)) {
            if (respectShield.get()) {
                if (waitingShieldTarget != target.getId()) {
                    waitingShieldTarget = target.getId();
                    shieldReactTicks = randomRangeSafe(shieldReactMin.get(), shieldReactMax.get());
                }
                return;
            }
        } else if (waitingShieldTarget == target.getId()) {
            if (shieldReactTicks > 0) {
                shieldReactTicks--;
                return;
            }
            waitingShieldTarget = -1;
        }

        attack(target, player);
    }

    private LivingEntity crosshairTarget() {
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.ENTITY)
            return null;

        Entity entity = ((EntityHitResult) hit).getEntity();
        return entity instanceof LivingEntity living ? living : null;
    }

    private void attack(LivingEntity target, LocalPlayer player) {
        if (mc.gameMode == null || target == null || !target.isAlive())
            return;

        mc.gameMode.attack(player, target);
        PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
        preAttackTicks = -1;
        combatTicks = 0;
        waitingShieldTarget = -1;
        shieldReactTicks = 0;
    }

    private boolean shouldCrit(LocalPlayer player) {
        return player.fallDistance > triggerMinFall.get()
                || ticksInAir < triggerCritStick.get();
    }

    private boolean isUserBusy(LocalPlayer player) {
        if (!respectItemUse.get())
            return false;
        if (player.isUsingItem())
            return true;

        int mode = itemUseMode.getCurrentIndex();
        if (mode >= 1 && mc.options.keyUse.isDown())
            return true;
        if (mode >= 2) {
            ItemStack main = player.getMainHandItem();
            return main.is(Items.ENDER_PEARL) || main.is(Items.SNOWBALL) || main.is(Items.EGG);
        }

        return false;
    }

    private boolean isBlockingWithShield(LivingEntity entity) {
        if (entity == null || !entity.isUsingItem())
            return false;

        ItemStack useItem = entity.getUseItem();
        if (!useItem.has(DataComponents.BLOCKS_ATTACKS))
            return false;

        if (entity == mc.player)
            return true;

        if (mc.player == null)
            return false;

        Vec3 toPlayer = mc.player.position().subtract(entity.position());
        if (toPlayer.lengthSqr() < 1.0E-6d)
            return true;

        return entity.getLookAngle().normalize().dot(toPlayer.normalize()) > 0.1d;
    }

    private boolean hasWeapon(LocalPlayer player) {
        ItemStack stack = player.getMainHandItem();
        return !stack.isEmpty()
                && (ToolUtil.INSTANCE.isSword(stack)
                || ToolUtil.INSTANCE.getToolCategory(stack.getItem()) == ToolUtil.TOOL_AXE);
    }

    private boolean moduleConflict() {
        return BehaviorUtils.noKillAura || ModuleManager.getModuleState(KillAura.class);
    }

    private int randomRangeSafe(int first, int second) {
        int min = Math.min(first, second);
        int max = Math.max(first, second);
        return min == max ? min : RandomUtil.nextInt(min, max + 1);
    }

    // ===================== FREELOOK =====================

    private void updateFreeLook(LocalPlayer player) {
        boolean active = tempEnabled && combatTicks < freeLookCooldown.get();
        if (active != freeLooking) {
            freeLooking = active;
            if (active) {
                cameraYaw = player.getYRot();
                cameraPitch = player.getXRot();
                cameraAnchorYaw = player.getYRot();
                cameraAnchored = true;
            } else {
                cameraAnchored = false;
            }
        }

        PerspectiveUtil.update(this, freeLookAutoF5.get() && freeLooking);
    }

    private static void resetFreeLook() {
        freeLooking = false;
        cameraAnchored = false;
        cameraYaw = 0f;
        cameraPitch = 0f;
        cameraAnchorYaw = 0f;
    }

    public static boolean isFreeLooking() {
        return freeLooking;
    }

    public static float getCameraYaw() {
        if (!freeLooking || mc.player == null)
            return mc.player != null ? mc.player.getYRot() : 0f;
        return cameraAnchored ? cameraYaw : mc.player.getYRot();
    }

    public static float getCameraPitch() {
        if (!freeLooking || mc.player == null)
            return mc.player != null ? mc.player.getXRot() : 0f;
        return cameraAnchored ? cameraPitch : mc.player.getXRot();
    }

    public static void turn(double deltaYaw, double deltaPitch) {
        LocalPlayer player = mc.player;
        if (!freeLooking || player == null)
            return;

        if (!cameraAnchored) {
            cameraYaw = player.getYRot();
            cameraPitch = player.getXRot();
            cameraAnchorYaw = player.getYRot();
            cameraAnchored = true;
        }

        SilentAura module = ModuleManager.getModule(SilentAura.class);
        float yawLimit = module != null ? module.freeLookYawLimit.get() : 0f;
        float pitchLimit = module != null ? module.freeLookPitchLimit.get() : 90f;

        cameraPitch = Mth.clamp(cameraPitch + (float) deltaPitch * 0.15f, -90f, 90f);
        float yawDelta = (float) deltaYaw * 0.15f;
        if (yawLimit <= 0f || yawLimit >= 360f)
            cameraYaw = Mth.wrapDegrees(cameraYaw + yawDelta);
        else
            cameraYaw = Mth.clamp(cameraYaw + yawDelta, cameraAnchorYaw - yawLimit, cameraAnchorYaw + yawLimit);
    }

    // ===================== RENDER HELPERS =====================

    private Color targetColorValue() {
        return switch (targetColor.get().toLowerCase(Locale.ROOT)) {
            case "green" -> new Color(80, 255, 80);
            case "blue" -> new Color(80, 80, 255);
            case "yellow" -> new Color(255, 255, 80);
            case "purple" -> new Color(255, 80, 255);
            case "cyan" -> new Color(80, 255, 255);
            case "white" -> Color.WHITE;
            default -> new Color(255, 80, 80);
        };
    }

    // ===================== STATE =====================

    private boolean isPage(String value) {
        return page.get().equalsIgnoreCase(value);
    }

    private boolean isAimPoint(String value) {
        return aimPoint.get().equalsIgnoreCase(value);
    }

    private boolean isTargetStyle(String value) {
        return targetStyle.get().equalsIgnoreCase(value);
    }

    public LivingEntity getCurrentTarget() {
        return renderTarget;
    }

    private void releaseTarget() {
        currentTarget = null;
        renderTarget = null;
        switchCooldown = 0;
        preAttackTicks = -1;
        waitingShieldTarget = -1;
        shieldReactTicks = 0;
        engagement.targetId = -1;
    }

    private void resetCombatState() {
        releaseTarget();
        ticksInAir = 0;
        lastSwitchTick = -1;
    }
}
