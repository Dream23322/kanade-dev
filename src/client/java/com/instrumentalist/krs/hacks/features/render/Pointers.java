package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import com.instrumentalist.krs.utils.render.RenderUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.nanovg.NVGPaint;
import org.nvgu.NVGU;
import org.nvgu.util.Alignment;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Screen-space player direction indicators.
 *
 * <p>The arrows use only the horizontal bearing to each player, so they remain
 * readable when a target is above, below, or behind the camera. The circle mode
 * blends them between two rings around the crosshair as target distance changes;
 * the edge mode slides each arrow to the screen border like a flight-sim direction
 * marker. Per-player smoothing eases changes in bearing and distance.</p>
 */
public class Pointers extends Module {
    private static final String MODE_CIRCLE = "Circle";
    private static final String MODE_EDGE = "Screen Edge";

    @Setting
    private final ListValue mode = new ListValue(
            "Mode",
            new String[]{MODE_CIRCLE, MODE_EDGE},
            MODE_CIRCLE
    ).describe("Circle keeps pointers around the crosshair; Screen Edge pushes them to the screen border.");

    @Setting
    private final ListValue style = new ListValue(
            "Style",
            new String[]{"Chevron", "Triangle", "Paper Plane", "Dart", "Outline"},
            "Paper Plane"
    ).describe("Arrow shape used for each pointer.");

    @Setting
    private final FloatValue radius = new FloatValue(
            "Radius",
            90f,
            24f,
            320f,
            "px",
            () -> mode.get().equalsIgnoreCase(MODE_CIRCLE)
    ).describe("Distance from the crosshair to the inner pointer ring in Circle mode.");

    @Setting
    private final FloatValue radius2 = new FloatValue(
            "Radius 2",
            160f,
            24f,
            480f,
            "px",
            () -> mode.get().equalsIgnoreCase(MODE_CIRCLE)
    ).describe("Outer ring radius. Pointers move from Radius toward Radius 2 as their target gets farther away.");

    @Setting
    private final BooleanValue distance = new BooleanValue(
            "Distance",
            true
    ).describe("Show the distance to each player below its pointer.");

    @Setting
    private final FloatValue smoothing = new FloatValue(
            "Smoothing",
            70f,
            0f,
            95f,
            "%"
    ).describe("Eases pointer movement and distance changes between frames. 0% follows instantly.");

    @Setting
    private final FloatValue range = new FloatValue(
            "Range",
            64f,
            8f,
            256f,
            "m"
    ).describe("Maximum distance at which a player receives a pointer.");

    @Setting
    private final BooleanValue onlyNonVisible = new BooleanValue(
            "Only Non-Visible",
            false
    ).describe("Only points to players outside your view or hidden behind blocks.");

    @Setting
    private final FloatValue size = new FloatValue(
            "Size",
            14f,
            1f,
            32f,
            "px"
    ).describe("Length of the pointer arrow.");

    private static final float[] SCREEN_PROJECTION = new float[3];
    private static final Map<UUID, PointerSmoother> SMOOTHERS = new HashMap<>();
    private static long lastSmoothingNanos;

    private record PointerTarget(UUID id, float angle, float distance) {
    }

    private record PointerPlacement(PointerTarget target, float x, float y, int alpha) {
    }

    private static final class PointerSmoother {
        private float angle;
        private float distance;
        private long lastSeenNanos;
        private boolean initialized;
    }

