package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.ListValue;

public class TargetHud extends Module {

    @Setting
    private static final ListValue mode = new ListValue(
            "Mode",
            new String[]{"3D", "2D"},
            "3D"
    ).describe("Style used by the target hud.");

    private long lastRenderNanos;

    @Override
    public String description() {
        return "Shows information about your current target";
    }

    public TargetHud() {
        super("Target Hud", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, true);
    }

    public static boolean is2D() {
        return mode.get().equalsIgnoreCase("2D");
    }

    @Override
    public void onEnable() {
        lastRenderNanos = 0L;
    }

    @Override
    public void onDisable() {
        lastRenderNanos = 0L;
        Interface.clearTargetHudTracking();
    }

    @Override
    public void onWorld(WorldEvent event) {
        lastRenderNanos = 0L;
        Interface.clearTargetHudTracking();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        Interface.collectTargetHudData(event);
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden())
            return;

        long now = System.nanoTime();
        long deltaNanos = lastRenderNanos == 0L ? 16_666_667L : now - lastRenderNanos;
        lastRenderNanos = now;
        long deltaTime = Math.clamp(deltaNanos / 1_000_000L, 1L, 100L);
        float deltaSpeed = Math.min(0.16f * (deltaTime / 16f), 1.0f);

        if (Client.nanoVgManager == null)
            return;

        Client.nanoVgManager.loadWorldAnchored(vg -> {
            if (!tempEnabled)
                return;

            float interfaceAlpha = com.instrumentalist.krs.utils.nanovg.NanoVGManager.shouldRenderBelowDebugOverlay() ? 0.2f : 1f;
            vg.globalAlpha(interfaceAlpha, () -> Interface.renderTargetHudModule(vg, deltaSpeed));
        });
    }
}
