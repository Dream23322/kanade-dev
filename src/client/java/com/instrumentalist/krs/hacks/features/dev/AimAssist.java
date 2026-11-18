package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.features.combat.Reach;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.math.BehaviorUtils;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class AimAssist extends Module {

    private static final long CLICK_WINDOW_MS = 350L;
    private static final float AIM_STEP = 0.1f;
    private static final float MAX_SPEED_SETTING = 10.0f;

    @Setting
    private final ListValue mode = new ListValue("Mode", new String[]{"Normal", "Lock On"}, "Normal")
            .describe("Normal only nudges your aim while clicking. Lock On keeps the crosshair on a body zone while attacking.");

    @Setting
    private final FloatValue horizontalSpeed = new FloatValue("Horizontal Speed", 3.0f, 0.0f, MAX_SPEED_SETTING)
            .describe("How strongly the assist pulls your yaw towards the target.");

    @Setting
    private final FloatValue verticalSpeed = new FloatValue("Vertical Speed", 0.0f, 0.0f, MAX_SPEED_SETTING)
            .describe("How strongly the assist pulls your pitch towards the target.");

    @Setting
    private final FloatValue smoothing = new FloatValue("Smoothing", 50.0f, 0.0f, 100.0f, "%")
            .describe("How far inside the hitbox the aim is pulled. Higher values aim closer to the box centre.");

    @Setting
    private final ListValue safeZone = new ListValue("Safe Zone", new String[]{"Head", "Torso", "Feet"}, "Torso", this::isLockOn)
            .describe("Body zone the crosshair can rest on without any assist in Lock On mode.");

    @Setting
    private final BooleanValue noise = new BooleanValue("Noise", false, this::isLockOn)
            .describe("Adds random aim offsets so the pull does not look perfectly mechanical.");

    @Setting
    private final FloatValue noiseMinYaw = new FloatValue("Noise Min Yaw", 0.0f, 0.0f, 5.0f, "°", () -> isLockOn() && noise.get())
            .describe("Smallest random yaw offset.");

    @Setting
    private final FloatValue noiseMaxYaw = new FloatValue("Noise Max Yaw", 1.0f, 0.0f, 5.0f, "°", () -> isLockOn() && noise.get())
            .describe("Largest random yaw offset.");

    @Setting
    private final FloatValue noiseMinPitch = new FloatValue("Noise Min Pitch", 0.0f, 0.0f, 5.0f, "°", () -> isLockOn() && noise.get())
            .describe("Smallest random pitch offset.");

    @Setting
    private final FloatValue noiseMaxPitch = new FloatValue("Noise Max Pitch", 1.0f, 0.0f, 5.0f, "°", () -> isLockOn() && noise.get())
            .describe("Largest random pitch offset.");

    @Setting
    private final FloatValue noiseSpeed = new FloatValue("Noise Speed", 1.0f, 0.1f, 10.0f, "x", () -> isLockOn() && noise.get())
            .describe("How often a new noise offset is picked.");

    @Setting
    private final FloatValue range = new FloatValue("Range", 4.5f, 3.0f, 8.0f, "m")
            .describe("Distance the assist looks for targets in.");

    @Setting
    private final IntValue fov = new IntValue("FOV", 90, 30, 360, "°")
            .describe("Cone around your crosshair that is checked for targets.");

    @Setting
    private final BooleanValue weaponOnly = new BooleanValue("Weapon Only", true)
            .describe("Only assists while holding a sword or an axe.");

    @Setting
    private final BooleanValue allowTools = new BooleanValue("Allow Tools", false, weaponOnly::get)
            .describe("Also assists while holding a pickaxe, shovel or hoe.");

    @Setting
    private final BooleanValue botChecks = new BooleanValue("Bot Check", true)
            .describe("Ignores entities the Anti Bot module marked as bots.");

    @Setting
    private final BooleanValue teams = new BooleanValue("Teams", true)
            .describe("Ignores players on your own team.");

    private double noiseYawOffset;
    private double noisePitchOffset;
    private long lastNoiseMs;
    private long lastAttackMs;
    private boolean attackWasDown;

    @Override
    public String description() {
        return "Assists your aim by subtly adjusting your view towards nearby targets when attacking";
    }

    public AimAssist() {
        super("Aim Assist", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @Override
    public void onWorld(WorldEvent event) {
        resetState();
    }

    @Override
    public void onTick(TickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gui.screen() != null)
            return;

        long now = System.currentTimeMillis();
        boolean attacking = mc.options.keyAttack.isDown();
        if (attacking && !attackWasDown)
            lastAttackMs = now;
        attackWasDown = attacking;

        if (isLockOn())
            tickLockOn(player, attacking);
        else
            tickNormal(player, attacking, now);
    }

    private void tickNormal(LocalPlayer player, boolean attacking, long now) {
        if (!holdingValidItem(player))
            return;

        if (attacking && isLookingAtBlock())
            return;
        if (!attacking && now - lastAttackMs >= CLICK_WINDOW_MS)
            return;

        Player target = selectTarget(player);
        if (target == null || distanceTo(player, target) <= 0.0)
            return;

        applyAim(player, target.getBoundingBox().inflate(target.getPickRadius()));
    }

    private void tickLockOn(LocalPlayer player, boolean attacking) {
        if (!attacking || isLookingAtBlock())
            return;
        if (!holdingValidItem(player))
            return;

        Player target = selectTarget(player);
        if (target == null)
            return;

        AABB safeBox = bodyZone(target);
        if (isCrosshairInside(player, safeBox))
            return;

        applyAim(player, safeBox);
    }

    private void applyAim(LocalPlayer player, AABB box) {
        float currentYaw = player.getYRot();
        float currentPitch = player.getXRot();

        float[] rotation = rotationsToBox(player, box, currentYaw, currentPitch);
        float yawSpeed = Math.min(Math.abs(horizontalSpeed.get()), MAX_SPEED_SETTING);
        float pitchSpeed = Math.min(Math.abs(verticalSpeed.get()), MAX_SPEED_SETTING);

        float yawStep = Mth.wrapDegrees(rotation[0] - currentYaw) * AIM_STEP * yawSpeed;
        float pitchStep = (rotation[1] - currentPitch) * AIM_STEP * pitchSpeed;

        if (Math.abs(yawStep) < 0.01f && Math.abs(pitchStep) < 0.01f)
            return;

        float targetYaw = currentYaw + yawStep;
        float targetPitch = Mth.clamp(currentPitch + pitchStep, -90.0f, 90.0f);

        if (isLockOn() && noise.get()) {
            updateNoise();
            targetYaw += (float) noiseYawOffset;
            targetPitch = Mth.clamp(targetPitch + (float) noisePitchOffset, -90.0f, 90.0f);
        }

        // An aim assist has to move the view the player is looking at. The silent rotation
        // pipeline only rewrites the rotation sent to the server, so the crosshair never moved
        // and the assist looked broken.
        player.setYRot(targetYaw);
        player.setXRot(targetPitch);
        player.yRotO += Mth.wrapDegrees(targetYaw - currentYaw);
        player.xRotO += targetPitch - currentPitch;
    }

    /**
     * Rotation towards the closest point of the box to the current look ray, pulled towards the
     * box centre by the smoothing setting so the aim does not snap to hitbox edges.
     */
    private float[] rotationsToBox(LocalPlayer player, AABB box, float yaw, float pitch) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = lookVector(yaw, pitch);
        Vec3 center = box.getCenter();

        double reach = Math.max(1.0, eye.distanceTo(center) + 2.0);
        Optional<Vec3> hit = box.clip(eye, eye.add(look.scale(reach)));

        Vec3 aimPoint;
        if (hit.isPresent()) {
            aimPoint = hit.get();
        } else {
            double along = Math.max(0.0, center.subtract(eye).dot(look));
            aimPoint = closestPoint(box, eye.add(look.scale(along)));
        }

        aimPoint = aimPoint.lerp(center, smoothing.get() / 100.0);
        return rotationsTo(eye, aimPoint);
    }

    private AABB bodyZone(Player target) {
        AABB full = target.getBoundingBox();
        double height = full.maxY - full.minY;
        double headSize = height / 4.5;
        double torsoSize = height / 2.75;

        AABB head = new AABB(full.minX, full.maxY - headSize, full.minZ, full.maxX, full.maxY, full.maxZ);
        AABB torso = new AABB(full.minX, full.minY + torsoSize, full.minZ, full.maxX, full.maxY - headSize, full.maxZ);
        AABB feet = new AABB(full.minX, full.minY, full.minZ, full.maxX, full.minY + torsoSize, full.maxZ);

        String zone = safeZone.get();
        AABB safe = "Head".equalsIgnoreCase(zone) ? head
                : "Feet".equalsIgnoreCase(zone) ? feet
                : torso;

        return safe.inflate(target.getPickRadius());
    }

    /** Projects the zone corners and checks whether the crosshair already rests inside it. */
    private boolean isCrosshairInside(LocalPlayer player, AABB box) {
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        Vec3 camera = player.getEyePosition();

        double boxLeft = Double.MAX_VALUE;
        double boxRight = -Double.MAX_VALUE;
        double boxTop = Double.MAX_VALUE;
        double boxBottom = -Double.MAX_VALUE;

        for (Vec3 corner : corners(box)) {
            double[] screenPos = worldToScreen(corner, camera, yaw, pitch);
            if (screenPos == null)
                continue;

            boxLeft = Math.min(boxLeft, screenPos[0]);
            boxRight = Math.max(boxRight, screenPos[0]);
            boxTop = Math.min(boxTop, screenPos[1]);
            boxBottom = Math.max(boxBottom, screenPos[1]);
        }

        if (boxLeft == Double.MAX_VALUE)
            return false;

        double aimX = mc.getWindow().getWidth() / 2.0;
        double aimY = mc.getWindow().getHeight() / 2.0;
        return aimX >= boxLeft && aimX <= boxRight && aimY >= boxTop && aimY <= boxBottom;
    }

    /** Projects a world position onto the framebuffer, or null when it is behind the camera. */
    private double[] worldToScreen(Vec3 worldPos, Vec3 cameraPos, float cameraYaw, float cameraPitch) {
        double dx = worldPos.x - cameraPos.x;
        double dy = worldPos.y - cameraPos.y;
        double dz = worldPos.z - cameraPos.z;

        double yawRad = Math.toRadians(cameraYaw);
        double cosYaw = Math.cos(-yawRad);
        double sinYaw = Math.sin(-yawRad);
        double x1 = dx * cosYaw - dz * sinYaw;
        double z1 = dx * sinYaw + dz * cosYaw;
        double y1 = dy;

        double pitchRad = Math.toRadians(cameraPitch);
        double cosPitch = Math.cos(-pitchRad);
        double sinPitch = Math.sin(-pitchRad);
        double y2 = y1 * cosPitch - z1 * sinPitch;
        double z2 = y1 * sinPitch + z1 * cosPitch;
        if (z2 <= 0.0)
            return null;

        int screenWidth = mc.getWindow().getWidth();
        int screenHeight = mc.getWindow().getHeight();
        double tanHalfFov = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double aspect = (double) screenWidth / (double) screenHeight;
        double screenX = (x1 / z2) / (tanHalfFov * aspect) * (screenWidth / 2.0) + screenWidth / 2.0;
        double screenY = (y2 / z2) / tanHalfFov * (screenHeight / 2.0) + screenHeight / 2.0;

        return new double[]{screenX, screenY};
    }

    private void updateNoise() {
        long now = System.currentTimeMillis();
        long interval = (long) (1000.0 / Math.max(0.1f, noiseSpeed.get()));
        if (now - lastNoiseMs < interval)
            return;

        lastNoiseMs = now;
        double minYaw = noiseMinYaw.get();
        double maxYaw = Math.max(minYaw, noiseMaxYaw.get());
        double minPitch = noiseMinPitch.get();
        double maxPitch = Math.max(minPitch, noiseMaxPitch.get());

        noiseYawOffset = minYaw + Math.random() * (maxYaw - minYaw);
        if (Math.random() < 0.5)
            noiseYawOffset = -noiseYawOffset;

        noisePitchOffset = minPitch + Math.random() * (maxPitch - minPitch);
        if (Math.random() < 0.5)
            noisePitchOffset = -noisePitchOffset;
    }

    private Player selectTarget(LocalPlayer player) {
        List<Player> candidates = new ArrayList<>();
        for (Player other : mc.level.players()) {
            if (isValidTarget(player, other))
                candidates.add(other);
        }

        if (candidates.isEmpty())
            return null;

        candidates.sort(Comparator.comparingDouble(other -> distanceTo(player, other)));

        double reach = Reach.hookEntityReach(player.entityInteractionRange());
        List<Player> inReach = new ArrayList<>(candidates.size());
        for (Player other : candidates) {
            if (distanceTo(player, other) <= reach)
                inReach.add(other);
        }

        return (inReach.isEmpty() ? candidates : inReach).get(0);
    }

    private boolean isValidTarget(LocalPlayer player, Player other) {
        if (other == player || other == player.getVehicle() || other == mc.getCameraEntity())
            return false;
        if (!other.isAlive() || other.isDeadOrDying() || other.isSpectator())
            return false;

        double distance = distanceTo(player, other);
        if (distance > range.get() || distance <= 0.0)
            return false;
        if (angleTo(player, other) > fov.get() / 2.0f)
            return false;
        if (!canSee(player, other))
            return false;
        if (botChecks.get() && BehaviorUtils.isBot(other, true))
            return false;

        return !teams.get() || !BehaviorUtils.isTeammate(other, true);
    }

    private boolean holdingValidItem(LocalPlayer player) {
        if (!weaponOnly.get())
            return true;

        ItemStack held = player.getMainHandItem();
        int category = ToolUtil.INSTANCE.getToolCategory(held.getItem());
        if (category == ToolUtil.TOOL_SWORD || category == ToolUtil.TOOL_AXE)
            return true;

        return allowTools.get() && category >= 0;
    }

    private boolean isLookingAtBlock() {
        return mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK;
    }

    private boolean isLockOn() {
        return "Lock On".equalsIgnoreCase(mode.get());
    }

    private void resetState() {
        noiseYawOffset = 0.0;
        noisePitchOffset = 0.0;
        lastNoiseMs = 0L;
        lastAttackMs = 0L;
        attackWasDown = false;
    }

    private static boolean canSee(LocalPlayer player, Player other) {
        Vec3 eye = player.getEyePosition();
        Vec3 point = closestPoint(other.getBoundingBox(), eye);
        return player.level().clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }

    private static double distanceTo(LocalPlayer player, Entity other) {
        Vec3 eye = player.getEyePosition();
        return closestPoint(other.getBoundingBox(), eye).distanceTo(eye);
    }

    private static float angleTo(LocalPlayer player, Entity other) {
        Vec3 eye = player.getEyePosition();
        Vec3 delta = closestPoint(other.getBoundingBox(), eye).subtract(eye);
        if (delta.lengthSqr() < 1.0E-6)
            return 0.0f;

        float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0f;
        float pitch = (float) -(Mth.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * Mth.RAD_TO_DEG);
        float yawDiff = Math.abs(Mth.wrapDegrees(yaw - player.getYRot()));
        float pitchDiff = Math.abs(pitch - player.getXRot());
        return Math.max(yawDiff, pitchDiff);
    }

    private static float[] rotationsTo(Vec3 eye, Vec3 point) {
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        float pitch = (float) -(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG);
        return new float[]{yaw, Mth.clamp(pitch, -90.0f, 90.0f)};
    }

    private static Vec3 lookVector(float yaw, float pitch) {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        return new Vec3(
                -Mth.cos(pitchRad) * Mth.sin(yawRad),
                -Mth.sin(pitchRad),
                Mth.cos(pitchRad) * Mth.cos(yawRad)
        );
    }

    private static Vec3 closestPoint(AABB box, Vec3 point) {
        return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ)
        );
    }

    private static Vec3[] corners(AABB box) {
        return new Vec3[]{
                new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.minX, box.maxY, box.maxZ),
                new Vec3(box.maxX, box.maxY, box.maxZ)
        };
    }
}
