package com.instrumentalist.krs.utils.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import org.nvgu.NVGU;

import java.awt.Color;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Projects an entity's bounding box from a supplied world position and draws a
 * theme-coloured target box. Outline mode uses glowing edges; walls mode fills
 * the visible faces without the glow pass.
 */
public final class TargetBoxRenderer {
    private static final float NEAR_W = 0.05f;
    private static final float EDGE_WIDTH = 1.5f;
    private static final float GLOW_WIDTH = 4.5f;
    private static final float GLOW_ALPHA = 0.18f;
    private static final int COLOR_ALPHA = 210;
    private static final int SIDE_WALL_ALPHA = 44;
    private static final int TOP_WALL_ALPHA = 64;
    private static final float EXPAND = 0.002f;
    private static final Vector4f CLIP = new Vector4f();
    private static final float[] CLIP_CORNERS = new float[8 * 4];
    private static final int[] EDGE_FROM = new int[12];
    private static final int[] EDGE_TO = new int[12];
    private static final int TOP_FACE = 3;
    private static final int[][] FACE_CORNERS = {
            {0, 2, 6, 4}, {1, 3, 7, 5},
            {0, 1, 5, 4}, {2, 3, 7, 6},
            {0, 1, 3, 2}, {4, 5, 7, 6}
    };

    static {
        int edge = 0;
        for (int corner = 0; corner < 8; corner++) {
            for (int axis = 0; axis < 3; axis++) {
                if ((corner >> axis & 1) != 0)
                    continue;

                EDGE_FROM[edge] = corner;
                EDGE_TO[edge] = corner | 1 << axis;
                edge++;
            }
        }
    }

    public static void render(Entity entity, Vec3 position, NVGU vg) {
        render(entity, position, vg, true);
    }

    public static void render(Entity entity, Vec3 position, NVGU vg, boolean walls) {
        render(entity, position, vg, walls, null);
    }