    public Pointers() {
        super("Pointers", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String description() {
        return "Draws arrows around the crosshair pointing toward nearby players";
    }

    @Override
    public void onEnable() {
        clearSmoothers();
    }

    @Override
    public void onDisable() {
        clearSmoothers();
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        if (mc.player == null
                || mc.level == null
                || mc.gui == null
                || mc.gui.hud.isHidden()
                || mc.gui.screen() != null
                || Client.nanoVgManager == null
                || !RenderUtil.shouldRenderWorldHudOverlays())
            return;

        float partialTicks = event.tickDelta;
        Vec3 eye = mc.player.getEyePosition(partialTicks);
        Vec3 look = mc.player.getViewVector(partialTicks);
        Vec3 flatLook = new Vec3(look.x, 0.0D, look.z);
        if (flatLook.lengthSqr() < 1.0E-6D)
            return;
        flatLook = flatLook.normalize();

        float maxRange = range.get();
        double maxRangeSquared = (double) maxRange * maxRange;
        List<PointerTarget> targets = new ArrayList<>();

        for (Player target : mc.level.players()) {
            if (target == mc.player || target.isRemoved() || target.isDeadOrDying())
                continue;

            Vec3 position = RenderUtil.INSTANCE.getLerpedPos(target, partialTicks);
            double dx = position.x - eye.x;
            double dy = position.y - eye.y;
            double dz = position.z - eye.z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared < 1.0E-4D || distanceSquared > maxRangeSquared)
                continue;

            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDistance < 1.0E-4D)
                continue;

            if (onlyNonVisible.get() && isVisibleOnScreen(target, position))
                continue;

            double directionX = dx / horizontalDistance;
            double directionZ = dz / horizontalDistance;
            double forward = flatLook.x * directionX + flatLook.z * directionZ;
            double right = -flatLook.z * directionX + flatLook.x * directionZ;
            float angle = (float) Math.atan2(right, forward);

            targets.add(new PointerTarget(target.getUUID(), angle, (float) Math.sqrt(distanceSquared)));
        }

        if (targets.isEmpty()) {
            pruneSmoothers(System.nanoTime());
            return;
        }

        List<PointerTarget> smoothed = smoothTargets(targets, smoothing.get());
        // Draw the nearest arrows last so they stay readable when bearings overlap.
        smoothed.sort(Comparator.comparingDouble(PointerTarget::distance).reversed());

        boolean circleMode = mode.get().equalsIgnoreCase(MODE_CIRCLE);
        String arrowStyle = style.get();
        float ringRadius = radius.get();
        float outerRingRadius = radius2.get();
        float arrowSize = size.get();
        boolean showDistance = distance.get();
        List<PointerTarget> snapshot = List.copyOf(smoothed);

        Client.nanoVgManager.load(vg -> {
            if (!tempEnabled)
                return;
            drawPointers(vg, snapshot, circleMode, arrowStyle, ringRadius, outerRingRadius,
                    arrowSize, maxRange, showDistance);
        });
    }

    private boolean isVisibleOnScreen(Player target, Vec3 position) {
        if (!RenderUtil.INSTANCE.renderedWorldToScreen(
                position.x,
                position.y + target.getBbHeight() * 0.5D,
                position.z,
                SCREEN_PROJECTION
        ))
            return false;

        return mc.player.hasLineOfSight(target);
    }

    private static List<PointerTarget> smoothTargets(List<PointerTarget> targets, float smoothingPercent) {
        long now = System.nanoTime();
        long elapsedNanos = lastSmoothingNanos == 0L ? 16_666_667L : now - lastSmoothingNanos;
        lastSmoothingNanos = now;

        float dt = Math.clamp(elapsedNanos / 1.0e9f, 0f, 0.1f);
        float amount = Math.clamp(smoothingPercent / 100f, 0f, 1f);
        float blend = amount < 0.0001f
                ? 1f
                : 1f - (float) Math.exp(-dt / (0.18f * amount));

        List<PointerTarget> smoothed = new ArrayList<>(targets.size());
        for (PointerTarget target : targets) {
            PointerSmoother state = SMOOTHERS.computeIfAbsent(target.id(), ignored -> new PointerSmoother());
            if (!state.initialized) {
                state.angle = target.angle();
                state.distance = target.distance();
                state.initialized = true;
            } else {
                state.angle = wrapAngle(state.angle + angleDelta(state.angle, target.angle()) * blend);
                state.distance += (target.distance() - state.distance) * blend;
            }

            state.lastSeenNanos = now;
            smoothed.add(new PointerTarget(target.id(), state.angle, state.distance));
        }

        pruneSmoothers(now);
        return smoothed;
    }

