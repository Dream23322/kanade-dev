package com.instrumentalist.krs.hacks.features.combat;

import java.util.ArrayDeque;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Ordered outgoing packet queue used by FakeLag. This class has no Minecraft dependency so the
 * exact queue/flush ordering can be tested.
 */
final class FakeLagQueue<T> {
    private final ArrayDeque<Entry<T>> queue = new ArrayDeque<>();
    private final Object lock = new Object();
    private final int maxQueued;
    private final Consumer<T> sender;

    private volatile boolean flushing;

    FakeLagQueue(int maxQueued, Consumer<T> sender) {
        this.maxQueued = Math.max(1, maxQueued);
        this.sender = sender;
    }

    /** Returns true when the queue reached its overflow limit. */
    boolean enqueue(T packet, long timestampNanos) {
        synchronized (lock) {
            queue.addLast(new Entry<>(packet, timestampNanos));
            return queue.size() >= maxQueued;
        }
    }

    void flush() {
        ArrayDeque<Entry<T>> pending;
        synchronized (lock) {
            if (queue.isEmpty())
                return;
            pending = new ArrayDeque<>(queue);
            queue.clear();
        }

        flushing = true;
        try {
            while (!pending.isEmpty()) {
                Entry<T> entry = pending.removeFirst();
                try {
                    sender.accept(entry.packet());
                } catch (RuntimeException ignored) {
                    pending.clear();
                }
            }
        } finally {
            flushing = false;
        }
    }

    void drop() {
        synchronized (lock) {
            queue.clear();
        }
    }

    boolean hasQueued() {
        synchronized (lock) {
            return !queue.isEmpty();
        }
    }

    long firstTimestampNanos() {
        synchronized (lock) {
            Entry<T> head = queue.peekFirst();
            return head == null ? -1L : head.timestampNanos();
        }
    }

    T firstMatching(Predicate<T> predicate) {
        synchronized (lock) {
            for (Entry<T> entry : queue) {
                if (predicate.test(entry.packet()))
                    return entry.packet();
            }
        }
        return null;
    }

    boolean isFlushing() {
        return flushing;
    }

    /**
     * Applies FakeLag's ordering-sensitive control-packet policy. {@code pass} represents handing
     * the packet back to the normal send path; the queue never sends it itself.
     */
    void handleControl(ControlAction action, T packet, Consumer<T> pass) {
        switch (action) {
            case DROP_QUEUE -> {
                drop();
                pass.accept(packet);
            }
            case NONE -> pass.accept(packet);
        }
    }

    enum ControlAction {
        NONE,
        DROP_QUEUE
    }

    private record Entry<T>(T packet, long timestampNanos) {
    }
}
