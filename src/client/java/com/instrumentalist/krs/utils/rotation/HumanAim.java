package com.instrumentalist.krs.utils.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Humanised per-frame aim movement used by Silent Aura.
 *
 * <p>This is a port of Tachibana's shared {@code HumanAim} engine. The aim
 * module supplies a target angle and a {@link Settings} accessor over its own
 * settings; this class owns the reaction delay, acceleration curve, wrist arc,
 * noise, overshoot and mouse-grid snapping.</p>
 */
public final class HumanAim {
    private static final Random RNG = new Random();

    private HumanAim() {
    }

    /** Per-engagement state. Kept by the caller so it survives frame updates. */
    public static final class Engagement {
        public int targetId = -1;
        public Vec3 previousTargetPosition = Vec3.ZERO;
        public Vec3 targetVelocity = Vec3.ZERO;
        public int reactionTimer;
        public int reactionDelay;
        public float overshootBias;
        public float noisePhase;
        public float arcAmount;
        public int engagementAge;
        public float residualYaw;
        public float residualPitch;
    }

    /** Tuning values implemented over the calling module's settings. */
    public interface Settings {
        float speed();

        float speedRandom();

        boolean reaction();

        int reactMin();

        int reactMax();

        boolean fitts();

        float arc();

        float noise();

        boolean saccade();

        float gcdAcc();

        boolean gcdEnabled();
    }

    public static void updatePrediction(Engagement engagement, Vec3 position) {
        engagement.targetVelocity = position.subtract(engagement.previousTargetPosition);
        engagement.previousTargetPosition = position;
    }

    public static void resetEngagement(Engagement engagement, LivingEntity target, Settings settings) {
        int targetId = target.getId();
        if (targetId == engagement.targetId) {
            engagement.engagementAge++;
            return;
        }

        engagement.targetId = targetId;
        engagement.previousTargetPosition = target.getEyePosition();
        engagement.targetVelocity = Vec3.ZERO;
        engagement.reactionTimer = 0;
        engagement.reactionDelay = settings.reaction()
                ? randomRange(settings.reactMin(), settings.reactMax())
                : 0;
        engagement.overshootBias = (RNG.nextFloat() - 0.5f) * 0.4f;
        engagement.noisePhase = RNG.nextFloat() * 1000f;
        engagement.arcAmount = 0.3f + RNG.nextFloat() * 0.4f;
        engagement.engagementAge = 0;
        engagement.residualYaw = 0f;
        engagement.residualPitch = 0f;
    }

    public static void driftEngagement(Engagement engagement) {
        engagement.overshootBias += (RNG.nextFloat() - 0.5f) * 0.005f;
        engagement.overshootBias = Mth.clamp(engagement.overshootBias, -1f, 1f);
        engagement.arcAmount += (RNG.nextFloat() - 0.5f) * 0.005f;
        engagement.arcAmount = Mth.clamp(engagement.arcAmount, 0.05f, 1f);
        engagement.noisePhase += (RNG.nextFloat() - 0.5f) * 0.1f;
    }

    public static Vec3 predictedAimPosition(Vec3 base, Vec3 velocity, float ticks) {
        return ticks <= 0f ? base : base.add(velocity.scale(ticks));
    }

    private static int randomRange(int minimum, int maximum) {
        int min = Math.min(minimum, maximum);
        int max = Math.max(minimum, maximum);
        return min + RNG.nextInt(max - min + 1);
    }

    private static float[] improvedNoise(Engagement engagement, float strength) {
        if (strength <= 0f)
            return new float[]{0f, 0f};

        float time = engagement.engagementAge * 0.05f + engagement.noisePhase;
        float yaw = (float) (Math.sin(time) * 0.5D
                + Math.sin(time * 2.1D + 1.3D) * 0.25D
                + Math.sin(time * 4.3D + 2.7D) * 0.125D);
        float pitch = (float) (Math.sin(time * 0.9D + 4.2D) * 0.5D
                + Math.sin(time * 2.3D + 5.7D) * 0.25D
                + Math.sin(time * 3.7D + 0.3D) * 0.125D);
        return new float[]{yaw * strength, pitch * strength * 0.6f};
    }

    private static float mouseStep() {
        double sensitivity = Minecraft.getInstance().options.sensitivity().get();
        double value = sensitivity * 0.6D + 0.2D;
        return (float) (value * value * value * 8.0D * 0.15D);
    }