    private static float angleDelta(float from, float to) {
        float delta = (to - from) % ((float) Math.PI * 2f);
        if (delta > Math.PI) delta -= (float) Math.PI * 2f;
        if (delta < -Math.PI) delta += (float) Math.PI * 2f;
        return delta;
    }

    private static float wrapAngle(float angle) {
        float wrapped = (angle + (float) Math.PI) % ((float) Math.PI * 2f);
        if (wrapped < 0f) wrapped += (float) Math.PI * 2f;
        return wrapped - (float) Math.PI;
    }

    private static void pruneSmoothers(long now) {
        SMOOTHERS.entrySet().removeIf(entry -> now - entry.getValue().lastSeenNanos > 750_000_000L);
    }

    private static void clearSmoothers() {
        SMOOTHERS.clear();
        lastSmoothingNanos = 0L;
    }

    private static void drawPointers(NVGU vg, List<PointerTarget> targets, boolean circleMode,
                                     String style, float ringRadius, float outerRingRadius,
                                     float arrowSize, float maxRange, boolean showDistance) {
        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        if (screenWidth < 24f || screenHeight < 24f)
            return;

        float centerX = screenWidth * 0.5f;
        float centerY = screenHeight * 0.5f;
        float maxRadius = Math.max(arrowSize * 1.5f,
                Math.min(screenWidth, screenHeight) * 0.5f - arrowSize * 1.5f);
        float circleRadius = Math.clamp(ringRadius, arrowSize * 1.5f, maxRadius);
        float outerCircleRadius = Math.clamp(outerRingRadius, arrowSize * 1.5f, maxRadius);
        float edgeInset = Math.max(16f, arrowSize * 1.25f + 5f);
        float halfWidth = Math.max(1f, centerX - edgeInset);
        float halfHeight = Math.max(1f, centerY - edgeInset);
        List<PointerPlacement> placements = new ArrayList<>(targets.size());

        for (PointerTarget target : targets) {
            double angle = target.angle();
            float directionX = (float) Math.sin(angle);
            float directionY = -(float) Math.cos(angle);
            float x;
            float y;

            if (circleMode) {
                float distanceRatio = Math.clamp(target.distance() / Math.max(1f, maxRange), 0f, 1f);
                distanceRatio = distanceRatio * distanceRatio * (3f - 2f * distanceRatio);
                float targetRadius = circleRadius + (outerCircleRadius - circleRadius) * distanceRatio;
                x = centerX + directionX * targetRadius;
                y = centerY + directionY * targetRadius;
            } else {
                float horizontalScale = Math.abs(directionX) < 1.0E-5f
                        ? Float.MAX_VALUE : halfWidth / Math.abs(directionX);
                float verticalScale = Math.abs(directionY) < 1.0E-5f
                        ? Float.MAX_VALUE : halfHeight / Math.abs(directionY);
                float scale = Math.min(horizontalScale, verticalScale);
                x = centerX + directionX * scale;
                y = centerY + directionY * scale;
            }

            float fade = Math.clamp(1f - 0.5f * (target.distance() / Math.max(1f, maxRange)),
                    0.45f, 1f);
            int alpha = Math.max(1, Math.min(255, Math.round(fade * 255f)));
            drawArrow(vg, x, y, (float) angle - (float) Math.PI * 0.5f, style, arrowSize, alpha);
            placements.add(new PointerPlacement(target, x, y, alpha));
        }

        if (!showDistance)
            return;

        for (PointerPlacement placement : placements) {
            drawDistance(vg, placement, arrowSize, screenWidth, screenHeight);
        }
    }

