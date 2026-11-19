package com.vandorlabs.gear.core;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Supplier;

/** The selected one-way connection through an arm. The arm owns no resource buffer. */
public enum ArmResource {
    DISABLED("Off"), POWER("RF Power"), FLUID("Fluids"), ITEM("Items");

    public static final String CHOICE = "ResourceTransfer";
    public static final String SAVED = "ArmResourceTransfer";
    private static final ArmResource[] MODES = values();
    private static final ThreadLocal<Set<Object>> ROUTE = new ThreadLocal<>();
    private final String label;

    ArmResource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ArmResource read(boolean present, boolean integer, int value) {
        if (!present) return DISABLED;
        if (!integer || value < 0 || value >= MODES.length) throw new IllegalArgumentException("Invalid arm resource transfer");
        return MODES[value];
    }

    public static ArmResource cycle(ArmResource previous, int step) {
        return MODES[Math.floorMod(previous.ordinal() + step, MODES.length)];
    }

    /** A downstream pipe may call back into this connection, directly or through another arm. */
    public static <T> T forward(Object input, T unavailable, Supplier<T> delivery) {
        Set<Object> route = ROUTE.get();
        if (route == null) {
            route = Collections.newSetFromMap(new IdentityHashMap<>());
            ROUTE.set(route);
        }
        if (route.size() >= 32 || !route.add(input)) return unavailable;
        try {
            return delivery.get();
        } finally {
            route.remove(input);
            if (route.isEmpty()) ROUTE.remove();
        }
    }
}
