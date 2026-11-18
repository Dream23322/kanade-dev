package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.PlayerMoveEvent;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.packet.BlinkUtil;
import com.instrumentalist.krs.utils.render.PathLines;
import com.instrumentalist.krs.utils.render.RenderColors;
import com.instrumentalist.krs.utils.render.RenderUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.instrumentalist.krs.utils.value.TextValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Port of OpenMyau-Plus Displace: while you attack a target the module alternates your silent
 * rotation by a fixed offset (or towards open space / void around the target) every other tick, so
 * your strafe is displaced around the fight.
 */
public class Displace extends Module {
    private static final int DISPLACE_WINDOW_TICKS = 10;
    private static final int VOID_SCAN_DIRECTIONS = 32;
    private static final int VOID_SCAN_RINGS = 12;
    private static final int VOID_SCAN_DEPTH = 10;
    private static final double VOID_SCAN_STEP = 0.5D;
    private static final double DYNAMIC_SCAN_STEP = 0.5D;
    private static final double DYNAMIC_SCAN_DISTANCE = 6.0D;
    private static final double DYNAMIC_SCAN_SIDE_STEP = 0.45D;
    private static final double DYNAMIC_WALL_CHECK_STEP = 0.25D;
    private static final double DYNAMIC_COLLISION_INSET = 0.03D;
    private static final double TARGET_RANGE = 9.0D;
    private static final long ARROW_FADE_MS = 250L;
    private static final double ARROW_FORWARD_GAP = 0.24D;
    private static final double ARROW_BODY_LENGTH = 0.74D;
    private static final double ARROW_HEAD_BACKSET = 0.18D;
    private static final double ARROW_HEAD_LENGTH = 0.52D;
    private static final double ARROW_HEAD_HALF_HEIGHT = 0.30D;
    private static final String ARROW_KEY = "displace-arrow";
    private static final String ARROW_HEAD_KEY = "displace-arrow-head";
    private static final double[] VOID_SCAN_X = new double[VOID_SCAN_DIRECTIONS];
    private static final double[] VOID_SCAN_Z = new double[VOID_SCAN_DIRECTIONS];

    static {
        for (int i = 0; i < VOID_SCAN_DIRECTIONS; i++) {
            double angle = Math.PI * 2.0D * i / VOID_SCAN_DIRECTIONS;
            VOID_SCAN_X[i] = Math.cos(angle);
            VOID_SCAN_Z[i] = Math.sin(angle);
        }
    }

    @Setting
    private final ListValue angleMode = new ListValue(
            "Angle",
            new String[]{"Static", "Dynamic"},
            "Static"
    ).describe("Static uses a fixed yaw offset, Dynamic scans for open space around the target.");

    @Setting
    private final FloatValue yawOffset = new FloatValue(
            "Yaw Offset",
            90f,
            0f,
            180f,
            "°",
            () -> !isDynamicAngle()
    ).describe("How far the silent rotation is turned away from your facing.");

    @Setting
    private final ListValue direction = new ListValue(
            "Direction",
            new String[]{"Left", "Right"},
            "Left",
            () -> !isDynamicAngle()
    ).describe("Side the rotation is offset to when void finding is off.");

    @Setting
    private final FloatValue delay = new FloatValue(
            "Delay",
            0f,
            0f,
            500f,
            "ms"
    ).describe("Hold between displacement windows.");

    @Setting
    private final BooleanValue showDirection = new BooleanValue(
            "Show Direction",
            true
    ).describe("Draws an arrow at the target showing the displacement direction.");

    @Setting
    private final BooleanValue findVoid = new BooleanValue(
            "Find Void",
            false,
            () -> !isDynamicAngle()
    ).describe("Turns the static offset towards the nearest void column around the target.");

    @Setting
    private final BooleanValue blink = new BooleanValue(
            "Blink",
            false
    ).describe("Holds the movement packet of a displacement tick until the next game tick.");

    @Setting
    private final BooleanValue ignoreTeammates = new BooleanValue(
            "Ignore Teammates",
            true
    ).describe("Does not displace around players on your own team.");

    @Setting
    private final BooleanValue hasKnockback = new BooleanValue(
            "Has Knockback",
            false
    ).describe("Only displaces while the held item has the Knockback enchantment.");

