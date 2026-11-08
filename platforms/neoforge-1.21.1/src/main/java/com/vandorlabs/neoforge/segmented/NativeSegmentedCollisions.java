package com.vandorlabs.neoforge.segmented;

import java.util.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

/** Loaded moved-panel collision snapshots; cache values retain no tile or level references. */
public final class NativeSegmentedCollisions {
 private static final com.google.common.cache.Cache<NativeSegmentedDoorEntity,Column> CACHE=com.google.common.cache.CacheBuilder.newBuilder().weakKeys().maximumWeight(8*1024*1024).weigher((NativeSegmentedDoorEntity root,Column value)->512+512*value.boxes.size()).build();
 private static final class Column {
  final int x,y,z,height,width,column,direction,pixels,position,rotation;final boolean straight;final double progress;final List<AABB> boxes;final List<VoxelShape> shapes;final AABB bounds;
  Column(NativeSegmentedDoorEntity root,double progress){var pos=root.getBlockPos();var cfg=root.settings();x=pos.getX();y=pos.getY();z=pos.getZ();height=root.height();width=root.columnsCount();column=root.column();direction=cfg.direction;pixels=cfg.treadPixels;position=cfg.position;rotation=rotation(root);straight=cfg.straight;this.progress=progress;var builder=new com.vandorlabs.segmented.SegmentedPanelCollision.Builder();for(int row=0;row<height;row++)com.vandorlabs.segmented.SegmentedPanelCollision.append(builder,x,y,z,rotation,row,height,column,width,progress,direction,straight,pixels,cfg.depthOffset());var prepared=new ArrayList<AABB>();var solid=new ArrayList<VoxelShape>();AABB total=null;for(double[] b:builder.boxes()){var box=new AABB(b[0],b[1],b[2],b[3],b[4],b[5]);prepared.add(box);solid.add(Shapes.create(box));total=total==null?box:total.minmax(box);}boxes=List.copyOf(prepared);shapes=List.copyOf(solid);bounds=total;}
  boolean matches(NativeSegmentedDoorEntity root){var pos=root.getBlockPos();var cfg=root.settings();return progress==root.pose(0)&&x==pos.getX()&&y==pos.getY()&&z==pos.getZ()&&height==root.height()&&width==root.columnsCount()&&column==root.column()&&direction==cfg.direction&&pixels==cfg.treadPixels&&position==cfg.position&&straight==cfg.straight&&rotation==rotation(root);}
 }
 private static int rotation(NativeSegmentedDoorEntity root){return switch(root.facing()){case NORTH->0;case WEST->1;case SOUTH->2;case EAST->3;default->throw new IllegalArgumentException("Horizontal facing required");};}
 public static void remove(NativeSegmentedDoorEntity root){CACHE.invalidate(root);}
 public static void unload(Level level){CACHE.asMap().keySet().removeIf(root->root.getLevel()==level);}
 private record Source(NativeSegmentedDoorEntity root,Column column){}
 public static Iterable<VoxelShape> blockCollisions(Level level,AABB query,Iterable<VoxelShape> vanilla){
  if(!NativeSegmentedTargets.hasCollisionRoots(level,query))return vanilla;
  return ()->{var sources=new ArrayList<Source>();NativeSegmentedTargets.collisionRoots(level,query,root->{var cached=CACHE.getIfPresent(root);if(cached==null||!cached.matches(root)){cached=new Column(root,root.pose(0));CACHE.put(root,cached);}if(cached.bounds!=null&&cached.bounds.intersects(query))sources.add(new Source(root,cached));});var base=vanilla.iterator();return new Iterator<VoxelShape>(){
   int owner,box;VoxelShape pending;
   private void advance(){while(pending==null&&owner<sources.size()){var source=sources.get(owner);if(NativeSegmentedTargets.loaded(level,source.root().getBlockPos())!=source.root()||source.root().topOffset()!=0||!source.root().formed()||!source.column().matches(source.root())){owner++;box=0;continue;}while(box<source.column().boxes.size()){int index=box++;if(source.column().boxes.get(index).intersects(query)){pending=source.column().shapes.get(index);return;}}owner++;box=0;}}
   public boolean hasNext(){if(base.hasNext())return true;advance();return pending!=null;}
   public VoxelShape next(){if(base.hasNext())return base.next();advance();if(pending==null)throw new NoSuchElementException();var next=pending;pending=null;return next;}
  };};
 }
 private NativeSegmentedCollisions(){}
}
