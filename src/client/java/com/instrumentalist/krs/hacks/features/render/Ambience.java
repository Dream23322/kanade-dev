package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.BooleanValue;
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
    private static final BooleanValue stretch = new BooleanValue("Stretch", false)
            .describe("Stretches the field of view like the widely putin meme.");

    private static final Long[] TIME_TICKS = {null, 1000L, 13000L, 18000L, 0L};

    @Override
    public String description() {
        return "Client side weather, time of day and stretched field of view";
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

    public static boolean isStretch() {
        return ModuleManager.getModuleState(Ambience.class) && stretch.get();
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
