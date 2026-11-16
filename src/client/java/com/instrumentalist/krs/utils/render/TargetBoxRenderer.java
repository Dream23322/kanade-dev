package com.instrumentalist.krs.utils.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.utils.nanovg.NanoVGManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;
import org.nvgu.NVGU;

import java.awt.Color;

/**
 * Projects an entity's bounding box from a supplied world position and draws a thin
 * theme-coloured outline around it. Used by modules that want a simple target box.
 */
public final class TargetBoxRenderer {
    private static final float PADDING = 1.5f;
    private static final float THICKNESS = 1.5f;
    private static final int COLOR_ALPHA = 210;
    private static final Vector4f CLIP = new Vector4f();

    public static void render(Entity entity, Vec3 position, NVGU vg) {
        if (entity == null || position == null || vg == null)
            return;

        if (!RenderUtil.shouldRenderWorldHudOverlays()
                || entity.isRemoved()
                || Client.mc.gui == null
                || Client.mc.gui.hud.isHidden())
            return;

        RenderUtil.INSTANCE.beginWorldProjection();

        double halfWidth = entity.getBbWidth() * 0.5D;
        AABB box = new AABB(
                position.x - halfWidth,
                position.y,
                position.z - halfWidth,
                position.x + halfWidth,
                position.y + entity.getBbHeight(),
                position.z + halfWidth
        );

        float screenWidth = NanoVGManager.getScaledScreenWidth();
        float screenHeight = NanoVGManager.getScaledScreenHeight();
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        int projected = 0;

        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? box.minX : box.maxX;
            double y = (corner & 2) == 0 ? box.minY : box.maxY;
            double z = (corner & 4) == 0 ? box.minZ : box.maxZ;
            if (!RenderUtil.INSTANCE.renderedWorldToClip(x, y, z, CLIP) || CLIP.w <= 0.05f)
                continue;

            float screenX = ((CLIP.x / CLIP.w) * 0.5f + 0.5f) * screenWidth;
            float screenY = (0.5f - (CLIP.y / CLIP.w) * 0.5f) * screenHeight;
            if (!Float.isFinite(screenX) || !Float.isFinite(screenY))
                continue;

            minX = Math.min(minX, screenX);
            minY = Math.min(minY, screenY);
            maxX = Math.max(maxX, screenX);
            maxY = Math.max(maxY, screenY);
            projected++;
        }

        if (projected < 4)
            return;
        minX = Math.clamp(minX - PADDING, 0f, screenWidth);
        minY = Math.clamp(minY - PADDING, 0f, screenHeight);
        maxX = Math.clamp(maxX + PADDING, 0f, screenWidth);
        maxY = Math.clamp(maxY + PADDING, 0f, screenHeight);

        if (maxX - minX < 2f || maxY - minY < 2f)
            return;

        Color color = NanoVGTheme.accent(COLOR_ALPHA);
        vg.rectangle(minX, minY, maxX - minX, THICKNESS, color);
        vg.rectangle(minX, maxY - THICKNESS, maxX - minX, THICKNESS, color);
        vg.rectangle(minX, minY, THICKNESS, maxY - minY, color);
        vg.rectangle(maxX - THICKNESS, minY, THICKNESS, maxY - minY, color);
    }

    private TargetBoxRenderer() {
    }
}