    public static void render(Entity entity, Vec3 position, NVGU vg, boolean walls, Color accent) {
        if (entity == null || position == null || vg == null)
            return;

        if (!RenderUtil.shouldRenderWorldHudOverlays()
                || entity.isRemoved()
                || Client.mc.gui == null
                || Client.mc.gui.hud.isHidden())
            return;

        RenderUtil renderUtil = RenderUtil.INSTANCE;
        renderUtil.beginWorldProjection();

        double halfWidth = entity.getBbWidth() * 0.5D;
        AABB box = new AABB(
                position.x - halfWidth - EXPAND,
                position.y - EXPAND,
                position.z - halfWidth - EXPAND,
                position.x + halfWidth + EXPAND,
                position.y + entity.getBbHeight() + EXPAND,
                position.z + halfWidth + EXPAND
        );

        if (!renderUtil.isAabInRenderedWorldFrustum(
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ))
            return;

        if (!walls && !isVisibleThroughWorld(box))
            return;

        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? box.minX : box.maxX;
            double y = (corner & 2) == 0 ? box.minY : box.maxY;
            double z = (corner & 4) == 0 ? box.minZ : box.maxZ;
            if (!renderUtil.renderedWorldToClip(x, y, z, CLIP))
                return;

            int offset = corner * 4;
            CLIP_CORNERS[offset] = CLIP.x;
            CLIP_CORNERS[offset + 1] = CLIP.y;
            CLIP_CORNERS[offset + 2] = CLIP.z;
            CLIP_CORNERS[offset + 3] = CLIP.w;
        }

        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        if (walls)
            drawWalls(vg, box, screenWidth, screenHeight, accent);
        else
            drawWireframe(vg, screenWidth, screenHeight, accent);
    }

    private static void drawWalls(NVGU vg, AABB box, float screenWidth, float screenHeight, Color accent) {
        if (Client.mc.gameRenderer == null)
            return;

        Vec3 camera = Client.mc.gameRenderer.mainCamera().position();
        boolean inside = camera.x > box.minX && camera.x < box.maxX
                && camera.y > box.minY && camera.y < box.maxY
                && camera.z > box.minZ && camera.z < box.maxZ;
        long handle = vg.getHandle();

        for (int face = 0; face < FACE_CORNERS.length; face++) {
            if (!inside && !isCameraFacingFace(face, box, camera))
                continue;

            int[] corners = FACE_CORNERS[face];
            boolean visible = true;
            for (int corner : corners) {
                if (CLIP_CORNERS[corner * 4 + 3] < NEAR_W) {
                    visible = false;
                    break;
                }
            }
            if (!visible)
                continue;

            nvgBeginPath(handle);
            for (int index = 0; index < corners.length; index++) {
                int offset = corners[index] * 4;
                float x = (CLIP_CORNERS[offset] / CLIP_CORNERS[offset + 3] * 0.5f + 0.5f) * screenWidth;
                float y = (0.5f - CLIP_CORNERS[offset + 1] / CLIP_CORNERS[offset + 3] * 0.5f) * screenHeight;
                if (index == 0)
                    nvgMoveTo(handle, x, y);
                else
                    nvgLineTo(handle, x, y);
            }
            nvgClosePath(handle);
            int alpha = face == TOP_FACE ? TOP_WALL_ALPHA : SIDE_WALL_ALPHA;
            nvgFillColor(handle, vg.createAndStoreColour(withAlpha(accent, alpha)));
            nvgFill(handle);
        }
    }

    private static boolean isCameraFacingFace(int face, AABB box, Vec3 camera) {
        return switch (face) {
            case 0 -> camera.x < box.minX;
            case 1 -> camera.x > box.maxX;
            case 2 -> camera.y < box.minY;
            case 3 -> camera.y > box.maxY;
            case 4 -> camera.z < box.minZ;
            default -> camera.z > box.maxZ;
        };
    }

    private static void drawWireframe(NVGU vg, float screenWidth, float screenHeight, Color accent) {
        long handle = vg.getHandle();

        nvgSave(handle);
        try {
            nvgLineJoin(handle, NVG_ROUND);
            nvgLineCap(handle, NVG_ROUND);
            nvgBeginPath(handle);

            int segments = 0;
            for (int edge = 0; edge < EDGE_FROM.length; edge++) {
                int fromOffset = EDGE_FROM[edge] * 4;
                int toOffset = EDGE_TO[edge] * 4;
                float fromX = CLIP_CORNERS[fromOffset];
                float fromY = CLIP_CORNERS[fromOffset + 1];
                float fromW = CLIP_CORNERS[fromOffset + 3];
                float toX = CLIP_CORNERS[toOffset];
                float toY = CLIP_CORNERS[toOffset + 1];
                float toW = CLIP_CORNERS[toOffset + 3];

                if (fromW < NEAR_W && toW < NEAR_W)
                    continue;

                if (fromW < NEAR_W) {
                    float t = (NEAR_W - fromW) / (toW - fromW);
                    fromX += (toX - fromX) * t;
                    fromY += (toY - fromY) * t;
                    fromW = NEAR_W;
                } else if (toW < NEAR_W) {
                    float t = (NEAR_W - toW) / (fromW - toW);
                    toX += (fromX - toX) * t;
                    toY += (fromY - toY) * t;
                    toW = NEAR_W;
                }

                float startX = (fromX / fromW * 0.5f + 0.5f) * screenWidth;
                float startY = (0.5f - fromY / fromW * 0.5f) * screenHeight;
                float endX = (toX / toW * 0.5f + 0.5f) * screenWidth;
                float endY = (0.5f - toY / toW * 0.5f) * screenHeight;
                if (!Float.isFinite(startX) || !Float.isFinite(startY)
                        || !Float.isFinite(endX) || !Float.isFinite(endY))
                    continue;

                nvgMoveTo(handle, startX, startY);
                nvgLineTo(handle, endX, endY);
                segments++;
            }

            if (segments == 0)
                return;

            Color edgeColor = withAlpha(accent, COLOR_ALPHA);
            Color glowColor = withAlpha(accent,
                    Math.max(1, Math.round(COLOR_ALPHA * GLOW_ALPHA)));
            nvgStrokeWidth(handle, GLOW_WIDTH);
            nvgStrokeColor(handle, vg.createAndStoreColour(glowColor));
            nvgStroke(handle);
            nvgStrokeWidth(handle, EDGE_WIDTH);
            nvgStrokeColor(handle, vg.createAndStoreColour(edgeColor));
            nvgStroke(handle);
        } finally {
            nvgRestore(handle);
        }
    }

    private static Color withAlpha(Color color, int alpha) {
        Color base = color != null ? color : NanoVGTheme.accent(255);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private static boolean isVisibleThroughWorld(AABB box) {
        if (Client.mc.player == null || Client.mc.level == null)
            return false;

        Vec3 eye = Client.mc.player.getEyePosition();
        Vec3 center = box.getCenter();
        Vec3[] samples = {
                center,
                new Vec3(center.x, box.minY, center.z),
                new Vec3(center.x, box.maxY, center.z),
                new Vec3(box.minX, center.y, box.minZ),
                new Vec3(box.maxX, center.y, box.maxZ)
        };

        for (Vec3 sample : samples) {
            BlockHitResult hit = Client.mc.level.clip(new ClipContext(
                    eye,
                    sample,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    Client.mc.player
            ));
            if (hit.getType() == HitResult.Type.MISS)
                return true;
        }

        return false;
    }

    private TargetBoxRenderer() {
    }
}
