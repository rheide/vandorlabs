package com.vandorlabs.container;

import java.util.IdentityHashMap;
import java.util.Map;

/** Exact int snapshots transported as the two signed-short window properties. */
public final class WindowPropertyDeltas<L> {
    @FunctionalInterface
    public interface Sender<L> {
        void send(L listener, int property, int value);
    }

    private final Map<L, int[]> snapshots = new IdentityHashMap<>();
    private final Sender<L> sender;

    public WindowPropertyDeltas(Sender<L> sender) {
        this.sender = sender;
    }

    /** A new listener always receives every field, including zero-valued fields. */
    public void sendChanges(L listener, int[] values) {
        int[] previous = snapshots.get(listener);
        boolean initial = previous == null || previous.length != values.length;
        if (initial) previous = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            int value = values[i];
            if (initial || previous[i] != value) {
                // Always send a complete pair, even when only one half changed.
                sender.send(listener, i * 2, value & 0xffff);
                sender.send(listener, i * 2 + 1, value >>> 16);
                previous[i] = value;
            }
        }
        if (initial) snapshots.put(listener, previous);
    }

    public void removeListener(L listener) {
        snapshots.remove(listener);
    }

    /** Both halves may be sign-extended by the vanilla short-valued packet. */
    public static void receive(int[] values, int property, int data) {
        if (property < 0 || property >= values.length * 2) return;
        int index = property / 2;
        values[index] = (property & 1) == 0
                ? (values[index] & 0xffff0000) | (data & 0xffff)
                : (values[index] & 0xffff) | ((data & 0xffff) << 16);
    }
}
