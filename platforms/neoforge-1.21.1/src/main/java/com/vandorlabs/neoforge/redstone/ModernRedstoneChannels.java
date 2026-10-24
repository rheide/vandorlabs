package com.vandorlabs.neoforge.redstone;

import com.vandorlabs.redstone.ChannelLatch;
import com.vandorlabs.redstone.ChannelList;
import com.vandorlabs.redstone.ChannelMember;
import com.vandorlabs.redstone.ChannelNetwork;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Server-level lifecycle adapter over exactly the same channel policy used by Forge. */
@EventBusSubscriber(modid = "vandorlabs")
public final class ModernRedstoneChannels {
    private static final Map<ServerLevel, State> LEVELS = new IdentityHashMap<>();
    private ModernRedstoneChannels() { }

    private static ServerLevel level(ModernChannelMember member) {
        BlockEntity owner = member.channelBlockEntity();
        if (owner == null || !(owner.getLevel() instanceof ServerLevel level)) return null;
        checkThread(level);
        return level;
    }

    private static void checkThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Channel world access requires the server thread");
    }

    private static State state(ServerLevel level) { return LEVELS.computeIfAbsent(level, unused -> new State()); }

    /** Schedule one derived update after all synchronous channel callbacks have settled. */
    public static void afterSignals(ModernChannelMember member, Runnable action) {
        ServerLevel level = level(member);
        if (level != null) ModernSignalBatch.afterSignals(member, action);
    }

    /** Safe from onLoad: membership and neighbour-dependent callbacks run after loading, at level tick end. */
    public static void queueRegistration(ModernChannelMember member) {
        ServerLevel level = level(member);
        if (level != null) state(level).pending.add(member);
    }

    /** Call after an authoritative channel configuration change on an installed block entity. */
    public static void channelsChanged(ModernChannelMember member) {
        ServerLevel level = level(member);
        if (level == null || !member.isChannelMemberActive()) return;
        State state = state(level);
        state.network.register(member);
    }

    public static void unregister(ModernChannelMember member) {
        ServerLevel level = level(member);
        if (level == null) return;
        State state = LEVELS.get(level);
        if (state != null) state.remove(member);
    }

    public static void inputChanged(ModernChannelMember member) {
        ServerLevel level = level(member);
        if (level == null || !member.isChannelMemberActive()) return;
        State state = LEVELS.get(level);
        if (state != null) state.network.inputChanged(member);
    }

    public static void latchLevelChanged(ModernChannelMember member, int value) {
        ServerLevel level = level(member);
        if (level == null || value < 0 || value > 15 || !member.isChannelMemberActive()
                || !(member instanceof ChannelLatch latch)) return;
        State state = LEVELS.get(level);
        if (state != null) state.network.latchChanged(latch, value);
    }

    /** Timed power remains independent of persistent latch levels and never holds a linked toggle on. */
    public static void pulse(ModernChannelMember source, int value, int ticks) {
        ServerLevel level = level(source);
        if (level == null || !source.isChannelMemberActive() || value < 1 || value > 15 || ticks < 1) return;
        State state = state(level);
        state.network.beginTransfer();
        try {
            Pulse old = state.pulses.remove(source);
            if (old != null) state.network.unregister(old);
            Pulse pulse = new Pulse(source, source.getRedstoneChannels(), value, level.getGameTime() + ticks);
            state.pulses.put(source, pulse);
            state.network.register(pulse);
        } finally { state.network.endTransfer(); }
    }

    public static int level(ServerLevel level, int channel) {
        checkThread(level);
        State state = LEVELS.get(level);
        return state == null ? 0 : state.network.level(channel);
    }

    public static boolean allPowered(ServerLevel level, ChannelList channels) {
        checkThread(level);
        State state = LEVELS.get(level);
        return state != null && state.network.allPowered(channels);
    }

    public static void beginTransfer(ServerLevel level) { checkThread(level); state(level).network.beginTransfer(); }
    public static void endTransfer(ServerLevel level) {
        checkThread(level);
        State state = LEVELS.get(level);
        if (state == null) throw new IllegalStateException("Unbalanced channel transaction");
        state.network.endTransfer();
    }

    @SubscribeEvent public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        State state = LEVELS.get(level);
        if (state == null) return;
        // Recheck identity: a queued block may have been removed or replaced while its chunk loaded.
        if (!state.pending.isEmpty()) {
            Set<ModernChannelMember> pending = identitySet();
            pending.addAll(state.pending); state.pending.clear();
            for (ModernChannelMember member : pending) if (member.isChannelMemberActive()) {
                state.network.register(member);
                if (member.isChannelMemberActive()) member.channelLoaded();
            }
        }
        if (state.pulses.isEmpty()) return;
        for (Map.Entry<ModernChannelMember, Pulse> entry : new ArrayList<>(state.pulses.entrySet())) {
            Pulse pulse = entry.getValue();
            if (!pulse.isChannelMemberActive() || level.getGameTime() >= pulse.expires) {
                state.pulses.remove(entry.getKey());
                state.network.unregister(pulse);
            }
        }
    }

    @SubscribeEvent public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) LEVELS.remove(level);
    }

    private static <T> Set<T> identitySet() { return Collections.newSetFromMap(new IdentityHashMap<>()); }

    private static final class State {
        final ChannelNetwork network = new ChannelNetwork(ModernSignalBatch::apply);
        final Set<ModernChannelMember> pending = identitySet();
        final Map<ModernChannelMember, Pulse> pulses = new IdentityHashMap<>();
        void remove(ModernChannelMember member) {
            pending.remove(member);
            network.unregister(member);
            Pulse pulse = pulses.remove(member);
            if (pulse != null) network.unregister(pulse);
        }
    }

    private record Pulse(ModernChannelMember source, ChannelList channels, int value, long expires) implements ChannelMember {
        @Override public ChannelList getRedstoneChannels() { return channels; }
        @Override public boolean isChannelMemberActive() { return source.isChannelMemberActive(); }
        @Override public int localSignalLevel(int channel) { return value; }
        @Override public void setChannelLevel(int level) { }
    }
}