    @Setting
    private final BooleanValue itemWhitelist = new BooleanValue(
            "Item Whitelist",
            false
    ).describe("Only displaces while holding one of the whitelisted items.");

    @Setting
    private final TextValue whitelistedItems = new TextValue(
            "Whitelisted Items",
            "",
            itemWhitelist::get
    ).describe("Comma separated item ids, for example minecraft:iron_sword.");

    private final Map<Integer, Integer> targetWindowStartTicks = new HashMap<>();
    private final List<Vec3> arrowPath = new ArrayList<>(3);
    private final List<Vec3> arrowHeadPath = new ArrayList<>(3);

    private boolean displaceThisTick;
    private boolean active;
    private boolean displaceLeft;
    private boolean releaseBlinkNextGameTick;
    private boolean blinkingModule;
    private Float dynamicVoidYaw;
    private Float renderDisplaceYaw;
    private Player renderTarget;
    private Float fadingDisplaceYaw;
    private Player fadingTarget;
    private long arrowFadeStartMs;
    private Float lastRenderedDisplaceYaw;
    private Player lastRenderedTarget;
    private long lastRenderedArrowMs;
    private int tickCounter;

    @Override
    public String description() {
        return "Alternates your silent rotation while fighting to displace your strafe around the target";
    }

    @Override
    public String tag() {
        return Math.round(delay.get()) + "ms";
    }

    public Displace() {
        super("Displace", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        resetState();
        releaseBlink();
    }

    @Override
    public void onDisable() {
        resetState();
        releaseBlink();
        hideArrow();
    }

    @Override
    public void onWorld(WorldEvent event) {
        resetState();
        releaseBlink();
        hideArrow();
    }

    @Override
    public void onTick(TickEvent event) {
        if (!releaseBlinkNextGameTick)
            return;

        releaseBlink();
        releaseBlinkNextGameTick = false;
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            clearActiveState();
            return;
        }

        tickCounter++;
        int currentTick = tickCounter;
        pruneTargetDelayStates();

        if (!passesItemCondition(player)) {
            clearActiveState();
            return;
        }

        boolean attacking = mc.options.keyAttack.isDown()
                || ModuleManager.getModuleState(KillAura.class) && KillAura.closestEntity != null;
        Player target = attacking ? findTarget(player) : null;

        boolean hasKBEnchant = knockbackLevel(player.getMainHandItem()) > 0;
        active = target != null && (hasKBEnchant || anyMovementKey());
        if (!active) {
            clearActiveState();
            return;
        }

        dynamicVoidYaw = isDynamicAngle()
                ? findDynamicVoidYaw(target)
                : findVoid.get() ? findStaticVoidYaw(target) : null;
        if (dynamicVoidYaw == null && !isDynamicAngle())
            displaceLeft = direction.get().equalsIgnoreCase("Left");

        renderDisplaceYaw = dynamicVoidYaw != null
                ? dynamicVoidYaw
                : isDynamicAngle() ? null : getFixedDisplaceYaw(player);
        renderTarget = renderDisplaceYaw != null ? target : null;
        if (renderDisplaceYaw == null) {
            clearActiveState();
            return;
        }

        displaceThisTick = !displaceThisTick;
        if (displaceThisTick && !shouldDisplaceInCurrentWindow(target, currentTick)) {
            startArrowFade();
            displaceThisTick = false;
            dynamicVoidYaw = null;
            renderDisplaceYaw = null;
            renderTarget = null;
            return;
        }

    }

    /**
     * The displaced yaw is written straight into the outgoing position packets instead of going
     * through RotationManager: the manager keeps a rotation alive for 1000 ms, which would make the
     * server see one constant offset instead of an alternating one.
     */
    @Override
    public void onMotion(MotionEvent event) {
        if (event.post || !displaceThisTick || renderDisplaceYaw == null)
            return;

        event.yaw = renderDisplaceYaw;
    }