    public static float snapYaw(float yaw) {
        float step = Math.max(0.001f, mouseStep());
        return Math.round(yaw / step) * step;
    }

    public static float snapPitch(float pitch) {
        float step = Math.max(0.001f, mouseStep());
        float snapped = Math.round(pitch / step) * step;
        if (snapped == 0f)
            snapped = pitch >= 0f ? step : -step;
        return snapped;
    }

    public static float[] applyVariableGcd(float deltaYaw, float deltaPitch, float accuracy) {
        float step = Math.max(0.001f, mouseStep());
        if (accuracy >= 0.999f) {
            return new float[]{
                    Math.round(deltaYaw / step) * step,
                    Math.round(deltaPitch / step) * step
            };
        }

        return new float[]{
                ditherMouseCount(deltaYaw / step, accuracy) * step,
                ditherMouseCount(deltaPitch / step, accuracy) * step
        };
    }

    private static float ditherMouseCount(float raw, float accuracy) {
        int nearest = Math.round(raw);
        float fraction = raw - (float) Math.floor(raw);
        float roll = RNG.nextFloat();
        float floorProbability = (1f - accuracy) * (1f - fraction);
        float ceilProbability = (1f - accuracy) * fraction;
        if (roll < floorProbability)
            return (float) Math.floor(raw);
        if (roll < floorProbability + ceilProbability)
            return (float) Math.ceil(raw);
        return nearest;
    }

    private static float cooldownFactor(boolean gcdEnabled, LocalPlayer player) {
        if (!gcdEnabled)
            return 1f;

        float cooldown = player.getAttackStrengthScale(0f);
        return cooldown < 0.85f ? 0.4f : 1f;
    }