    private static void drawDistance(NVGU vg, PointerPlacement placement, float arrowSize,
                                     float screenWidth, float screenHeight) {
        float fontSize = Math.clamp(arrowSize * 0.48f, 7f, 12f);
        float textX = Math.clamp(placement.x(), fontSize * 1.4f,
                Math.max(fontSize * 1.4f, screenWidth - fontSize * 1.4f));
        float gap = arrowSize * 1.05f;
        float belowY = placement.y() + gap;
        boolean below = belowY + fontSize <= screenHeight - 2f;
        float textY = below
                ? belowY
                : Math.max(1f, placement.y() - gap - fontSize);
        int alpha = Math.max(1, Math.round(placement.alpha() * 0.82f));

        NVGFonts.INTER_MEDIUM.drawText(
                Math.round(placement.target().distance()) + "m",
                textX,
                textY,
                fontSize,
                NanoVGTheme.text(alpha),
                Alignment.CENTER_TOP,
                true
        );
    }

    private static void drawArrow(NVGU vg, float x, float y, float rotation,
                                  String style, float size, int alpha) {
        long handle = vg.getHandle();
        Color accent = NanoVGTheme.accent(alpha);
        Color light = lighten(accent, 0.42f);
        Color dark = darken(accent, 0.48f);

        nvgSave(handle);
        nvgTranslate(handle, x, y);
        nvgRotate(handle, rotation);
        nvgLineCap(handle, NVG_ROUND);
        nvgLineJoin(handle, NVG_ROUND);

        switch (style.toLowerCase(Locale.ROOT)) {
            case "chevron" -> drawChevron(vg, size, light);
            case "triangle" -> drawTriangle(vg, size, accent, light, dark);
            case "dart" -> drawDart(vg, size, accent, light, dark);
            case "outline" -> drawOutline(vg, size, light);
            default -> drawPaperPlane(vg, size, alpha);
        }

        nvgRestore(handle);
    }

    private static void drawChevron(NVGU vg, float size, Color color) {
        long handle = vg.getHandle();
        float tipX = size * 0.86f;
        float tailX = -size * 0.62f;
        float wingY = size * 0.64f;

        drawShadow(vg, handle, size, () -> {
            chevronPath(handle, tipX, tailX, wingY);
            nvgStrokeWidth(handle, Math.max(1.8f, size * 0.16f));
            nvgStrokeColor(handle, vg.createAndStoreColour(new Color(0, 0, 0, 145)));
            nvgStroke(handle);
        });

        chevronPath(handle, tipX, tailX, wingY);
        nvgStrokeWidth(handle, Math.max(1.8f, size * 0.16f));
        nvgStrokeColor(handle, vg.createAndStoreColour(color));
        nvgStroke(handle);
    }

    private static void chevronPath(long handle, float tipX, float tailX, float wingY) {
        nvgBeginPath(handle);
        nvgMoveTo(handle, tailX, -wingY);
        nvgLineTo(handle, tipX, 0f);
        nvgLineTo(handle, tailX, wingY);
    }

    private static void drawTriangle(NVGU vg, float size, Color fill, Color light, Color dark) {
        long handle = vg.getHandle();
        float tipX = size * 0.94f;
        float tailX = -size * 0.66f;
        float notchX = -size * 0.24f;
        float wingY = size * 0.62f;

        drawShadow(vg, handle, size, () -> {
            notchedArrowPath(handle, tipX, tailX, notchX, wingY);
            nvgFillColor(handle, vg.createAndStoreColour(new Color(0, 0, 0, 150)));
            nvgFill(handle);
        });

        notchedArrowPath(handle, tipX, tailX, notchX, wingY);
        nvgFillColor(handle, vg.createAndStoreColour(fill));
        nvgFill(handle);

        nvgStrokeWidth(handle, Math.max(1f, size * 0.07f));
        nvgStrokeColor(handle, vg.createAndStoreColour(light));
        nvgStroke(handle);

        nvgBeginPath(handle);
        nvgMoveTo(handle, tipX, 0f);
        nvgLineTo(handle, notchX, 0f);
        nvgStrokeWidth(handle, Math.max(1f, size * 0.045f));
        nvgStrokeColor(handle, vg.createAndStoreColour(withAlpha(dark, 150)));
        nvgStroke(handle);
    }

