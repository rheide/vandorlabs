package com.vandorlabs.gear.core;

import java.util.function.Supplier;

/** The selected one-way connection through an arm. The arm owns no resource buffer. */
public enum ArmResource {
    DISABLED("Off"), POWER("RF Power"), FLUID("Fluids"), ITEM("Items");

    public static final String CHOICE = "ResourceTransfer";
    public static final String OUT = "ResourceOut", SAVED_OUT = "ArmResourceOut";
    public static boolean output(boolean present,boolean bool,int value){if(present&&(!bool||value<0||value>1))throw new IllegalArgumentException("Invalid arm resource direction");return present&&value==1;}
    public static final String SAVED = "ArmResourceTransfer";
    private static final ArmResource[] MODES = values();
    private static final ThreadLocal<Route> ROUTE = ThreadLocal.withInitial(Route::new);
    private static final class Route {
        final Object[] inputs = new Object[32];
        int depth;
    }
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
        Route route = ROUTE.get();
        if (route.depth == route.inputs.length) return unavailable;
        for (int index=0;index<route.depth;index++) {
            if (route.inputs[index] == input) return unavailable;
        }
        route.inputs[route.depth++] = input;
        try {
            return delivery.get();
        } finally {
            route.inputs[--route.depth] = null;
        }
    }
}
