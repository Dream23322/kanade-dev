package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.AttackEvent;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import com.instrumentalist.krs.utils.render.NanoVGTheme;
import com.instrumentalist.krs.utils.render.RenderUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.nvgu.NVGU;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.nanovg.NanoVG.*;

public class HitMarker extends Module {
    private static final int MAX_3D_MARKERS = 8;
    private static final float[] PROJECTION = new float[3];
    private static final float POP_DURATION_MS = 110f;
    private static final float[] DASH_STARTS = {0.06f, 0.40f, 0.74f};
    private static final float[] DASH_ENDS = {0.30f, 0.64f, 0.94f};

    @Setting
    private final ListValue style = new ListValue(
            "Style",
            new String[]{"Classic X", "Bold X", "Long X", "Split X", "Chevron X", "Dotted X", "CS2 Ticks", "Diamond", "Ring"},
            "Classic X"
    ).describe("Shape of the hit marker.");

    @Setting
    private final ListValue placement = new ListValue(
            "Placement",
            new String[]{"Crosshair", "3D"},
            "Crosshair"
    ).describe("Crosshair draws a single marker at the screen centre; 3D keeps markers on their hit positions in the world.");

    @Setting
    private final FloatValue size = new FloatValue(
            "Size",
            18f,
            8f,
            48f,
            "px"
    ).describe("Width of the marker.");

    @Setting
    private final FloatValue gap = new FloatValue(
            "Gap",
            4f,
            0f,
            16f,
            "px"
    ).describe("Space left empty in the centre of the marker.");

    @Setting
    private final FloatValue thickness = new FloatValue(
            "Thickness",
            2f,
            1f,
            5f,
            "px"
    ).describe("Stroke width of the marker.");

    @Setting
    private final IntValue hangTime = new IntValue(
            "Hang Time",
            100,
            0,
            3000,
            "ms"
    ).describe("How long the marker stays fully visible before it starts fading.");

    @Setting
    private final IntValue duration = new IntValue(
            "Fade Time",
            300,
            100,
            3000,
            "ms"
    ).describe("How long the marker takes to fade out after its hang time.");

    @Setting
    private final BooleanValue scalePop = new BooleanValue(
            "Scale Pop",
            true
    ).describe("Briefly scales the marker up when it appears.");

    @Setting
    private final BooleanValue fade = new BooleanValue(
            "Fade",
            true
    ).describe("Fades the marker out after its hang time.");

    @Setting
    private final BooleanValue bloom = new BooleanValue(
            "Bloom",
            true
    ).describe("Adds a soft glow around the hit marker.");

    private final List<Marker> markers = new ArrayList<>(MAX_3D_MARKERS);
    private long crosshairStartNanos;

    public HitMarker() {
        super("Hit Marker", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, false);
    }

    @Override
    public String description() {
        return "Shows theme-coloured markers when your attacks land";
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onWorld(WorldEvent event) {
        reset();
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (event == null || mc.player == null)
            return;

        Entity entity = event.entity;
        if (!(entity instanceof LivingEntity living)
                || living == mc.player
                || living instanceof ArmorStand
                || living.isRemoved()
                || living.isDeadOrDying())
            return;

        long now = System.nanoTime();
        crosshairStartNanos = now;

        if (!is3D())
            return;

        while (markers.size() >= MAX_3D_MARKERS)
            markers.remove(0);
        markers.add(new Marker(now, hitPoint(living)));
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!is3D())
            return;

        long now = System.nanoTime();
        pruneExpired(now);

        for (Marker marker : markers) {
            marker.projected = false;
            if (!RenderUtil.INSTANCE.renderedWorldToScreen(
                    marker.position.x, marker.position.y, marker.position.z, PROJECTION))
                continue;

            marker.screenX = NanoVGManager.fromFramebufferX(PROJECTION[0]);
            marker.screenY = NanoVGManager.fromFramebufferY(PROJECTION[1]);
            marker.projected = true;
        }
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        if (Client.nanoVgManager == null
                || mc.gui == null
                || mc.gui.hud.isHidden()
                || mc.gui.screen() != null
                || !RenderUtil.shouldRenderWorldHudOverlays())
            return;

        long now = System.nanoTime();

        if (is3D()) {
            pruneExpired(now);
            if (markers.isEmpty())
                return;

            Client.nanoVgManager.loadWorldAnchored(vg -> drawWorldMarkers(vg, now));
            return;
        }

        if (crosshairStartNanos == 0L)
            return;

        if (isExpired(now, crosshairStartNanos)) {
            crosshairStartNanos = 0L;
            return;
        }

        float alpha = alphaAt(now, crosshairStartNanos);
        if (alpha <= 0.002f)
            return;

        float x = NanoVGManager.getScaledScreenWidth() * 0.5f;
        float y = NanoVGManager.getScaledScreenHeight() * 0.5f;
        String currentStyle = style.get();
        float currentSize = size.get();
        float currentGap = gap.get();
        float currentThickness = thickness.get();
        float currentScale = scaleAt(now, crosshairStartNanos);
        float currentProgress = progressAt(now, crosshairStartNanos);
        float currentAlpha = alpha;
        boolean currentBloom = bloom.get();

        Client.nanoVgManager.load(vg -> drawMarker(vg, x, y, currentStyle, currentSize,
                currentGap, currentThickness, currentScale, currentAlpha, currentProgress, currentBloom));
    }

