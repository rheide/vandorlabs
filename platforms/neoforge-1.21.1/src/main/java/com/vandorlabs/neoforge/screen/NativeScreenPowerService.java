package com.vandorlabs.neoforge.screen;
import com.vandorlabs.neoforge.redstone.NativeLoadedRedstonePower;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
/** Event-driven publication for affected loaded groups; idle levels never scan their Screen tiles. */
@net.neoforged.fml.common.EventBusSubscriber(modid="vandorlabs")
public final class NativeScreenPowerService {
 private NativeScreenPowerService(){}
 private static final Map<ServerLevel,Pending> LEVELS=new WeakHashMap<>();
 private static final class Pending{final Set<NativeScreenEntity> roots=Collections.newSetFromMap(new WeakHashMap<>()),topology=Collections.newSetFromMap(new WeakHashMap<>()),inputs=Collections.newSetFromMap(new WeakHashMap<>());boolean updating;}
 private static Pending pending(ServerLevel level){return LEVELS.computeIfAbsent(level,ignored->new Pending());}
 /** Local references only: safe during load and removal, without neighbor reads or channel publication. */
 public static void queueTopology(NativeScreenEntity tile){if(!(tile.getLevel() instanceof ServerLevel level))return;synchronized(LEVELS){var state=pending(level);state.topology.add(tile);state.roots.addAll(NativeScreenJoinedPower.knownMembers(tile));state.roots.add(tile);}}
 public static void refresh(NativeScreenEntity tile){if(!(tile.getLevel() instanceof ServerLevel level))return;if(!level.getServer().isSameThread())throw new IllegalStateException("Screen power publication requires the server thread");synchronized(LEVELS){var state=pending(level);state.roots.add(tile);state.inputs.add(tile);}drain(level);}
 /** Mirrors the Source light getter. Worker calls queue references; only the owning thread samples neighbors. */
 public static void queryLight(NativeScreenEntity tile){var visual=tile.visual();if(visual.inert()||!(tile.getLevel() instanceof ServerLevel level))return;var settings=visual.settings();var data=settings.scalar();if(!com.vandorlabs.screen.ScreenPowerQueries.lightQueriesPhysical(visual.family(),settings.activation().data().mode,data.displayMode,data.redstoneEnabled,visual.controls().get(0).enabled(),visual.controls().get(1).enabled(),settings.channelLevel(),data.channelSignal))return;synchronized(LEVELS){pending(level).roots.add(tile);}if(level.getServer().isSameThread())drain(level);}
 @net.neoforged.bus.api.SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event){if(event.getLevel() instanceof ServerLevel level)drain(level);}
 private static boolean installed(ServerLevel level,NativeScreenEntity tile){if(tile.inert()||tile.isRemoved()||tile.getLevel()!=level)return false;var pos=tile.getBlockPos();var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);return chunk!=null&&chunk.getBlockEntities().get(pos)==tile&&chunk.getBlockState(pos)==tile.getBlockState();}
 private static void drain(ServerLevel level){if(!com.vandorlabs.neoforge.redstone.NativeRedstoneChannels.open(level)){synchronized(LEVELS){LEVELS.remove(level);}return;}List<NativeScreenEntity> roots,topology,inputs;Pending state;synchronized(LEVELS){state=LEVELS.get(level);if(state==null||state.updating||state.roots.isEmpty())return;state.updating=true;roots=new ArrayList<>(state.roots);topology=new ArrayList<>(state.topology);inputs=new ArrayList<>(state.inputs);state.roots.clear();state.topology.clear();state.inputs.clear();}
  try{for(var tile:topology)if(tile.getLevel()==level)NativeScreenJoinedPower.changed(tile);for(var tile:inputs)if(tile.getLevel()==level)NativeScreenJoinedPower.inputChanged(tile);var powers=new IdentityHashMap<NativeScreenEntity,Integer>();for(var tile:roots){if(powers.containsKey(tile)||!installed(level,tile))continue;if(tile.getBlockState().getBlock() instanceof NativeScreenBlock){int value=NativeScreenJoinedPower.level(tile);for(var member:NativeScreenJoinedPower.members(tile))if(installed(level,member))powers.put(member,value);}else powers.put(tile,NativeLoadedRedstonePower.level(level,tile.getBlockPos()));}var changed=new ArrayList<NativeScreenEntity>();for(var entry:powers.entrySet())if(entry.getKey().installPhysicalPower(entry.getValue()))changed.add(entry.getKey());for(var tile:changed)if(installed(level,tile))tile.publishPower();}finally{synchronized(LEVELS){state.updating=false;}}
 }
}
