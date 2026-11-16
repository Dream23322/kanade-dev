package com.instrumentalist.krs.utils;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class PerspectiveUtil {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Set<Object> holders = Collections.newSetFromMap(new IdentityHashMap<>());
    private static CameraType previousCameraType;

    private PerspectiveUtil() {
    }

    public static void update(Object owner, boolean active) {
        if (owner == null || mc.options == null)
            return;

        if (active) {
            if (holders.isEmpty())
                previousCameraType = mc.options.getCameraType();

            holders.add(owner);
            if (mc.options.getCameraType().isFirstPerson())
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (holders.remove(owner) && holders.isEmpty()) {
            if (previousCameraType != null)
                mc.options.setCameraType(previousCameraType);

            previousCameraType = null;
        }
    }

    public static void release(Object owner) {
        update(owner, false);
    }
}
