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
 private static final class Weight {int references,cells;}
 private static final Map<Group,Weight> WEIGHTS=new IdentityHashMap<>();
 private static int retainedCells,accountedOwners;
 private static void add(Group group){WEIGHTS.computeIfAbsent(group,g->new Weight()).references++;}
 private static void release(Group group){Weight weight=WEIGHTS.get(group);if(weight!=null&&--weight.references==0){retainedCells-=weight.cells;WEIGHTS.remove(group);}}
 private static void refresh(Group group){Weight weight=WEIGHTS.get(group);if(weight!=null){int cells=group.retainedCells();retainedCells+=cells-weight.cells;weight.cells=cells;}}
 private static void invalidate(Group group){group.invalidateDependency();refresh(group);}
 /** Reconcile only when weak owners were collected, never on ordinary group installation. */
 private static void prepare(){
  while(OWNERS.size()!=accountedOwners){int before=OWNERS.size();WEIGHTS.clear();retainedCells=0;for(var watches:OWNERS.values())for(var watch:watches.values())add(watch.group);for(Group group:WEIGHTS.keySet())refresh(group);accountedOwners=before;}
 }
 public static synchronized boolean observe(BlockEntity root,int kind,Bounds bounds,Group group){return observe(java.util.List.of(root),kind,bounds,group);}
 public static synchronized boolean observe(java.util.Collection<? extends BlockEntity> roots,int kind,Bounds bounds,Group group){
  prepare();java.util.Set<BlockEntity> added=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());for(BlockEntity root:roots)if(!OWNERS.containsKey(root))added.add(root);
  if(OWNERS.size()+added.size()>MAX_OWNERS){invalidate(group);return false;}
  for(BlockEntity root:roots){var old=OWNERS.computeIfAbsent(root,k->new HashMap<>()).put(kind,new Watch(bounds,group));if(old==null||old.group!=group){if(old!=null)release(old.group);add(group);}}
  accountedOwners+=added.size();prepare();refresh(group);
  // Discard coordinate payloads while retaining weak dependency bounds. A far
  // edit must still wake a root whose correct published layout outlived its cache.
  if(retainedCells>MAX_CELLS)for(Group candidate:WEIGHTS.keySet()){if(retainedCells<=MAX_CELLS)break;invalidate(candidate);}
  return true;
 }
 public record Stats(int owners,int cells){}
 public static synchronized Stats stats(){prepare();return new Stats(OWNERS.size(),retainedCells);}
 public static synchronized void changed(Level level,BlockPos pos){invalidate(level,pos.getX(),pos.getY(),pos.getZ(),pos.getX(),pos.getY(),pos.getZ());}
 public static synchronized void chunk(Level level,int x,int z){invalidate(level,x<<4,Integer.MIN_VALUE,z<<4,(x<<4)+15,Integer.MAX_VALUE,(z<<4)+15);}
 private static void invalidate(Level level,int x0,int y0,int z0,int x1,int y1,int z1){
  prepare();List<ContainedFluidHost> queued=new ArrayList<>();for(var entry:OWNERS.entrySet()){BlockEntity root=entry.getKey();if(root==null||root.getLevel()!=level)continue;boolean changed=false;for(var watch:entry.getValue().values())if(watch.bounds.intersects(x0,y0,z0,x1,y1,z1)){invalidate(watch.group);changed=true;}if(changed&&root instanceof ContainedFluidHost host&&!root.isRemoved())queued.add(host);}
  for(var host:queued)NativeFluidWorld.queue(host);
 }
 public static synchronized void forget(BlockEntity owner){prepare();var watches=OWNERS.remove(owner);if(watches!=null){accountedOwners--;for(var watch:watches.values()){invalidate(watch.group);release(watch.group);}}prepare();}
 public static synchronized void unload(Level level){prepare();var entries=OWNERS.entrySet().iterator();while(entries.hasNext()){var entry=entries.next();if(entry.getKey()==null||entry.getKey().getLevel()==level){for(var watch:entry.getValue().values()){invalidate(watch.group);release(watch.group);}entries.remove();accountedOwners--;}}prepare();}
 private NativeJoinDependencies(){}
}
