package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.redstone.ChannelMember;
import java.util.LinkedHashMap;
import java.util.Map;

/** Coalesces physical neighbour updates until synchronous shared-channel settlement completes. */
final class ModernSignalBatch {
    private static final ThreadLocal<Map<ChannelMember, Runnable>> PENDING = new ThreadLocal<>();
    private ModernSignalBatch() { }
    static void afterSignals(ChannelMember member, Runnable action) {
        Map<ChannelMember, Runnable> pending = PENDING.get();
        if (pending == null) action.run(); else pending.put(member, action);
    }
    static void apply(Runnable signals) {
        if (PENDING.get() != null) { signals.run(); return; }
        Map<ChannelMember, Runnable> pending = new LinkedHashMap<>();
        PENDING.set(pending);
        try { signals.run(); }
        finally {
            PENDING.remove();
            for (Map.Entry<ChannelMember, Runnable> entry : pending.entrySet()) {
                if (entry.getKey().isChannelMemberActive()) entry.getValue().run();
            }
        }
    }
}