    private void drawWorldMarkers(NVGU vg, long now) {
        String currentStyle = style.get();
        float currentSize = size.get();
        float currentGap = gap.get();
        float currentThickness = thickness.get();
        boolean currentBloom = bloom.get();

        for (Marker marker : markers) {
            if (!marker.projected)
                continue;

            float alpha = alphaAt(now, marker.startNanos);
            if (alpha <= 0.002f)
                continue;

            drawMarker(vg, marker.screenX, marker.screenY, currentStyle, currentSize,
                    currentGap, currentThickness, scaleAt(now, marker.startNanos), alpha,
                    progressAt(now, marker.startNanos), currentBloom);
        }
    }

    private boolean is3D() {
        return placement.get().equalsIgnoreCase("3D");
    }

    private int totalLifetimeMillis() {
        return Math.max(1, hangTime.get() + duration.get());
    }

    private boolean isExpired(long now, long startNanos) {
        return (now - startNanos) / 1_000_000f >= totalLifetimeMillis();
    }

    private void pruneExpired(long now) {
        markers.removeIf(marker -> isExpired(now, marker.startNanos));
    }

    private float alphaAt(long now, long startNanos) {
        float elapsedMillis = Math.max(0f, (now - startNanos) / 1_000_000f);
        float fadeElapsed = elapsedMillis - hangTime.get();
        if (fadeElapsed <= 0f || !fade.get())
            return 1f;

        float fadeMillis = Math.max(1, duration.get());
        return 1f - easeOutCubic(Math.clamp(fadeElapsed / fadeMillis, 0f, 1f));
    }

    private float scaleAt(long now, long startNanos) {
        if (!scalePop.get())
            return 1f;

        float elapsedMillis = Math.max(0f, (now - startNanos) / 1_000_000f);
        return 1f + 0.35f * (1f - easeOutCubic(Math.clamp(elapsedMillis / POP_DURATION_MS, 0f, 1f)));
    }

    private float progressAt(long now, long startNanos) {
        float elapsedMillis = Math.max(0f, (now - startNanos) / 1_000_000f);
        return Math.clamp(elapsedMillis / totalLifetimeMillis(), 0f, 1f);
    }

    private void reset() {
        markers.clear();
        crosshairStartNanos = 0L;
    }

