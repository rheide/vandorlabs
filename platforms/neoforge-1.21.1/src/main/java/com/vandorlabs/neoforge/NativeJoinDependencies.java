package com.vandorlabs.neoforge;

import com.vandorlabs.neoforge.fluid.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Owning-thread weak dependency records; rejected cells and chunk boundaries remain observed. */
public final class NativeJoinDependencies {
 public static final int MAX_OWNERS=8192,MAX_CELLS=131072;
 public interface Group {int retainedCells();void invalidateDependency();}
 public static final class Bounds {
  private int x0=Integer.MAX_VALUE,y0=x0,z0=x0,x1=Integer.MIN_VALUE,y1=x1,z1=x1;
  public void include(BlockPos p){x0=Math.min(x0,p.getX());y0=Math.min(y0,p.getY());z0=Math.min(z0,p.getZ());x1=Math.max(x1,p.getX());y1=Math.max(y1,p.getY());z1=Math.max(z1,p.getZ());}
  public boolean contains(BlockPos p){return intersects(p.getX(),p.getY(),p.getZ(),p.getX(),p.getY(),p.getZ());}
  public boolean intersects(int ax,int ay,int az,int bx,int by,int bz){return x0<=bx&&x1>=ax&&y0<=by&&y1>=ay&&z0<=bz&&z1>=az;}
 }
 private record Watch(Bounds bounds,Group group){}
 private static final Map<BlockEntity,Map<Integer,Watch>> OWNERS=new WeakHashMap<>();
 public static synchronized boolean observe(BlockEntity root,int kind,Bounds bounds,Group group){return observe(java.util.List.of(root),kind,bounds,group);}
 public static synchronized boolean observe(java.util.Collection<? extends BlockEntity> roots,int kind,Bounds bounds,Group group){
  java.util.Set<BlockEntity> added=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());for(BlockEntity root:roots)if(!OWNERS.containsKey(root))added.add(root);
  if(OWNERS.size()+added.size()>MAX_OWNERS){group.invalidateDependency();return false;}
  for(BlockEntity root:roots)OWNERS.computeIfAbsent(root,k->new HashMap<>()).put(kind,new Watch(bounds,group));
  int cells=0;java.util.Set<Group> retained=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());for(var watches:OWNERS.values())for(var watch:watches.values())if(retained.add(watch.group))cells+=watch.group.retainedCells();
  // Discard coordinate payloads while retaining weak dependency bounds. A far
  // edit must still wake a root whose correct published layout outlived its cache.
  for(Group candidate:retained){if(cells<=MAX_CELLS)break;cells-=candidate.retainedCells();candidate.invalidateDependency();}
  return true;
 }
 public record Stats(int owners,int cells){}
 public static synchronized Stats stats(){int cells=0;java.util.Set<Group> groups=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());for(var watches:OWNERS.values())for(var watch:watches.values())if(groups.add(watch.group))cells+=watch.group.retainedCells();return new Stats(OWNERS.size(),cells);}
 public static synchronized void changed(Level level,BlockPos pos){invalidate(level,pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ());}
 public static synchronized void chunk(Level level,int x,int z){invalidate(level,x<<4,Integer.MIN_VALUE,z<<4,(x<<4)+15,Integer.MAX_VALUE,(z<<4)+15);}
 private static void invalidate(Level level,int x0,int y0,int z0,int x1,int y1,int z1){
  List<ContainedFluidHost> queued=new ArrayList<>();for(var entry:OWNERS.entrySet()){BlockEntity root=entry.getKey();if(root==null||root.getLevel()!=level)continue;boolean changed=false;for(var watch:entry.getValue().values())if(watch.bounds.intersects(x0,y0,z0,x1,y1,z1)){watch.group.invalidateDependency();changed=true;}if(changed&&root instanceof ContainedFluidHost host&&!root.isRemoved())queued.add(host);}
  for(var host:queued)NativeFluidWorld.queue(host);
 }
 public static synchronized void forget(BlockEntity owner){var watches=OWNERS.remove(owner);if(watches!=null)for(var watch:watches.values())watch.group.invalidateDependency();}
 public static synchronized void unload(Level level){var entries=OWNERS.entrySet().iterator();while(entries.hasNext()){var entry=entries.next();if(entry.getKey()==null||entry.getKey().getLevel()==level){for(var watch:entry.getValue().values())watch.group.invalidateDependency();entries.remove();}}}
 private NativeJoinDependencies(){}
}
