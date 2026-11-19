package com.vandorlabs.navigation;

/** A per-tick power demand shared by both platform energy adapters. */
public final class ObserverPower {
    public static final int RF_PER_TICK = 20;
    private long inputTick = Long.MIN_VALUE;
    private long poweredTick = Long.MIN_VALUE;
    private int received;

    public int receive(long tick, int maximum, boolean simulate) {
        int accepted = Math.min(Math.max(0, maximum), RF_PER_TICK - received(tick));
        if (!simulate && accepted > 0) {
            received = received(tick) + accepted;
            inputTick = tick;
            if (received == RF_PER_TICK) poweredTick = tick;
        }
        return accepted;
    }

    public int received(long tick) {
        return inputTick == tick ? received : 0;
    }

    public boolean powered(long tick) {
        return poweredTick == tick || poweredTick == tick - 1;
    }

    public static String[] noPowerHaiku() {
        return new String[]{"Silent dish awaits", "No power flows through its port", "Bring the map to life"};
    }
}
