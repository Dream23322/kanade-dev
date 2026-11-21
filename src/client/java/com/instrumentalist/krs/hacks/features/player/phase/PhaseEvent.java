package com.instrumentalist.krs.hacks.features.player.phase;

import com.instrumentalist.krs.events.features.BlockEvent;
import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.utils.IMinecraft;

public interface PhaseEvent extends IMinecraft {
    String getName();

    default void onEnable() {
    }

    default void onDisable() {
    }

    default void onUpdate(UpdateEvent event) {
    }

    default void onMotion(MotionEvent event) {
    }

    default void onBlock(BlockEvent event) {
    }

    default void onReceivedPacket(ReceivedPacketEvent event) {
    }

    default void onWorld(WorldEvent event) {
    }
}