    /**
     * Returns the frame's yaw/pitch delta after smooth acceleration, reaction,
     * overshoot, wrist arc, noise and GCD snapping.
     */
    public static float[] computeDelta(Engagement engagement, LocalPlayer player,
                                       float targetYaw, float targetPitch,
                                       double blockDistance, double angularWidth,
                                       float speed, Settings settings) {
        float deltaYaw = Mth.wrapDegrees(targetYaw - player.getYRot());
        float deltaPitch = Mth.clamp(targetPitch - player.getXRot(), -90f, 90f);
        float distance = (float) Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (distance < 0.01f) {
            engagement.residualYaw = 0f;
            engagement.residualPitch = 0f;
            return new float[]{0f, 0f};
        }

        boolean reactionActive = settings.reaction() && engagement.reactionTimer < engagement.reactionDelay;
        if (reactionActive)
            engagement.reactionTimer++;

        float reactionFactor = reactionActive
                ? Math.min(1f, engagement.reactionTimer / (float) Math.max(1, engagement.reactionDelay))
                : 1f;

        float directionYaw = deltaYaw / distance;
        float directionPitch = deltaPitch / distance;

        float snapProportion = Math.min(0.85f, speed * 0.07f + 0.08f);
        if (distance < 10f)
            snapProportion *= distance / (distance + 2.5f);

        float trackProportion = 0.50f + speed * 0.03f;
        float blend = Mth.clamp(distance / 3f, 0f, 1f);
        float proportion = snapProportion * blend + trackProportion * (1f - blend);
        float moveSpeed = distance * proportion;

        if (settings.fitts()) {
            double width = Math.max(1.0D, angularWidth);
            double indexOfDifficulty = Math.log(1.0D + distance / width) / Math.log(2.0D);
            double movementTime = 0.2D + 0.1D * indexOfDifficulty;
            float fittsMaximum = (float) (distance / (movementTime * 20.0D));
            if (moveSpeed > fittsMaximum)
                moveSpeed = fittsMaximum;
        }

        // No single frame may cross the whole angle gap. Without this the
        // smooth curve can snap to the target and feel like a lock-on.
        float maximumMove = distance * 0.80f + 0.05f;
        if (moveSpeed > maximumMove)
            moveSpeed = maximumMove;

        float speedRandom = settings.speedRandom();
        if (speedRandom > 0f) {
            float noiseTime = engagement.engagementAge * 0.05f + engagement.noisePhase;
            float variation = (float) (Math.sin(noiseTime) * speedRandom * 0.002D);
            moveSpeed *= 1f + variation;
        }

        moveSpeed *= cooldownFactor(settings.gcdEnabled(), player);
        moveSpeed *= reactionFactor;

        float moveYaw = directionYaw * moveSpeed;
        float movePitch = directionPitch * moveSpeed;

        if (blend > 0.5f && moveSpeed > distance) {
            float excess = moveSpeed - distance;
            float overshootWeight = Math.max(0f, engagement.overshootBias);
            float undershootWeight = Math.max(0f, -engagement.overshootBias);

            if (overshootWeight > 0.05f) {
                engagement.residualYaw += directionYaw * excess * 0.3f * overshootWeight;
                engagement.residualPitch += directionPitch * excess * 0.3f * overshootWeight;
            }
            if (undershootWeight > 0.05f) {
                float undershoot = undershootWeight * 0.3f;
                moveSpeed = distance * (1f - undershoot);
                moveYaw = directionYaw * moveSpeed;
                movePitch = directionPitch * moveSpeed;
            }
        }

        if (Math.abs(engagement.residualYaw) > 0.001f || Math.abs(engagement.residualPitch) > 0.001f) {
            moveYaw += engagement.residualYaw * 0.2f;
            movePitch += engagement.residualPitch * 0.2f;
            engagement.residualYaw *= 0.85f;
            engagement.residualPitch *= 0.85f;
        }

        float arcStrength = settings.arc();
        if (arcStrength > 0f && (blend > 0.3f || moveSpeed > 0.5f)) {
            float progress = Math.min(1f, moveSpeed / Math.max(distance, 0.01f));
            float arcDeviation = (float) (Math.sin(progress * Math.PI) * arcStrength * engagement.arcAmount * 2.0D);
            moveYaw += -directionPitch * arcDeviation * 0.15f;
            movePitch += directionYaw * arcDeviation * 0.15f;
        }

        if (distance < 1f) {
            float glide = (1f - distance / (distance + 2.5f)) * 0.03f;
            moveYaw += (RNG.nextFloat() - 0.5f) * 2f * glide;
            movePitch += (RNG.nextFloat() - 0.5f) * 2f * glide * 0.6f;
        }

        float noiseStrength = settings.noise();
        if (noiseStrength > 0f) {
            float[] noise = improvedNoise(engagement, noiseStrength);
            float noiseScale = blend < 0.5f ? 0.08f : Math.min(0.5f, distance * 0.1f);
            moveYaw += noise[0] * 0.4f * noiseScale;
            movePitch += noise[1] * 0.4f * noiseScale;
        }

        if (settings.saccade() && blend < 0.5f && engagement.engagementAge > 10) {
            float tremor = (float) (Math.sin(engagement.engagementAge * 0.22D + engagement.noisePhase) * 0.005D
                    + Math.sin(engagement.engagementAge * 0.37D + engagement.noisePhase * 1.3D) * 0.003D);
            moveYaw += tremor;
            movePitch += tremor * 0.6f;
        }

        float wobbleStrength = settings.noise() * 14f;
        if (wobbleStrength > 0.05f) {
            float[] wobbled = wobbleDelta(moveYaw, movePitch, wobbleStrength,
                    engagement.engagementAge * 0.05f + engagement.noisePhase);
            moveYaw = wobbled[0];
            movePitch = wobbled[1];
        }

        return applyVariableGcd(moveYaw, movePitch, settings.gcdAcc());
    }

    private static float[] wobbleDelta(float deltaYaw, float deltaPitch, float strength, float phase) {
        float wobble = (float) (Math.sin(phase) * 0.7D + Math.sin(phase * 2.1D + 1.3D) * 0.3D) * strength;
        wobble += (RNG.nextFloat() - 0.5f) * 2f * strength;
        float radians = wobble * 0.017453292f;
        float cosine = (float) Math.cos(radians);
        float sine = (float) Math.sin(radians);
        return new float[]{deltaYaw * cosine - deltaPitch * sine, deltaYaw * sine + deltaPitch * cosine};
    }

    private static final double DEGREES = 180.0D / Math.PI;

    public static float yawTo(Vec3 from, Vec3 to) {
        double deltaX = to.x - from.x;
        double deltaZ = to.z - from.z;
        return (float) (Math.atan2(-deltaX, deltaZ) * DEGREES);
    }

    public static float pitchTo(Vec3 from, Vec3 to) {
        double deltaX = to.x - from.x;
        double deltaY = to.y - from.y;
        double deltaZ = to.z - from.z;
        double horizontal = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        return (float) (-Math.atan2(deltaY, horizontal) * DEGREES);
    }
}