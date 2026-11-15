package com.vandorlabs.neoforge.legacylanding;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
/** Identity-only onLoad queues; bounded weak chunk watchers never load a neighboring chunk. */
@EventBusSubscriber(modid="vandorlabs")
final class NativeLegacyUpdates {
 private static final int MAX_ROOTS=8192,MAX_PENDING=65536,BUDGET=64;
 private static final Map<ServerLevel,Registry> WORLDS=new IdentityHashMap<>();
 private static final class Entry {final WeakReference<NativeLegacyLandingEntity> root;final Set<Long> chunks;Entry(NativeLegacyLandingEntity root,Set<Long> chunks){this.root=new WeakReference<>(root);this.chunks=chunks;}}
 private static final class Registry {final Set<NativeLegacyLandingEntity> pending=Collections.newSetFromMap(new WeakHashMap<>());final Map<NativeLegacyLandingEntity,Entry> roots=new WeakHashMap<>();final Map<Long,Set<Entry>> chunks=new HashMap<>();final Set<Entry> all=Collections.newSetFromMap(new IdentityHashMap<>());long epoch;}
 static void queue(NativeLegacyLandingEntity tile){if(!(tile.getLevel() instanceof ServerLevel world))return;if(!world.getServer().isSameThread())throw new IllegalStateException("Telescopic update thread");var registry=WORLDS.computeIfAbsent(world,w->new Registry());if(registry.pending.size()>=MAX_PENDING&&!registry.pending.contains(tile)){tile.queueOverflow();return;}registry.pending.add(tile);}
 static long epoch(ServerLevel world){return WORLDS.computeIfAbsent(world,w->new Registry()).epoch;}
 static void topology(NativeLegacyLandingEntity tile){if(tile.getLevel() instanceof ServerLevel world){WORLDS.computeIfAbsent(world,w->new Registry()).epoch++;queue(tile);}}
 static boolean watch(NativeLegacyLandingEntity root){var world=(ServerLevel)root.getLevel();var registry=WORLDS.computeIfAbsent(world,w->new Registry());if(registry.roots.containsKey(root))return true;if(registry.all.size()>=MAX_ROOTS){for(var entry:new ArrayList<>(registry.all))if(entry.root.get()==null)forget(registry,entry);if(registry.all.size()>=MAX_ROOTS)return false;}var pos=root.getBlockPos();var chunks=new HashSet<Long>();for(int x=(pos.getX()-1)>>4;x<=(pos.getX()+1)>>4;x++)for(int z=(pos.getZ()-1)>>4;z<=(pos.getZ()+1)>>4;z++)chunks.add(ChunkPos.asLong(x,z));var entry=new Entry(root,Set.copyOf(chunks));registry.roots.put(root,entry);registry.all.add(entry);for(long chunk:chunks)registry.chunks.computeIfAbsent(chunk,key->Collections.newSetFromMap(new IdentityHashMap<>())).add(entry);return true;}
 static void remove(NativeLegacyLandingEntity tile){if(!(tile.getLevel() instanceof ServerLevel world))return;var registry=WORLDS.get(world);if(registry==null)return;registry.pending.remove(tile);var entry=registry.roots.remove(tile);if(entry!=null)forget(registry,entry);}
 private static void forget(Registry registry,Entry entry){registry.all.remove(entry);for(long chunk:entry.chunks){var set=registry.chunks.get(chunk);if(set!=null){set.remove(entry);if(set.isEmpty())registry.chunks.remove(chunk);}}}
 @SubscribeEvent public static void tick(LevelTickEvent.Post event){if(!(event.getLevel() instanceof ServerLevel world))return;var registry=WORLDS.get(world);if(registry==null)return;for(int n=0;n<BUDGET&&!registry.pending.isEmpty();n++){var tile=registry.pending.iterator().next();registry.pending.remove(tile);if(!tile.isRemoved()&&NativeLegacyLandingBlock.loaded(world,tile.getBlockPos())&&world.getBlockEntity(tile.getBlockPos())==tile)tile.deferred();}}
 private static void changed(ChunkEvent event){if(!(event.getLevel() instanceof ServerLevel world))return;var registry=WORLDS.get(world);if(registry==null)return;registry.epoch++;var entries=registry.chunks.get(event.getChunk().getPos().toLong());if(entries==null)return;for(var iterator=entries.iterator();iterator.hasNext();){var root=iterator.next().root.get();if(root==null){iterator.remove();continue;}queue(root);}}
 @SubscribeEvent public static void loaded(ChunkEvent.Load event){changed(event);}@SubscribeEvent public static void unloaded(ChunkEvent.Unload event){changed(event);}
 @SubscribeEvent public static void unload(LevelEvent.Unload event){if(event.getLevel() instanceof ServerLevel world)WORLDS.remove(world);}
 @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event){if(!(event.getLevel() instanceof ServerLevel world)||!(event.getState().getBlock() instanceof NativeLegacyLandingBlock))return;var root=NativeLegacyLandingBlock.owner(world,event.getPos());if(root==null||root.inert()||NativeLegacyFootprints.mutating(world,event.getPos())){event.setCanceled(true);return;}}
 private NativeLegacyUpdates(){}
}
