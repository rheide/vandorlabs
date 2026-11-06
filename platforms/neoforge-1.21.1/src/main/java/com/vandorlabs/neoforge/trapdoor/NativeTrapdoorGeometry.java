package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.render.TrapdoorLeafPose;
import com.vandorlabs.render.TrapdoorLeafSnapshot;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** World-free immutable endpoint publication; construct on state changes and reuse for hover/collision. */
public final class NativeTrapdoorGeometry {
 private NativeTrapdoorGeometry(){}
 private static final int BUDGET=8*1024*1024;
 private static final java.util.LinkedHashMap<TrapdoorLeafPose,Pair> CACHE=new java.util.LinkedHashMap<>(32,.75F,true);private static int retained;
 private static final class Pair {Snapshot closed,open;int cost=256;}
 /** The hit path allocates no wrapper key or concurrent access-queue node. */
 public static synchronized Snapshot snapshot(TrapdoorLeafPose pose,boolean open){
  var pair=CACHE.get(pose);if(pair==null){pair=new Pair();CACHE.put(pose,pair);retained+=pair.cost;}
  var found=open?pair.open:pair.closed;if(found==null){found=new Snapshot(new TrapdoorLeafSnapshot(pose,open));if(open)pair.open=found;else pair.closed=found;int cost=found.retainedBytes();pair.cost+=cost;retained+=cost;while(retained>BUDGET){var iterator=CACHE.entrySet().iterator();var old=iterator.next();retained-=old.getValue().cost;iterator.remove();}}
  return found;
 }
 public static TrapdoorLeafPose missing(boolean diagonal,int turns,boolean top){return new TrapdoorLeafPose(diagonal,diagonal?0:top?2:0,turns,diagonal&&top,false,false,false,false,false,false,1,1D/16,15D/16,1D/16);}
 public static int turns(Direction facing){return switch(facing){case NORTH->0;case EAST->1;case SOUTH->2;case WEST->3;default->throw new IllegalArgumentException("Horizontal trapdoor facing");};}
 public static final class Snapshot {
  private final TrapdoorLeafSnapshot scalars;private final AABB bounds;private final AABB[] collision;private final net.minecraft.world.phys.shapes.VoxelShape outlineShape;
  private Snapshot(TrapdoorLeafSnapshot scalars){this.scalars=scalars;bounds=new AABB(scalars.bound(0),scalars.bound(1),scalars.bound(2),scalars.bound(3),scalars.bound(4),scalars.bound(5));outlineShape=net.minecraft.world.phys.shapes.Shapes.create(bounds);collision=new AABB[scalars.collisionCount()];for(int i=0;i<collision.length;i++)collision[i]=new AABB(scalars.collision(i,0),scalars.collision(i,1),scalars.collision(i,2),scalars.collision(i,3),scalars.collision(i,4),scalars.collision(i,5));}
  public AABB bounds(){return bounds;}public net.minecraft.world.phys.shapes.VoxelShape outlineShape(){return outlineShape;}public int collisionCount(){return collision.length;}public AABB collision(int index){return collision[index];}public double corner(int index,int axis){return scalars.corner(index,axis);}
  public boolean trace(boolean offset,double ox,double oy,double oz,double sx,double sy,double sz,double ex,double ey,double ez,double[] hit){return scalars.trace(offset,ox,oy,oz,sx,sy,sz,ex,ey,ez,hit);}
  public boolean blockTrace(net.minecraft.core.BlockPos owner,net.minecraft.world.phys.Vec3 start,net.minecraft.world.phys.Vec3 end,double[] hit){return trace(false,owner.getX(),owner.getY(),owner.getZ(),start.x,start.y,start.z,end.x,end.y,end.z,hit);}
  public boolean offsetTrace(net.minecraft.core.BlockPos owner,net.minecraft.world.phys.Vec3 start,net.minecraft.world.phys.Vec3 end,double[] hit){return trace(true,owner.getX(),owner.getY(),owner.getZ(),start.x,start.y,start.z,end.x,end.y,end.z,hit);}
  public void outline(TrapdoorLeafSnapshot.EdgeSink sink){scalars.outline(sink);}public void leafEdges(TrapdoorLeafSnapshot.EdgeSink sink){scalars.leafEdges(sink);}
  public int retainedBytes(){return 512+scalars.retainedBytes()+collision.length*80;}
 }
 public static synchronized long retainedBytes(){return retained;}
}
