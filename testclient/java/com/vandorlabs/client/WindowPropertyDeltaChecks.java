package com.vandorlabs.client;

import com.vandorlabs.container.WindowPropertyDeltas;
import java.io.*;
import java.util.*;

/** Dependency-free checks of the production sender/receiver using signed-short wire data. */
public final class WindowPropertyDeltaChecks {
    private static final class Listener {
        final int[] values = new int[3];
        final List<Integer> properties = new ArrayList<>();
        int messages;
        void receive(int property, int value) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream(4);
                DataOutputStream output = new DataOutputStream(bytes);
                output.writeShort(property);
                output.writeShort(value);
                DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
                WindowPropertyDeltas.receive(values, input.readShort(), input.readShort());
                properties.add(property);
                messages++;
            } catch (IOException failure) { throw new AssertionError(failure); }
        }
        void reset() { messages = 0; properties.clear(); }
        // Listener identity, rather than a coincidentally equal value, owns a snapshot.
        @Override public boolean equals(Object other) { return other instanceof Listener; }
        @Override public int hashCode() { return 1; }
    }
    private static void need(boolean yes, String message) { if (!yes) throw new AssertionError(message); }
    private static WindowPropertyDeltas<Listener> deltas() {
        return new WindowPropertyDeltas<>((listener, property, value) -> listener.receive(property, value));
    }
    private static void fullInts() {
        WindowPropertyDeltas<Listener> deltas = deltas();
        Listener listener = new Listener();
        int[] values = {0, 100000, -1};
        deltas.sendChanges(listener, values);
        need(listener.messages == 6 && Arrays.equals(listener.values, values), "complete initial zero/capacity/color snapshot");
        int[] edges = {0, 1, 32767, 32768, 65535, 65536, 65537, 0x7fff0000,
                Integer.MAX_VALUE, Integer.MIN_VALUE, -65536, -1, 0xff3f76e4, 0xffff831a};
        for (int field = 0; field < values.length; field++) for (int value : edges) {
            int expected = values[field] == value ? 0 : 2;
            values[field] = value;
            listener.reset();
            deltas.sendChanges(listener, values);
            need(listener.messages == expected, "single field must send a complete pair");
            need(Arrays.equals(listener.values, values), "signed/full-width value truncated: " + value);
            if (expected != 0) need(listener.properties.equals(Arrays.asList(field * 2, field * 2 + 1)), "wrong pair/order");
        }
        Random random = new Random(0x51a7eL);
        for (int trial = 0; trial < 10000; trial++) {
            for (int field = 0; field < values.length; field++) values[field] = random.nextInt();
            deltas.sendChanges(listener, values);
            need(Arrays.equals(listener.values, values), "random full-width round trip");
        }
        int[] before = listener.values.clone();
        WindowPropertyDeltas.receive(listener.values, -1, 7);
        WindowPropertyDeltas.receive(listener.values, 6, 7);
        need(Arrays.equals(listener.values, before), "unknown property changed meter values");
    }
    private static void listenerLifecycle() {
        WindowPropertyDeltas<Listener> deltas = deltas();
        Listener first = new Listener(), late = new Listener();
        int[] values = {65536, 100000, 0xff3f76e4};
        deltas.sendChanges(first, values);
        first.reset();
        deltas.sendChanges(first, values);
        deltas.sendChanges(late, values);
        need(first.messages == 0 && late.messages == 6, "late listener inherited another listener's baseline");
        need(Arrays.equals(late.values, values), "late listener did not receive exact current snapshot");
        values[0]++;
        deltas.sendChanges(first, values);
        need(first.messages == 2 && late.values[0] != values[0], "listener baselines were shared");
        late.reset();
        deltas.sendChanges(late, values);
        need(late.messages == 2 && Arrays.equals(late.values, values), "pending listener delta lost");
        deltas.removeListener(first);
        first.reset();
        deltas.sendChanges(first, values);
        need(first.messages == 6, "removed listener snapshot retained on re-add");
        late.reset();
        deltas.sendChanges(late, values);
        need(late.messages == 0, "removing one listener disturbed another");
    }
    private static void messageCounts() {
        WindowPropertyDeltas<Listener> deltas = deltas();
        Listener listener = new Listener();
        int[] values = {0, 100000, -1};
        deltas.sendChanges(listener, values);
        int initial = listener.messages;
        for (int tick = 0; tick < 1000; tick++) deltas.sendChanges(listener, values);
        int idle = listener.messages - initial;
        for (int tick = 0; tick < 100; tick++) { values[0]++; deltas.sendChanges(listener, values); }
        int active = listener.messages - initial - idle;
        need(initial == 6 && idle == 0 && active == 200 && listener.messages == 206, "message-count regression");
        System.out.println("storage-window-properties initial before=6 after=" + initial
                + "; 1000 idle samples before=6000 after=" + idle
                + "; 100 amount changes before=600 after=" + active
                + "; total before=6606 after=" + listener.messages);
    }
    public static void main(String[] args) {
        fullInts(); listenerLifecycle(); messageCounts();
        System.out.println("window-property-deltas PASS: exact signed 32-bit values, both halves, per-listener initial state, removal/re-add");
    }
    private WindowPropertyDeltaChecks() { }
}