    private static Vec3 hitPoint(LivingEntity entity) {
        AABB box = entity.getBoundingBox();
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0f);
        double reach = Math.max(3.0D, eye.distanceTo(box.getCenter()) + 1.0D);
        return box.clip(eye, eye.add(look.scale(reach))).orElseGet(box::getCenter);
    }

    private static float easeOutCubic(float value) {
        float inverse = 1f - Math.clamp(value, 0f, 1f);
        return 1f - inverse * inverse * inverse;
    }

    private static void drawMarker(NVGU vg, float x, float y, String style, float size, float gap,
                                   float thickness, float scale, float alpha, float progress, boolean bloom) {
        if (vg == null || alpha <= 0f)
            return;

        float half = Math.max(2f, size * 0.5f * scale);
        float gapPx = Math.max(0f, gap * scale);
        float strokeWidth = Math.max(1f, thickness * scale);
        int alphaInt = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        Color color = NanoVGTheme.accent(alphaInt);
        long handle = vg.getHandle();

        switch (style) {
            case "Bold X" -> {
                half *= 0.84f;
                strokeWidth *= 1.7f;
            }
            case "Long X" -> {
                half *= 1.16f;
                strokeWidth *= 0.82f;
            }
            default -> {
            }
        }

        nvgSave(handle);
        nvgGlobalCompositeOperation(handle, NVG_SOURCE_OVER);
        nvgLineCap(handle, NVG_ROUND);
        nvgLineJoin(handle, NVG_ROUND);
        nvgBeginPath(handle);

        switch (style) {
            case "Bold X", "Long X" -> drawX(handle, x, y, half, gapPx);
            case "Split X" -> drawSplitX(handle, x, y, half, gapPx);
            case "Chevron X" -> drawChevronX(handle, x, y, half, gapPx);
            case "Dotted X" -> drawDottedX(handle, x, y, half, gapPx);
            case "CS2 Ticks" -> drawTicks(handle, x, y, half, gapPx);
            case "Diamond" -> drawDiamond(handle, x, y, half, gapPx);
            case "Ring" -> drawRing(handle, x, y, half, gapPx, progress);
            default -> drawX(handle, x, y, half, gapPx);
        }

        if (bloom)
            drawBloom(handle, vg, half, strokeWidth, alphaInt);

        nvgGlobalCompositeOperation(handle, NVG_SOURCE_OVER);
        nvgStrokeWidth(handle, strokeWidth);
        nvgStrokeColor(handle, vg.createAndStoreColour(color));
        nvgStroke(handle);
        nvgRestore(handle);
    }

    private static void drawBloom(long handle, NVGU vg, float half, float strokeWidth, int alpha) {
        float base = Math.max(strokeWidth, half * 0.10f);
        Color outer = NanoVGTheme.accentEnd(255);
        Color inner = NanoVGTheme.accent(255);
        Color hot = lighten(inner, 0.24f);

        // Soft base halo keeps the marker readable over bright scenery.
        nvgStrokeWidth(handle, base * 7.5f);
        nvgStrokeColor(handle, vg.createAndStoreColour(withAlpha(outer, Math.max(1, Math.round(alpha * 0.05f)))));
        nvgStroke(handle);

        // Additive layers approximate the blur passes of a real bloom effect.
        nvgGlobalCompositeOperation(handle, NVG_LIGHTER);
        bloomLayer(handle, vg, base * 6.4f, alpha * 0.045f, outer);
        bloomLayer(handle, vg, base * 4.8f, alpha * 0.070f, outer);
        bloomLayer(handle, vg, base * 3.3f, alpha * 0.100f, inner);
        bloomLayer(handle, vg, base * 2.1f, alpha * 0.155f, hot);
        nvgGlobalCompositeOperation(handle, NVG_SOURCE_OVER);
    }

    private static void bloomLayer(long handle, NVGU vg, float width, float alpha, Color color) {
        int layerAlpha = Math.max(0, Math.min(255, Math.round(alpha)));
        if (layerAlpha == 0)
            return;

        nvgStrokeWidth(handle, width);
        nvgStrokeColor(handle, vg.createAndStoreColour(withAlpha(color, layerAlpha)));
        nvgStroke(handle);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp(alpha, 0, 255));
    }

    private static Color lighten(Color color, float amount) {
        float t = Math.clamp(amount, 0f, 1f);
        return new Color(
                Math.round(color.getRed() + (255 - color.getRed()) * t),
                Math.round(color.getGreen() + (255 - color.getGreen()) * t),
                Math.round(color.getBlue() + (255 - color.getBlue()) * t),
                color.getAlpha()
        );
    }

    private static void drawX(long handle, float x, float y, float half, float gap) {
        float inner = Math.min(gap * 0.70710678f, half * 0.8f);
        line(handle, x - inner, y - inner, x - half, y - half);
        line(handle, x + inner, y - inner, x + half, y - half);
        line(handle, x - inner, y + inner, x - half, y + half);
        line(handle, x + inner, y + inner, x + half, y + half);
    }

    private static void drawSplitX(long handle, float x, float y, float half, float gap) {
        float inner = Math.min(gap * 0.70710678f, half * 0.8f);
        float offset = Math.max(1.2f, half * 0.075f);
        splitArm(handle, x - inner, y - inner, x - half, y - half, offset);
        splitArm(handle, x + inner, y - inner, x + half, y - half, offset);
        splitArm(handle, x - inner, y + inner, x - half, y + half, offset);
        splitArm(handle, x + inner, y + inner, x + half, y + half, offset);
    }

    private static void splitArm(long handle, float x1, float y1, float x2, float y2, float offset) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001f)
            return;

        float px = -dy / length * offset;
        float py = dx / length * offset;
        line(handle, x1 + px, y1 + py, x2 + px, y2 + py);
        line(handle, x1 - px, y1 - py, x2 - px, y2 - py);
    }

    private static void drawChevronX(long handle, float x, float y, float half, float gap) {
        float inner = Math.min(gap * 0.70710678f, half * 0.8f);
        chevronArm(handle, x - inner, y - inner, x - half, y - half, half);
        chevronArm(handle, x + inner, y - inner, x + half, y - half, half);
        chevronArm(handle, x - inner, y + inner, x - half, y + half, half);
        chevronArm(handle, x + inner, y + inner, x + half, y + half, half);
    }

    private static void chevronArm(long handle, float x1, float y1, float x2, float y2, float half) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.001f)
            return;

        line(handle, x1, y1, x2, y2);

        float barb = Math.max(2f, half * 0.28f);
        float spread = barb * 0.8f;
        float ux = dx / length;
        float uy = dy / length;
        float px = -uy * spread;
        float py = ux * spread;
        line(handle, x2, y2, x2 - ux * barb + px, y2 - uy * barb + py);
        line(handle, x2, y2, x2 - ux * barb - px, y2 - uy * barb - py);
    }

    private static void drawDottedX(long handle, float x, float y, float half, float gap) {
        float inner = Math.min(gap * 0.70710678f, half * 0.8f);
        dottedArm(handle, x - inner, y - inner, x - half, y - half);
        dottedArm(handle, x + inner, y - inner, x + half, y - half);
        dottedArm(handle, x - inner, y + inner, x - half, y + half);
        dottedArm(handle, x + inner, y + inner, x + half, y + half);
    }

    private static void dottedArm(long handle, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        for (int i = 0; i < DASH_STARTS.length; i++) {
            line(handle,
                    x1 + dx * DASH_STARTS[i], y1 + dy * DASH_STARTS[i],
                    x1 + dx * DASH_ENDS[i], y1 + dy * DASH_ENDS[i]);
        }
    }

    private static void drawTicks(long handle, float x, float y, float half, float gap) {
        float inner = Math.min(gap, half * 0.8f);
        line(handle, x - inner, y, x - half, y);
        line(handle, x + inner, y, x + half, y);
        line(handle, x, y - inner, x, y - half);
        line(handle, x, y + inner, x, y + half);
    }

    private static void drawDiamond(long handle, float x, float y, float half, float gap) {
        float gapFraction = Math.clamp(gap / half, 0.02f, 0.42f);
        edge(handle, x, y - half, x + half, y, gapFraction);
        edge(handle, x + half, y, x, y + half, gapFraction);
        edge(handle, x, y + half, x - half, y, gapFraction);
        edge(handle, x - half, y, x, y - half, gapFraction);
    }

    private static void drawRing(long handle, float x, float y, float half, float gap, float progress) {
        float radius = Math.max(2f, half * (0.7f + 0.35f * Math.clamp(progress, 0f, 1f)));
        nvgCircle(handle, x, y, radius);

        float inner = radius + Math.max(1.5f, gap);
        float outer = inner + Math.max(2f, half * 0.22f);
        line(handle, x - inner, y, x - outer, y);
        line(handle, x + inner, y, x + outer, y);
        line(handle, x, y - inner, x, y - outer);
        line(handle, x, y + inner, x, y + outer);
    }

    private static void edge(long handle, float x1, float y1, float x2, float y2, float gapFraction) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        nvgMoveTo(handle, x1 + dx * gapFraction, y1 + dy * gapFraction);
        nvgLineTo(handle, x2 - dx * gapFraction, y2 - dy * gapFraction);
    }

    private static void line(long handle, float x1, float y1, float x2, float y2) {
        nvgMoveTo(handle, x1, y1);
        nvgLineTo(handle, x2, y2);
    }

    private static final class Marker {
        private final long startNanos;
        private final Vec3 position;
        private float screenX;
        private float screenY;
        private boolean projected;

        private Marker(long startNanos, Vec3 position) {
            this.startNanos = startNanos;
            this.position = position;
        }
    }
}
