package com.instrumentalist.krs.events.features;

import com.instrumentalist.krs.events.EventArgument;
import com.instrumentalist.krs.events.EventListener;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * Fired at the head of {@code Entity.move}, so {@link #movement} is the vector that is about to be
 * applied for this tick.
 */
public class PlayerMoveEvent extends EventArgument {
    public final MoverType type;
    public Vec3 movement;

    public PlayerMoveEvent(MoverType type, Vec3 movement) {
        this.type = type;
        this.movement = movement;
    }

    @Override
    public void call(EventListener listener) {
        Objects.requireNonNull(listener).onPlayerMove(this);
    }
}
