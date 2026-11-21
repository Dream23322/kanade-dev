package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.instrumentalist.krs.utils.value.SettingValue;
import com.instrumentalist.krs.utils.value.TextValue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mid fight switch that tunes the combat modules for HvH.
 *
 * <p>Everything it changes is an ordinary module setting, so the same result can be built by
 * hand. Enabling the module snapshots the settings it is about to write, applies the selected
 * preset, and optionally turns the combat modules on. Disabling it puts the user's own values
 * back, which makes it safe to toggle in the middle of a fight.</p>
 */
public class HvH extends Module {
    private static final String KILL_AURA = "Kill Aura";
    private static final String AUTO_POT = "Auto Pot";
    private static final String VELOCITY = "Velocity";
    private static final String CRITICALS = "Criticals";
    private static final String WTAP = "WTap";
    private static final String INV_MANAGER = "Inv Manager";

    private static final List<String> FORCED_MODULES = List.of(
            KILL_AURA, AUTO_POT, VELOCITY, CRITICALS, WTAP
    );

    /** Every setting a preset may write. Snapshotted before anything is applied. */
    private static final List<String[]> TOUCHED_SETTINGS = List.of(
            new String[]{KILL_AURA, "No Hit Check"},
            new String[]{KILL_AURA, "Through Walls"},
            new String[]{KILL_AURA, "APS Mode"},
            new String[]{AUTO_POT, "Heal"},
            new String[]{AUTO_POT, "Buffs"},
            new String[]{AUTO_POT, "Heal At Health"},
            new String[]{AUTO_POT, "Panic Health"},
            new String[]{AUTO_POT, "Not Mid Fight"},
            new String[]{AUTO_POT, "Combat Range"},
            new String[]{AUTO_POT, "Avoid Others"},
            new String[]{AUTO_POT, "High Ping Confirm"},
            new String[]{AUTO_POT, "Heal Anyway"},
            new String[]{AUTO_POT, "Max Wait"},
            new String[]{AUTO_POT, "Ground Only"},
            new String[]{AUTO_POT, "Smart Heal"},
            new String[]{AUTO_POT, "DPS Window"},
            new String[]{AUTO_POT, "Ping Stop"},
            new String[]{AUTO_POT, "Ping Stop Threshold"},
            new String[]{VELOCITY, "Reduce"},
            new String[]{VELOCITY, "Only While Fighting"},
            new String[]{CRITICALS, "Rotation First"},
            new String[]{WTAP, "Ping Offset"},
            new String[]{INV_MANAGER, "Weapon Scoring"},
            new String[]{INV_MANAGER, "Armor Scoring"}
    );

    private static final List<Snapshot> snapshots = new ArrayList<>();
    private static final List<Module> forcedModules = new ArrayList<>();
    private static boolean active;

    @Setting
    private final ListValue preset = new ListValue(
            "Preset",
            new String[]{"Efficient", "Blatant"},
            "Efficient"
    ) {
        @Override
        protected void onChanged(String oldValue, String newValue) {
            // Switching the preset while the module is on re-tunes without a toggle.
            if (active) {
                applyPreset();
                notifyChanged();
            }
        }
    }.describe("Efficient stays plausible, Blatant trades subtlety for maximum damage.");

    @Setting
    private final BooleanValue forceModules = new BooleanValue(
            "Force Modules",
            true
    ).describe("Enables Kill Aura, Auto Pot, Velocity, Criticals and WTap while HvH is on.");

    @Setting
    private final BooleanValue restoreSettings = new BooleanValue(
            "Restore Settings",
            true
    ).describe("Puts your own settings back when HvH is disabled.");

    @Setting
    private final BooleanValue notify = new BooleanValue(
            "Notify",
            true
    ).describe("Shows a notification when a profile is applied or restored.");

    private record Snapshot(Module module, SettingValue<?> setting, Object value) {
    }

    @Override
    public String description() {
        return "Tunes the combat modules for HvH and can be toggled mid fight";
    }

    @Override
    public String tag() {
        return preset.get();
    }

