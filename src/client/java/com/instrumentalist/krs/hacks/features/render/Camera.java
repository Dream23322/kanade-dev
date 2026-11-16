package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.events.features.KeyboardEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.KeyBindValue;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;

public class Camera extends Module {
    @Setting
    public static final BooleanValue freeLook = new BooleanValue("Free Look", true)
            .describe("Lets you look around without turning your character.");

    @Setting
    private final KeyBindValue freeLookKey = new KeyBindValue("Free Look Key (Bind config)", Keys.KEY_UNKNOWN, freeLook::get, KeyBindValue.ConfigStorage.BIND_CONFIG)
            .describe("Key that holds the camera still while you look around.");

    @Setting
    public static final BooleanValue skipFrontView = new BooleanValue("Skip Front View", false)
            .describe("Skips the front facing camera when you spin around.");

    @Setting
    public static final BooleanValue minimalBobbing = new BooleanValue("Minimal Bobbing", false)
            .describe("Reduces the view bobbing while walking.");

    @Setting
    public static final BooleanValue noClip = new BooleanValue("No Clip", false)
            .describe("Lets the third person camera pass through blocks.");

    @Setting
    public static final FloatValue noClipDistance = new FloatValue("No Clip Distance", 0f, -4f, 100f, "m", noClip::get)
            .describe("How far the camera is pushed forward through walls.");

    private static boolean freeLooking;
    private static float lookYaw;
    private static float lookPitch;
    private static CameraType previousCameraType;

    @Override
    public String description() {
        return "Free look, no clip camera and view bobbing tweaks that keep your aim untouched";
    }

    public Camera() {
        super("Camera", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, false);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        stopFreeLook();
    }

    @Override
    public void onWorld(WorldEvent event) {
        stopFreeLook();
    }

    @Override
    public void onKey(KeyboardEvent event) {
        int key = freeLookKey.get();
        if (key == Keys.KEY_UNKNOWN || event.key != key) return;

        if (event.action == Keys.PRESS && !freeLooking && freeLook.get() && mc.player != null && mc.gui.screen() == null)
            startFreeLook();
        else if (event.action == Keys.RELEASE)
            stopFreeLook();
    }

    private void startFreeLook() {
        freeLooking = true;
        lookYaw = mc.player.getYRot();
        lookPitch = mc.player.getXRot();
        previousCameraType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void stopFreeLook() {
        if (!freeLooking) return;

        freeLooking = false;
        if (previousCameraType != null)
            mc.options.setCameraType(previousCameraType);
        previousCameraType = null;
    }

    public static boolean shouldDisableCameraBobbing() {
        return ModuleManager.getModuleState(Camera.class) && minimalBobbing.get();
    }

    public static boolean isFreeLooking() {
        return freeLooking && ModuleManager.getModuleState(Camera.class);
    }

    public static void turn(double deltaYaw, double deltaPitch) {
        lookYaw += (float) deltaYaw * 0.15F;
        lookPitch = Mth.clamp(lookPitch + (float) deltaPitch * 0.15F, -90.0F, 90.0F);
    }

    public static float getLookYaw() {
        return lookYaw;
    }

    public static float getLookPitch() {
        return lookPitch;
    }

    public static CameraType nextCameraType(CameraType previous, CameraType next) {
        if (!ModuleManager.getModuleState(Camera.class))
            return next;

        if (next == CameraType.THIRD_PERSON_FRONT && skipFrontView.get())
            next = next.cycle();

        return next;
    }
}
