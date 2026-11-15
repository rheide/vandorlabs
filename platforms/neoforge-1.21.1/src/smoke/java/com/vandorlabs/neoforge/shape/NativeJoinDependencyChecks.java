package com.vandorlabs.neoforge.shape;

import com.vandorlabs.neoforge.NativeJoinDependencies;
import com.vandorlabs.surface.TextureJoinSettings;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.entity.*;

/** Actual bounded panel discovery and tile-owned groups through dependency/lifecycle invalidation. */
public final class NativeJoinDependencyChecks {
 private static void need(boolean value,String why){if(!value)throw new AssertionError(why);}
 private static final class Owner extends BlockEntity {private Owner(){super(null,BlockPos.ZERO,null);}}
 private static final class CountingGroup implements NativeJoinDependencies.Group {static long probes;int cells;CountingGroup(int cells){this.cells=cells;}public int retainedCells(){probes++;return cells;}public void invalidateDependency(){cells=0;}}
 private static void observationCost(sun.misc.Unsafe unsafe)throws Exception{
  int count=2048;List<Owner> owners=new ArrayList<>();List<CountingGroup> groups=new ArrayList<>();CountingGroup.probes=0;
  for(int i=0;i<count;i++){Owner owner=(Owner)unsafe.allocateInstance(Owner.class);owners.add(owner);var group=new CountingGroup(1);groups.add(group);var bounds=new NativeJoinDependencies.Bounds();bounds.include(new BlockPos(i*4,80,0));need(NativeJoinDependencies.observe(owner,0,bounds,group),"isolated group admission");}
  long probes=CountingGroup.probes;System.out.println("Native join observation owners="+count+" retainedCellProbes="+probes);need(probes<=count*4L&&NativeJoinDependencies.stats().cells()==count,"ordinary group observation must not recount every earlier owner");
  var bounds=new NativeJoinDependencies.Bounds();bounds.include(BlockPos.ZERO);NativeJoinDependencies.observe(owners.get(0),0,bounds,new CountingGroup(2));need(NativeJoinDependencies.stats().cells()==count+1,"replaced group weight releases even when old descriptor remains reachable");var shared=new CountingGroup(3);NativeJoinDependencies.observe(List.of(owners.get(0),owners.get(1)),1,bounds,shared);need(NativeJoinDependencies.stats().cells()==count+4,"shared descriptor counted once");NativeJoinDependencies.forget(owners.get(0));need(NativeJoinDependencies.stats().owners()==count-1&&NativeJoinDependencies.stats().cells()==count-1,"forget retires own and invalidated shared coordinate payloads");NativeJoinDependencies.unload(null);need(NativeJoinDependencies.stats().owners()==0&&NativeJoinDependencies.stats().cells()==0,"weighted index releases on unload");
 }
 public static void main(String[] args)throws Exception{
  // The registry reads only identity/level fields. No vanilla/mod registration or world is needed.
  var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);var unsafe=(sun.misc.Unsafe)field.get(null);
  BlockPos start=new BlockPos(0,80,8);Owner owner=(Owner)unsafe.allocateInstance(Owner.class);var policy=new TextureJoinSettings(5,0);Set<BlockPos> cells=new HashSet<>();for(int x=0;x<48;x++)cells.add(new BlockPos(x,80,8));
  var dependencies=new NativeJoinDependencies.Bounds();dependencies.include(start);var captured=NativePanelGroups.capture(start,Direction.EAST,"same",p->{dependencies.include(p);return cells.contains(p)?"same":null;},(a,b)->policy.connects(a.getX(),a.getY(),a.getZ(),b.getX(),b.getY(),b.getZ()));
  NativeWallLayouts.Group group=new NativeWallLayouts.Group(NativePanelGroups.Mode.MATERIAL,captured.layouts(),captured.capped());NativeJoinDependencies.observe(owner,0,dependencies,group);need(group.layouts.get(start.asLong()).columns()==48,"owner spans three physical chunk columns");
  NativeJoinDependencies.changed(null,new BlockPos(500,80,8));need(group.valid,"unrelated block retains group");NativeJoinDependencies.changed(null,new BlockPos(47,80,8));need(!group.valid&&group.layouts.isEmpty(),"far accepted third-section peer invalidates owner and releases cached cells");
  cells.remove(new BlockPos(47,80,8));var bounds=new NativeJoinDependencies.Bounds();bounds.include(start);var next=NativePanelGroups.capture(start,Direction.EAST,"same",p->{bounds.include(p);return cells.contains(p)?"same":null;},(a,b)->policy.connects(a.getX(),a.getY(),a.getZ(),b.getX(),b.getY(),b.getZ()));var rebuilt=new NativeWallLayouts.Group(NativePanelGroups.Mode.MATERIAL,next.layouts(),next.capped());NativeJoinDependencies.observe(owner,0,bounds,rebuilt);need(rebuilt.layouts.get(start.asLong()).columns()==47,"same actual discovery reflects removed peer");NativeJoinDependencies.changed(null,new BlockPos(47,80,8));need(!rebuilt.valid,"rejected missing peer remains a future bridge dependency");
  var chunkGroup=new NativeWallLayouts.Group(NativePanelGroups.Mode.MATERIAL,next.layouts(),next.capped());NativeJoinDependencies.observe(owner,0,bounds,chunkGroup);NativeJoinDependencies.chunk(null,2,0);need(!chunkGroup.valid,"far chunk load/unload invalidates dependent owner");
  var unloadGroup=new NativeWallLayouts.Group(NativePanelGroups.Mode.MATERIAL,next.layouts(),next.capped());NativeJoinDependencies.observe(owner,0,bounds,unloadGroup);NativeJoinDependencies.unload(null);need(!unloadGroup.valid&&unloadGroup.layouts.isEmpty(),"world unload releases dependency payload");
  List<Owner> owners=new ArrayList<>();for(int i=0;i<NativeJoinDependencies.MAX_OWNERS;i++)owners.add((Owner)unsafe.allocateInstance(Owner.class));var shared=new NativeWallLayouts.Group(NativePanelGroups.Mode.MATERIAL,next.layouts(),next.capped());need(NativeJoinDependencies.observe(owners,0,bounds,shared),"bounded shared owner admission");need(NativeJoinDependencies.stats().owners()==NativeJoinDependencies.MAX_OWNERS,"weak owner budget populated exactly");var refused=new NativeWallLayouts.Group(NativePanelGroups.Mode.MATERIAL,next.layouts(),next.capped());need(!NativeJoinDependencies.observe((Owner)unsafe.allocateInstance(Owner.class),0,bounds,refused)&&!refused.valid,"excess owner refuses joined caching for explicit unit fallback");
  need(!NativeJoinDependencies.observe((Owner)unsafe.allocateInstance(Owner.class),0,bounds,shared)&&!shared.valid&&NativeJoinDependencies.stats().cells()==0,"rejected shared group refreshes retained accounting");
  for(int i=0;i<33;i++){Map<Long,NativePanelLayout> records=new HashMap<>();for(int n=0;n<4096;n++)records.put((long)n,NativePanelLayout.UNIT);NativeJoinDependencies.observe(owners.get(i),0,bounds,new NativeWallLayouts.Group(NativePanelGroups.Mode.MATERIAL,Map.copyOf(records),true));}need(NativeJoinDependencies.stats().cells()<=NativeJoinDependencies.MAX_CELLS&&NativeJoinDependencies.stats().owners()==NativeJoinDependencies.MAX_OWNERS,"coordinate eviction stays bounded while retaining far dependency watches");NativeJoinDependencies.unload(null);need(NativeJoinDependencies.stats().owners()==0&&NativeJoinDependencies.stats().cells()==0,"unload releases full budget");
  observationCost(unsafe);
  System.out.println("Native join dependencies PASS threeChunkSpan/farPeer/rejectedBridge/chunkLifecycle/worldUnload/boundedTileOwnedGroups/8192Owners/131072Cells; owning-world queue checks separate");
 }
}