    public HvH() {
        super("HvH", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        if (active)
            return;

        active = true;
        captureOriginals();
        applyPreset();
        if (forceModules.get())
            enableForcedModules();

        notifyChanged();
    }

    @Override
    public void onDisable() {
        if (!active)
            return;

        if (restoreSettings.get())
            restoreOriginals();

        for (Module module : forcedModules) {
            if (module.tempEnabled)
                module.setState(false);
        }
        forcedModules.clear();

        snapshots.clear();
        active = false;

        notifyChanged();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (!active || !forceModules.get())
            return;

        // Another module reset can switch the combat modules off, so keep them on while HvH is on.
        enableForcedModules();
    }

    @Override
    public void onWorld(WorldEvent event) {
        if (!active)
            return;

        // Capturing again keeps one world's tuned values from being restored into the next.
        restoreOriginals();
        snapshots.clear();
        captureOriginals();
        applyPreset();
        if (forceModules.get())
            enableForcedModules();

        notifyChanged();
    }

    /** Saved configs store the user's values, never the tuned profile. */
    public static boolean isActive() {
        return active;
    }

    public static boolean storedEnabled(Module module, boolean current) {
        if (!active)
            return current;

        return forcedModules.contains(module) ? false : current;
    }

    public static Object storedValue(Module module, String settingName, Object current) {
        if (!active)
            return current;

        for (Snapshot snapshot : snapshots) {
            if (snapshot.module() == module && snapshot.setting().name.equals(settingName))
                return snapshot.value();
        }

        return current;
    }

    private void captureOriginals() {
        snapshots.clear();

        for (String[] entry : TOUCHED_SETTINGS) {
            Module module = ModuleManager.getModuleByName(entry[0]);
            if (module == null)
                continue;

            SettingValue<?> setting = findSetting(module, entry[1]);
            if (setting != null)
                snapshots.add(new Snapshot(module, setting, setting.get()));
        }
    }

    private void restoreOriginals() {
        for (Snapshot snapshot : snapshots)
            setValue(snapshot.setting(), snapshot.value());
    }

    private void applyPreset() {
        boolean blatant = preset.get().equalsIgnoreCase("Blatant");

        apply(KILL_AURA, "No Hit Check", blatant);
        apply(KILL_AURA, "Through Walls", true);
        apply(KILL_AURA, "APS Mode", "Hurt Math");

        apply(AUTO_POT, "Heal", true);
        apply(AUTO_POT, "Buffs", true);
        apply(AUTO_POT, "Heal At Health", blatant ? 16f : 14f);
        apply(AUTO_POT, "Panic Health", blatant ? 10f : 8f);
        apply(AUTO_POT, "Not Mid Fight", true);
        apply(AUTO_POT, "Combat Range", 6f);
        apply(AUTO_POT, "Avoid Others", true);
        apply(AUTO_POT, "High Ping Confirm", true);
        apply(AUTO_POT, "Heal Anyway", blatant);
        apply(AUTO_POT, "Max Wait", 10);
        apply(AUTO_POT, "Ground Only", true);
        apply(AUTO_POT, "Smart Heal", true);
        apply(AUTO_POT, "DPS Window", 60);
        apply(AUTO_POT, "Ping Stop", true);
        apply(AUTO_POT, "Ping Stop Threshold", 150);

        apply(VELOCITY, "Reduce", blatant ? 100f : 90f);
        apply(VELOCITY, "Only While Fighting", !blatant);

        apply(CRITICALS, "Rotation First", true);
        apply(WTAP, "Ping Offset", true);
        apply(INV_MANAGER, "Weapon Scoring", "DPS");
        apply(INV_MANAGER, "Armor Scoring", "EPF");
    }

    private void enableForcedModules() {
        for (String name : FORCED_MODULES) {
            Module module = ModuleManager.getModuleByName(name);
            if (module == null || module.tempEnabled)
                continue;

            module.setState(true);
            if (!forcedModules.contains(module))
                forcedModules.add(module);
        }
    }

    private void notifyChanged() {
        AutoPot autoPot = ModuleManager.getModule(AutoPot.class);
        if (autoPot != null)
            autoPot.onProfileChanged();

        if (notify.get() && Client.notificationManager != null)
            Client.notificationManager.addNotification("HvH",
                    preset.get() + " profile " + (active ? "applied" : "restored"));
    }

    private void apply(String moduleName, String settingName, Object value) {
        Module module = ModuleManager.getModuleByName(moduleName);
        if (module == null)
            return;

        SettingValue<?> setting = findSetting(module, settingName);
        if (setting != null)
            setValue(setting, value);
    }

    private static SettingValue<?> findSetting(Module module, String settingName) {
        for (Field field : ModuleManager.getSettings(module)) {
            try {
                if (field.get(module) instanceof SettingValue<?> setting && setting.name.equals(settingName))
                    return setting;
            } catch (IllegalAccessException ignored) {
            }
        }

        return null;
    }

    private static void setValue(SettingValue<?> setting, Object value) {
        if (value == null)
            return;

        switch (setting) {
            case BooleanValue booleanValue when value instanceof Boolean bool -> booleanValue.set(bool);
            case FloatValue floatValue when value instanceof Number number -> floatValue.set(number.floatValue());
            case IntValue intValue when value instanceof Number number -> intValue.set(number.intValue());
            case ListValue listValue when value instanceof String string -> listValue.set(string);
            case TextValue textValue when value instanceof String string -> textValue.set(string);
            default -> {
            }
        }
    }
}