    private static void drawDart(NVGU vg, float size, Color fill, Color light, Color dark) {
        long handle = vg.getHandle();
        float tipX = size * 0.96f;
        float shoulderX = size * 0.16f;
        float tailX = -size * 0.94f;
        float notchX = -size * 0.55f;
        float shoulderY = size * 0.32f;
        float tailY = size * 0.15f;

        drawShadow(vg, handle, size, () -> {
            nvgBeginPath(handle);
            nvgMoveTo(handle, tipX, 0f);
            nvgLineTo(handle, shoulderX, -shoulderY);
            nvgLineTo(handle, tailX, -tailY);
            nvgLineTo(handle, notchX, 0f);
            nvgLineTo(handle, tailX, tailY);
            nvgLineTo(handle, shoulderX, shoulderY);
            nvgClosePath(handle);
            nvgFillColor(handle, vg.createAndStoreColour(new Color(0, 0, 0, 150)));
            nvgFill(handle);
        });

        nvgBeginPath(handle);
        nvgMoveTo(handle, tipX, 0f);
        nvgLineTo(handle, shoulderX, -shoulderY);
        nvgLineTo(handle, tailX, -tailY);
        nvgLineTo(handle, notchX, 0f);
        nvgClosePath(handle);
        nvgFillColor(handle, vg.createAndStoreColour(lighten(fill, 0.18f)));
        nvgFill(handle);

        nvgBeginPath(handle);
        nvgMoveTo(handle, tipX, 0f);
        nvgLineTo(handle, notchX, 0f);
        nvgLineTo(handle, tailX, tailY);
        nvgLineTo(handle, shoulderX, shoulderY);
        nvgClosePath(handle);
        nvgFillColor(handle, vg.createAndStoreColour(dark));
        nvgFill(handle);

        nvgBeginPath(handle);
        nvgMoveTo(handle, tipX, 0f);
        nvgLineTo(handle, tailX, -tailY);
        nvgMoveTo(handle, tipX, 0f);
        nvgLineTo(handle, tailX, tailY);
        nvgStrokeWidth(handle, Math.max(1f, size * 0.06f));
        nvgStrokeColor(handle, vg.createAndStoreColour(light));
        nvgStroke(handle);
    }

    private static void drawOutline(NVGU vg, float size, Color color) {
        long handle = vg.getHandle();
        float tipX = size * 0.92f;
        float tailX = -size * 0.64f;
        float notchX = -size * 0.22f;
        float wingY = size * 0.6f;

        drawShadow(vg, handle, size, () -> {
            notchedArrowPath(handle, tipX, tailX, notchX, wingY);
            nvgStrokeWidth(handle, Math.max(1.8f, size * 0.15f));
            nvgStrokeColor(handle, vg.createAndStoreColour(new Color(0, 0, 0, 145)));
            nvgStroke(handle);
        });

        notchedArrowPath(handle, tipX, tailX, notchX, wingY);
        nvgStrokeWidth(handle, Math.max(1.4f, size * 0.13f));
        nvgStrokeColor(handle, vg.createAndStoreColour(color));
        nvgStroke(handle);

        nvgBeginPath(handle);
        nvgMoveTo(handle, -size * 0.38f, -size * 0.34f);
        nvgLineTo(handle, size * 0.48f, 0f);
        nvgLineTo(handle, -size * 0.38f, size * 0.34f);
        nvgStrokeWidth(handle, Math.max(1f, size * 0.06f));
        nvgStrokeColor(handle, vg.createAndStoreColour(withAlpha(color, 150)));
        nvgStroke(handle);
    }

