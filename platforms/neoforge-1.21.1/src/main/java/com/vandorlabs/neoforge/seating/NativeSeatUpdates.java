package com.vandorlabs.neoforge.seating;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
/** Deferred loaded-identity work only. Captures also refresh their selected profiles synchronously. */
@EventBusSubscriber(modid="vandorlabs")
public final class NativeSeatUpdates {
    private static final Map<ServerLevel,LinkedHashSet<NativeChairEntity>> PENDING=new IdentityHashMap<>();
    public static void queue(NativeChairEntity tile){if(tile.getLevel() instanceof ServerLevel world&&(!NativeSeatBlock.proxy(tile.getBlockState())||tile.kind()==NativeSeatKind.ADVANCED)){if(!world.getServer().isSameThread())throw new IllegalStateException("Seat update thread");PENDING.computeIfAbsent(world,unused->new LinkedHashSet<>()).add(tile);}}
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){if(!(event.getLevel() instanceof ServerLevel world))return;var queue=PENDING.get(world);if(queue==null)return;for(int budget=0;budget<32&&!queue.isEmpty();budget++){var owner=queue.iterator().next();queue.remove(owner);if(!owner.isRemoved()&&NativeSeatBlock.loaded(world,owner.getBlockPos())&&world.getBlockEntity(owner.getBlockPos())==owner){if(owner.kind()==NativeSeatKind.ADVANCED)NativeAdvancedChairBlock.repair(world,owner);else owner.repairUpper();owner.refreshAppearance();}}if(queue.isEmpty())PENDING.remove(world);}
    @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel world)PENDING.remove(world);}
    private NativeSeatUpdates(){}
}
