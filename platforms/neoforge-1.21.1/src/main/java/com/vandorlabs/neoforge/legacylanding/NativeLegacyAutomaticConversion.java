package com.vandorlabs.neoforge.legacylanding;

import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Routes imported, settled wheel owners through the existing durable maintenance transfer. */
@EventBusSubscriber(modid = "vandorlabs")
final class NativeLegacyAutomaticConversion {
    static final boolean ENABLED = Boolean.getBoolean("vandorlabs.autoConvertLandingGear");
    private static final int RETRY_TICKS = 40;
    private static final int RECENT_LIMIT = 8192;
    private static final Map<ServerLevel, State> WORLDS = new IdentityHashMap<>();

    private static final class State {
        final Set<UUID> submitted = new LinkedHashSet<>();
        final Map<NativeLegacyLandingEntity, Long> retries = new WeakHashMap<>();

        void markSubmitted(UUID owner) {
            if (submitted.size() >= RECENT_LIMIT) submitted.remove(submitted.iterator().next());
            submitted.add(owner);
        }
    }

    static boolean loaded(NativeLegacyLandingEntity root) {
        if (!ENABLED || !root.importedWheel() || !root.pristine()
                || Math.abs(root.progress() - root.targetDistance()) >= .0001) return false;
        var world = (ServerLevel) root.getLevel();
        var state = WORLDS.computeIfAbsent(world, ignored -> new State());
        long now = world.getGameTime();
        if (state.retries.getOrDefault(root, Long.MIN_VALUE) > now || state.submitted.contains(root.identity())) return false;

        if (!world.noSave) {
            try {
                var operation = NativeLegacyMigration.request(root);
                if (operation != null) {
                    state.retries.remove(root);
                    state.markSubmitted(root.identity());
                    return true;
                }
            } catch (IllegalArgumentException refused) {
                // Footprint and mount refusals are reconsidered when a watched chunk or neighbor changes.
                state.retries.remove(root);
                return false;
            }
        }
        state.retries.put(root, now + RETRY_TICKS);
        return false;
    }

    static void tick(NativeLegacyLandingEntity root) {
        if (!ENABLED || !root.importedWheel()) return;
        var world = (ServerLevel) root.getLevel();
        var state = WORLDS.get(world);
        if (state == null) return;
        var retry = state.retries.get(root);
        if (retry != null && world.getGameTime() >= retry) {
            state.retries.remove(root);
            NativeLegacyUpdates.queue(root);
        }
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel world) WORLDS.remove(world);
    }

    private NativeLegacyAutomaticConversion() {}
}
