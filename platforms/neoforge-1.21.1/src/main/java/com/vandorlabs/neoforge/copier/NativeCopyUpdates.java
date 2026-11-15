package com.vandorlabs.neoforge.copier;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Deferred identity-checked pad work; clean controllers have no repeated discovery/tick scan. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeCopyUpdates {
    private static final Map<ServerLevel,LinkedHashSet<NativeCopyControllerEntity>> PENDING=new IdentityHashMap<>();
    private static final Map<ServerLevel,Set<NativeCopyControllerEntity>> LOADED=new IdentityHashMap<>();
    public static void queue(NativeCopyControllerEntity owner){if(owner.getLevel() instanceof ServerLevel level){if(!level.getServer().isSameThread())throw new IllegalStateException("Copy update thread");PENDING.computeIfAbsent(level,unused->new LinkedHashSet<>()).add(owner);LOADED.computeIfAbsent(level,unused->Collections.newSetFromMap(new IdentityHashMap<>())).add(owner);}}
    public static void remove(NativeCopyControllerEntity owner){if(owner.getLevel() instanceof ServerLevel level){var pending=PENDING.get(level);if(pending!=null)pending.remove(owner);var loaded=LOADED.get(level);if(loaded!=null)loaded.remove(owner);}}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer actor))return;var loaded=LOADED.get(actor.serverLevel());if(loaded!=null)for(var owner:loaded)if(actor.getUUID().equals(owner.actorId()))queue(owner);}
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){if(!(event.getLevel() instanceof ServerLevel level))return;var pending=PENDING.get(level);if(pending==null)return;for(int n=0;n<16&&!pending.isEmpty();n++){var owner=pending.iterator().next();pending.remove(owner);if(owner.installed())owner.deferred();}if(pending.isEmpty())PENDING.remove(level);}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel level){PENDING.remove(level);LOADED.remove(level);}}
    private NativeCopyUpdates(){}
}