    private static void drawPaperPlane(NVGU vg, float size, int alpha) {
        long handle = vg.getHandle();
        float tipX = size * 0.98f;
        float tailX = -size * 0.7f;
        float wingY = size * 0.6f;
        float notchX = -size * 0.28f;
        float notchY = 0f;

        drawShadow(vg, handle, size, () -> {
            paperPlanePath(handle, tipX, tailX, wingY);
            nvgFillColor(handle, vg.createAndStoreColour(new Color(0, 0, 0, 140)));
            nvgFill(handle);
        });

        Color top = new Color(235, 238, 245, alpha);
        Color middle = new Color(169, 175, 188, alpha);
        Color bottom = new Color(91, 98, 113, alpha);
        Color outline = new Color(248, 250, 255, Math.max(1, Math.round(alpha * 0.9f)));

        paperPlanePath(handle, tipX, tailX, wingY);
        NVGPaint paint = vg.createAndStorePaint();
        nvgLinearGradient(handle, tailX, -wingY, tailX, wingY,
                vg.createAndStoreColour(top), vg.createAndStoreColour(bottom), paint);
        nvgFillPaint(handle, paint);
        nvgFill(handle);

        // The lower crease gets a darker facet, which is what gives the silhouette its shallow 3D look.
        nvgBeginPath(handle);
        nvgMoveTo(handle, tipX, 0f);
        nvgLineTo(handle, notchX, notchY);
        nvgLineTo(handle, tailX, wingY);
        nvgClosePath(handle);
        nvgFillColor(handle, vg.createAndStoreColour(withAlpha(bottom, Math.max(1, alpha * 3 / 4))));
        nvgFill(handle);

        nvgBeginPath(handle);
        nvgMoveTo(handle, tipX, 0f);
        nvgLineTo(handle, notchX, notchY);
        nvgStrokeWidth(handle, Math.max(1f, size * 0.055f));
        nvgStrokeColor(handle, vg.createAndStoreColour(withAlpha(middle, Math.max(1, alpha * 4 / 5))));
        nvgStroke(handle);

        nvgBeginPath(handle);
        nvgMoveTo(handle, tipX, 0f);
        nvgLineTo(handle, tailX, -wingY);
        nvgStrokeWidth(handle, Math.max(1f, size * 0.05f));
        nvgStrokeColor(handle, vg.createAndStoreColour(withAlpha(top, Math.max(1, alpha * 3 / 4))));
        nvgStroke(handle);

        paperPlanePath(handle, tipX, tailX, wingY);
        nvgStrokeWidth(handle, Math.max(1f, size * 0.045f));
        nvgStrokeColor(handle, vg.createAndStoreColour(outline));
        nvgStroke(handle);
    }

    private static void paperPlanePath(long handle, float tipX, float tailX, float wingY) {
        nvgBeginPath(handle);
        nvgMoveTo(handle, tipX, 0f);
        nvgLineTo(handle, tailX, -wingY);
        nvgBezierTo(handle,
                tailX + (tipX - tailX) * 0.28f, -wingY * 0.46f,
                tailX + (tipX - tailX) * 0.28f, wingY * 0.46f,
                tailX, wingY);
        nvgClosePath(handle);
    }

    private static void notchedArrowPath(long handle, float tipX, float tailX,
                                          float notchX, float wingY) {
        nvgBeginPath(handle);
        nvgMoveTo(handle, tipX, 0f);
        nvgLineTo(handle, tailX, -wingY);
        nvgLineTo(handle, notchX, 0f);
        nvgLineTo(handle, tailX, wingY);
        nvgClosePath(handle);
    }

    private static void drawShadow(NVGU vg, long handle, float size, Runnable draw) {
        nvgSave(handle);
        nvgTranslate(handle, size * 0.1f, size * 0.12f);
        draw.run();
        nvgRestore(handle);
    }

    private static Color lighten(Color color, float amount) {
        float mix = Math.clamp(amount, 0f, 1f);
        return new Color(
                Math.round(color.getRed() + (255 - color.getRed()) * mix),
                Math.round(color.getGreen() + (255 - color.getGreen()) * mix),
                Math.round(color.getBlue() + (255 - color.getBlue()) * mix),
                color.getAlpha()
        );
    }

    private static Color darken(Color color, float amount) {
        float scale = 1f - Math.clamp(amount, 0f, 1f);
        return new Color(
                Math.round(color.getRed() * scale),
                Math.round(color.getGreen() * scale),
                Math.round(color.getBlue() * scale),
                color.getAlpha()
        );
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.clamp(alpha, 0, 255));
    }
}
