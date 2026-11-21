package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.ListValue;

import java.util.Locale;

public class Ambience extends Module {

    @Setting
    private static final ListValue weather = new ListValue(
            "Weather",
            new String[]{"Off", "Clear", "Rain", "Thunder"},
            "Off"
    ).describe("Client side weather.");

    @Setting
    private static final ListValue time = new ListValue(
            "Time of Day",
            new String[]{"Vanilla", "Day", "Night", "Midnight", "Sunrise"},
            "Vanilla"
    ).describe("Time of day rendered on your side.");

    @Setting
    private static final ListValue stretch = new ListValue(
            "Stretch",
            new String[]{"Off", "4:3", "5:4", "16:10", "16:9", "21:9", "Custom"},
            "Off"
    ).describe("Stretches the image horizontally, as if the game rendered at that aspect ratio and was stretched to fill your screen.");

    @Setting
    private static final FloatValue customStretch = new FloatValue(
            "Custom Stretch",
            2f,
            1f,
            4f,
            "x",
            () -> "Custom".equalsIgnoreCase(stretch.get())
    ).describe("Horizontal stretch used by the Custom preset. 2x renders at half width.");

    private static final Long[] TIME_TICKS = {null, 1000L, 13000L, 18000L, 0L};

    @Override
    public String description() {
        return "Client side weather, time of day and stretched resolutions";
    }

    public Ambience() {
        super("Ambience", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    /**
     * Width the projection should use for the current stretch preset, so the image comes out
     * stretched horizontally. Presets imitate rendering at that aspect ratio.
     */
    public static float stretchWidth(float width, float height) {
        if (!ModuleManager.getModuleState(Ambience.class) || height <= 0f)
            return width;

        String mode = stretch.get();
        if ("Custom".equalsIgnoreCase(mode))
            return width / Math.max(1f, customStretch.get());

        float targetAspect = switch (mode.toLowerCase(Locale.ROOT)) {
            case "4:3" -> 4f / 3f;
            case "5:4" -> 5f / 4f;
            case "16:10" -> 16f / 10f;
            case "16:9" -> 16f / 9f;
            case "21:9" -> 21f / 9f;
            default -> 0f;
        };

        return targetAspect <= 0f ? width : height * targetAspect;
    }

    public static Long getActiveTimeTicks() {
        if (!ModuleManager.getModuleState(Ambience.class) || mc.level == null)
            return null;

        int index = time.getCurrentIndex();
        return index >= 0 && index < TIME_TICKS.length ? TIME_TICKS[index] : null;
    }

    public static float rainLevelOverride(float original) {
        if (!ModuleManager.getModuleState(Ambience.class))
            return original;

        return switch (weather.get().toLowerCase(Locale.ROOT)) {
            case "clear" -> 0f;
            case "rain", "thunder" -> 1f;
            default -> original;
        };
    }

    public static float thunderLevelOverride(float original) {
        if (!ModuleManager.getModuleState(Ambience.class))
            return original;

        return switch (weather.get().toLowerCase(Locale.ROOT)) {
            case "clear", "rain" -> 0f;
            case "thunder" -> 1f;
            default -> original;
        };
    }

    public static boolean rainingOverride(boolean original) {
        if (!ModuleManager.getModuleState(Ambience.class))
            return original;

        return switch (weather.get().toLowerCase(Locale.ROOT)) {
            case "clear" -> false;
            case "rain", "thunder" -> true;
            default -> original;
        };
    }

    public static boolean thunderingOverride(boolean original) {
        if (!ModuleManager.getModuleState(Ambience.class))
            return original;

        return switch (weather.get().toLowerCase(Locale.ROOT)) {
            case "clear", "rain" -> false;
            case "thunder" -> true;
            default -> original;
        };
    }
}
