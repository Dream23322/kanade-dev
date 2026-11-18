package com.instrumentalist.krs.utils.entity;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.EventListener;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.events.features.WorldLeaveEvent;
import com.instrumentalist.krs.utils.IMinecraft;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.util.EnumSet;
import java.util.Set;

/**
 * Briefly toggles every enabled skin layer off and back on after joining a world.
 *
 * <p>The change is sent to the server so the player's synced model customization is
 * refreshed, then the original per-layer settings are restored.</p>
 */
public final class SkinLayerToggler implements EventListener {

    private static final int JOIN_SETTLE_TICKS = 4;
    private static final int LAYER_OFF_TICKS = 6;
    private static final PlayerModelPart[] PARTS = PlayerModelPart.values();

    private static final SkinLayerToggler INSTANCE = new SkinLayerToggler();
    private static boolean registered;

    private final Set<PlayerModelPart> enabledBefore = EnumSet.noneOf(PlayerModelPart.class);

    private Stage stage = Stage.IDLE;
    private ClientLevel targetLevel;
    private int waitTicks;
    private boolean layersDisabled;

    private enum Stage {
        IDLE,
        WAITING_FOR_WORLD,
        WAITING_TO_RESTORE
    }

    private SkinLayerToggler() {
    }

    public static void init() {
        if (registered || Client.eventManager == null)
            return;

        Client.eventManager.register(INSTANCE);
        registered = true;
    }

    public static void shutdown() {
        INSTANCE.restoreLayers(false);
        INSTANCE.reset();

        if (registered && Client.eventManager != null)
            Client.eventManager.unregister(INSTANCE);
        registered = false;
    }

    @Override
    public void onWorld(WorldEvent event) {
        restoreLayers(true);
        reset();

        if (event.world == null || event.previousWorld != null)
            return;

        targetLevel = event.world;
        stage = Stage.WAITING_FOR_WORLD;
        waitTicks = JOIN_SETTLE_TICKS;
    }

    @Override
    public void onWorldLeave(WorldLeaveEvent event) {
        restoreLayers(false);
        reset();
    }

    @Override
    public void onTick(TickEvent event) {
        switch (stage) {
            case WAITING_FOR_WORLD -> tickWaitingForWorld();
            case WAITING_TO_RESTORE -> tickWaitingToRestore();
            case IDLE -> {
            }
        }
    }

    private void tickWaitingForWorld() {
        LocalPlayer player = mc.player;
        if (targetLevel == null
                || mc.level != targetLevel
                || player == null
                || player.connection == null)
            return;

        if (waitTicks-- > 0)
            return;

        beginToggle(player);
    }

    private void tickWaitingToRestore() {
        if (waitTicks-- > 0)
            return;

        restoreLayers(true);
        stage = Stage.IDLE;
        targetLevel = null;
    }

    private void beginToggle(LocalPlayer player) {
        Options options = mc.options;
        if (options == null || player.connection == null) {
            reset();
            return;
        }

        enabledBefore.clear();
        for (PlayerModelPart part : PARTS) {
            if (options.isModelPartEnabled(part))
                enabledBefore.add(part);
        }

        // Nothing to show if every layer was already disabled.
        if (enabledBefore.isEmpty()) {
            reset();
            return;
        }

        for (PlayerModelPart part : PARTS)
            options.setModelPart(part, false);

        layersDisabled = true;
        options.broadcastOptions();
        stage = Stage.WAITING_TO_RESTORE;
        waitTicks = LAYER_OFF_TICKS;
    }

    private void restoreLayers(boolean broadcast) {
        if (!layersDisabled)
            return;

        Options options = mc.options;
        if (options != null) {
            for (PlayerModelPart part : PARTS)
                options.setModelPart(part, enabledBefore.contains(part));

            LocalPlayer player = mc.player;
            if (broadcast && player != null && player.connection != null)
                options.broadcastOptions();
        }

        layersDisabled = false;
        enabledBefore.clear();
    }

    private void reset() {
        stage = Stage.IDLE;
        targetLevel = null;
        waitTicks = 0;
    }
}
