package com.vandorlabs.redstone;

import java.util.LinkedHashMap;
import java.util.Map;

/** Coalesces derived work until all synchronous channel signals have been applied. */
public final class SignalUpdateBatch {
    private static final ThreadLocal<Map<Object, Runnable>> PENDING = new ThreadLocal<>();
    private SignalUpdateBatch() { }

    public static boolean isActive() { return PENDING.get() != null; }

    /** The key identifies one settlement job, even through nested channel notifications. */
    public static void afterSignals(Object key, Runnable action) {
        Map<Object,Runnable> pending=PENDING.get();
        if (pending==null) throw new IllegalStateException("no active signal batch");
        pending.put(key,action);
    }

    static void apply(Runnable signals) {
        if (isActive()) {
            signals.run();
            return;
        }
        Map<Object,Runnable> pending=new LinkedHashMap<>();
        PENDING.set(pending);
        try {
            signals.run();
        } finally {
            // Settlements may deliver further channel signals; those get a new scope.
            PENDING.remove();
            for (Runnable action:pending.values()) action.run();
        }
    }
}