    /**
     * Movement is rebuilt from the raw movement keys relative to the displacement yaw, which is what
     * MoveUtil.fixMovement did in the original. Reading the keys instead of the ClientInput move
     * vector keeps this independent of Movement Fix: that module rewrites the move vector to its own
     * rotation, and consuming the rewritten vector here would apply the correction twice.
     */
    @Override
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!displaceThisTick || renderDisplaceYaw == null || event.movement == null)
            return;

        double forward = (mc.options.keyUp.isDown() ? 1.0D : 0.0D) - (mc.options.keyDown.isDown() ? 1.0D : 0.0D);
        double strafe = (mc.options.keyLeft.isDown() ? 1.0D : 0.0D) - (mc.options.keyRight.isDown() ? 1.0D : 0.0D);
        double inputLength = Math.sqrt(forward * forward + strafe * strafe);
        if (inputLength < 1.0E-4D)
            return;

        double magnitude = Math.sqrt(event.movement.x * event.movement.x + event.movement.z * event.movement.z);
        if (magnitude < 1.0E-4D)
            return;

        double normalForward = forward / inputLength;
        double normalStrafe = strafe / inputLength;

        float yawRad = (float) Math.toRadians(renderDisplaceYaw);
        float sin = Mth.sin(yawRad);
        float cos = Mth.cos(yawRad);

        // Vanilla builds the input vector as (strafe, 0, forward) and rotates it by the yaw.
        double moveX = normalStrafe * cos - normalForward * sin;
        double moveZ = normalForward * cos + normalStrafe * sin;

        event.movement = new Vec3(moveX * magnitude, event.movement.y, moveZ * magnitude);
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (!blink.get() || !active || !displaceThisTick || releaseBlinkNextGameTick)
            return;
        if (blinkingModule || BlinkUtil.INSTANCE.getBlinking())
            return;
        if (!(event.packet instanceof ServerboundMovePlayerPacket))
            return;

        BlinkUtil.INSTANCE.doBlink();
        blinkingModule = true;
        releaseBlinkNextGameTick = true;
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!showDirection.get()) {
            clearArrowState();
            hideArrow();
            return;
        }

        long nowMs = System.currentTimeMillis();
        boolean activeArrow = active && renderDisplaceYaw != null && renderTarget != null && !renderTarget.isDeadOrDying();

        Float arrowYaw;
        Player arrowTarget;
        float alpha = 1.0f;

        if (activeArrow) {
            clearFadingArrow();
            arrowYaw = renderDisplaceYaw;
            arrowTarget = renderTarget;
        } else {
            if (fadingDisplaceYaw == null || fadingTarget == null || fadingTarget.isDeadOrDying()) {
                clearFadingArrow();
                hideArrow();
                return;
            }

            long fadeElapsedMs = nowMs - arrowFadeStartMs;
            if (fadeElapsedMs >= ARROW_FADE_MS) {
                clearFadingArrow();
                hideArrow();
                return;
            }

            arrowYaw = fadingDisplaceYaw;
            arrowTarget = fadingTarget;
            alpha = 1.0f - (float) fadeElapsedMs / (float) ARROW_FADE_MS;
        }

        drawArrow(arrowTarget, arrowYaw, event.partialTicks, alpha);

        if (activeArrow) {
            lastRenderedDisplaceYaw = arrowYaw;
            lastRenderedTarget = arrowTarget;
            lastRenderedArrowMs = nowMs;
        }
    }

    private void drawArrow(Player target, float yaw, float partialTicks, float alpha) {
        Vec3 lerped = RenderUtil.INSTANCE.getLerpedPos(target, partialTicks);
        if (lerped == null)
            return;

        Vec3 center = lerped.add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        double yawRad = Math.toRadians(yaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double baseOffset = target.getBbWidth() * 0.5D + ARROW_FORWARD_GAP;

        Vec3 tail = new Vec3(
                center.x + forwardX * baseOffset,
                center.y,
                center.z + forwardZ * baseOffset
        );
        Vec3 bodyEnd = tail.add(forwardX * ARROW_BODY_LENGTH, 0.0D, forwardZ * ARROW_BODY_LENGTH);
        Vec3 headBack = tail.add(
                forwardX * (ARROW_BODY_LENGTH - ARROW_HEAD_BACKSET),
                0.0D,
                forwardZ * (ARROW_BODY_LENGTH - ARROW_HEAD_BACKSET)
        );
        Vec3 tip = bodyEnd.add(forwardX * ARROW_HEAD_LENGTH, 0.0D, forwardZ * ARROW_HEAD_LENGTH);

        Vec3 side = new Vec3(-forwardZ, 0.0D, forwardX).scale(ARROW_HEAD_HALF_HEIGHT);
        Color color = RenderColors.withAlpha(RenderColors.DISPLACE, Math.round(255.0f * alpha));

        arrowPath.clear();
        arrowPath.add(bodyEnd);
        arrowPath.add(tip);
        PathLines.show(this, ARROW_KEY, tail, arrowPath, color);

        arrowHeadPath.clear();
        arrowHeadPath.add(headBack.subtract(side));
        arrowHeadPath.add(tip);
        arrowHeadPath.add(headBack.add(side));
        PathLines.show(this, ARROW_HEAD_KEY, tail, arrowHeadPath, color);
    }

    private void hideArrow() {
        PathLines.hide(this, ARROW_KEY);
        PathLines.hide(this, ARROW_HEAD_KEY);
    }

    private boolean isDynamicAngle() {
        return angleMode.get().equalsIgnoreCase("Dynamic");
    }

    private boolean anyMovementKey() {
        return mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown();
    }

    private Player findTarget(LocalPlayer player) {
        Player best = null;
        double bestDistance = TARGET_RANGE * TARGET_RANGE;

        for (Player other : mc.level.players()) {
            if (other == player || other == player.getVehicle() || other == mc.getCameraEntity())
                continue;
            if (!other.isAlive() || other.isDeadOrDying() || other.isSpectator())
                continue;
            if (ignoreTeammates.get() && BehaviorUtils.isTeammate(other, true))
                continue;

            double distance = player.distanceToSqr(other);
            if (distance > bestDistance)
                continue;

            bestDistance = distance;
            best = other;
        }

        return best;
    }

    private boolean passesItemCondition(LocalPlayer player) {
        if (!hasKnockback.get() && !itemWhitelist.get())
            return true;

        // Both enabled conditions have to hold; the original ORed them, which made the item
        // whitelist a no-op whenever the knockback condition was off.
        boolean knockbackPass = !hasKnockback.get() || knockbackLevel(player.getMainHandItem()) > 0;
        boolean whitelistPass = !itemWhitelist.get() || isWhitelisted(player.getMainHandItem());
        return knockbackPass && whitelistPass;
    }

    private boolean isWhitelisted(ItemStack stack) {
        if (stack.isEmpty())
            return false;

        String configured = whitelistedItems.get();
        if (configured == null || configured.isBlank())
            return false;

        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();

        for (String entry : configured.split(",")) {
            String value = entry.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty())
                continue;
            if (value.equals(id) || value.equals(path))
                return true;
        }

        return false;
    }

    private static int knockbackLevel(ItemStack stack) {
        if (stack.isEmpty())
            return 0;

        for (var entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(Enchantments.KNOCKBACK))
                return entry.getIntValue();
        }

        return 0;
    }

    private float getFixedDisplaceYaw(LocalPlayer player) {
        float baseYaw = player.getYRot();
        float offset = yawOffset.get();
        return displaceLeft ? baseYaw - offset : baseYaw + offset;
    }

    // ==================== Void scanning ====================

    private Float findStaticVoidYaw(Player target) {
        LocalPlayer player = mc.player;
        if (target == null || player == null || mc.level == null)
            return null;

        double bestX = 0.0D;
        double bestZ = 0.0D;
        double bestScore = Double.MAX_VALUE;

        for (int ring = 1; ring <= VOID_SCAN_RINGS; ring++) {
            double radius = ring * VOID_SCAN_STEP;
            boolean foundInRing = false;

            for (int i = 0; i < VOID_SCAN_DIRECTIONS; i++) {
                double x = target.getX() + VOID_SCAN_X[i] * radius;
                double z = target.getZ() + VOID_SCAN_Z[i] * radius;
                if (!isVoidColumn(x, target.getY(), z))
                    continue;

                double playerDx = x - player.getX();
                double playerDz = z - player.getZ();
                double playerDistSq = playerDx * playerDx + playerDz * playerDz;
                double score = radius * radius * 1000.0D + playerDistSq;
                if (score < bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestZ = z;
                    foundInRing = true;
                }
            }

            if (foundInRing)
                break;
        }

        if (bestScore == Double.MAX_VALUE)
            return null;

        updateDisplaceSide(target, bestX, bestZ);

        double dx = bestX - target.getX();
        double dz = bestZ - target.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.001D)
            return null;

        double aimRadius = Math.min(dist, Math.max(0.35D, target.getBbWidth() * 0.5D + 0.15D));
        double aimX = target.getX() + dx / dist * aimRadius;
        double aimZ = target.getZ() + dz / dist * aimRadius;

        Vec3 eyes = player.getEyePosition();
        double aimDx = aimX - eyes.x;
        double aimDy = target.getY() + target.getBbHeight() * 0.5D - eyes.y;
        double aimDz = aimZ - eyes.z;
        return (float) Math.toDegrees(Math.atan2(aimDz, aimDx)) - 90.0f;
    }

    private Float findDynamicVoidYaw(Player target) {
        LocalPlayer player = mc.player;
        if (target == null || player == null || mc.level == null)
            return null;

        double bestForwardX = 0.0D;
        double bestForwardZ = 0.0D;
        double bestScore = 0.0D;

        for (int i = 0; i < VOID_SCAN_DIRECTIONS; i++) {
            double forwardX = VOID_SCAN_X[i];
            double forwardZ = VOID_SCAN_Z[i];
            double score = scoreVoidPath(target, forwardX, forwardZ);

            if (score > bestScore) {
                bestScore = score;
                bestForwardX = forwardX;
                bestForwardZ = forwardZ;
            }
        }

        if (bestScore <= 0.0D)
            return null;

        updateDisplaceSide(target, target.getX() + bestForwardX, target.getZ() + bestForwardZ);
        return yawFromForward(bestForwardX, bestForwardZ);
    }

    private static float yawFromForward(double forwardX, double forwardZ) {
        return (float) (Math.toDegrees(Math.atan2(forwardZ, forwardX)) - 90.0D);
    }

    private double scoreVoidPath(Player target, double forwardX, double forwardZ) {
        double sideX = -forwardZ;
        double sideZ = forwardX;
        double score = 0.0D;
        double checkedForward = 0.0D;
        int consecutiveCenterVoid = 0;

        AABB baseCollisionBox = target.getBoundingBox().deflate(DYNAMIC_COLLISION_INSET, 0.0D, DYNAMIC_COLLISION_INSET);

        for (int step = 1; step <= (int) (DYNAMIC_SCAN_DISTANCE / DYNAMIC_SCAN_STEP); step++) {
            double forward = step * DYNAMIC_SCAN_STEP;
            if (!isDynamicPathClear(target, baseCollisionBox, forwardX, forwardZ, checkedForward, forward))
                break;

            checkedForward = forward;
            boolean centerVoid = false;

            for (int lane = -1; lane <= 1; lane++) {
                double sideOffset = lane * DYNAMIC_SCAN_SIDE_STEP;
                double x = target.getX() + forwardX * forward + sideX * sideOffset;
                double z = target.getZ() + forwardZ * forward + sideZ * sideOffset;
                if (isVoidColumn(x, target.getY(), z)) {
                    double laneWeight = lane == 0 ? 1.4D : 1.0D;
                    score += laneWeight * (DYNAMIC_SCAN_DISTANCE + DYNAMIC_SCAN_STEP - forward);
                    centerVoid |= lane == 0;
                }
            }

            if (centerVoid) {
                consecutiveCenterVoid++;
                score += consecutiveCenterVoid * 2.0D;
            } else {
                consecutiveCenterVoid = 0;
            }
        }

        return score;
    }

    private boolean isDynamicPathClear(Player target, AABB baseCollisionBox, double forwardX, double forwardZ,
                                       double fromForward, double toForward) {
        for (double forward = fromForward + DYNAMIC_WALL_CHECK_STEP;
             forward <= toForward + 1.0E-4D;
             forward += DYNAMIC_WALL_CHECK_STEP) {
            AABB checkBox = baseCollisionBox.move(forwardX * forward, 0.0D, forwardZ * forward);
            if (hasBlockCollision(target, checkBox))
                return false;
        }

        return true;
    }

    private boolean hasBlockCollision(Player target, AABB box) {
        if (mc.level == null)
            return true;
        if (!mc.level.isLoaded(BlockPos.containing(box.getCenter())))
            return true;

        return !mc.level.noCollision(target, box);
    }

    private boolean isVoidColumn(double x, double y, double z) {
        if (mc.level == null)
            return false;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        int startY = Mth.floor(y) - 1;
        int endY = Math.max(mc.level.getMinY(), startY - VOID_SCAN_DEPTH);

        for (int blockY = startY; blockY >= endY; blockY--) {
            if (!mc.level.getBlockState(pos.set(blockX, blockY, blockZ)).isAir())
                return false;
        }

        return true;
    }

    private void updateDisplaceSide(Player target, double voidX, double voidZ) {
        LocalPlayer player = mc.player;
        if (player == null)
            return;

        double targetDx = target.getX() - player.getX();
        double targetDz = target.getZ() - player.getZ();
        double voidDx = voidX - player.getX();
        double voidDz = voidZ - player.getZ();
        double cross = targetDx * voidDz - targetDz * voidDx;
        displaceLeft = cross < 0.0D;
    }

    // ==================== State helpers ====================

    private static int msToTicks(double ms) {
        if (ms <= 0.0D)
            return 0;

        return (int) Math.ceil(ms / 50.0D);
    }

    private void pruneTargetDelayStates() {
        if (mc.level == null) {
            targetWindowStartTicks.clear();
            return;
        }

        Iterator<Map.Entry<Integer, Integer>> iterator = targetWindowStartTicks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Integer> entry = iterator.next();
            if (!(mc.level.getEntity(entry.getKey()) instanceof Player player)
                    || player.isDeadOrDying()
                    || !player.isAlive()) {
                iterator.remove();
            }
        }
    }

    private boolean shouldDisplaceInCurrentWindow(Player target, int currentTick) {
        if (target == null)
            return true;

        int targetId = target.getId();
        Integer windowStartTick = targetWindowStartTicks.get(targetId);
        if (windowStartTick == null || currentTick - windowStartTick >= DISPLACE_WINDOW_TICKS) {
            targetWindowStartTicks.put(targetId, currentTick);
            return true;
        }

        int delayTicks = msToTicks(delay.get());
        if (delayTicks <= 0)
            return true;

        return currentTick - windowStartTick >= delayTicks;
    }

    private void releaseBlink() {
        if (!blinkingModule)
            return;

        BlinkUtil.INSTANCE.sync(true, true);
        BlinkUtil.INSTANCE.stopBlink();
        blinkingModule = false;
    }

    private void clearActiveState() {
        startArrowFade();
        active = false;
        displaceThisTick = false;
        dynamicVoidYaw = null;
        renderDisplaceYaw = null;
        renderTarget = null;
    }

    private void clearFadingArrow() {
        fadingDisplaceYaw = null;
        fadingTarget = null;
        arrowFadeStartMs = 0L;
    }

    private void clearArrowState() {
        clearFadingArrow();
        lastRenderedDisplaceYaw = null;
        lastRenderedTarget = null;
        lastRenderedArrowMs = 0L;
    }

    private void startArrowFade() {
        long nowMs = System.currentTimeMillis();
        if (lastRenderedDisplaceYaw != null
                && lastRenderedTarget != null
                && !lastRenderedTarget.isDeadOrDying()
                && nowMs - lastRenderedArrowMs <= ARROW_FADE_MS) {
            fadingDisplaceYaw = lastRenderedDisplaceYaw;
            fadingTarget = lastRenderedTarget;
            arrowFadeStartMs = nowMs;
        }

        lastRenderedDisplaceYaw = null;
        lastRenderedTarget = null;
        lastRenderedArrowMs = 0L;
    }

    private void resetState() {
        displaceThisTick = false;
        active = false;
        releaseBlinkNextGameTick = false;
        dynamicVoidYaw = null;
        renderDisplaceYaw = null;
        renderTarget = null;
        clearArrowState();
        tickCounter = 0;
        targetWindowStartTicks.clear();
    }
}
