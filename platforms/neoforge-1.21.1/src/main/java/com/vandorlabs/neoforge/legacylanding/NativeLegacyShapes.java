package com.vandorlabs.neoforge.legacylanding;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;
/** One authoritative pose per physical owner; cell unions are lazy and bounded to its54-cell box. */
final class NativeLegacyShapes {
 static Pose pose(NativeLegacyLandingEntity root){return new Pose(root.kind(),root.settings().size(),root.progress(),root.getBlockState().getValue(NativeLegacyLandingBlock.FACING));}
 static final class Pose {
  private final List<AABB> boxes;private final boolean wheel;private final Direction facing;private final Map<BlockPos,VoxelShape> cells=new HashMap<>();
  Pose(NativeLegacyLandingKind kind,int size,float progress,Direction facing){this.facing=facing;wheel=!kind.nonWheeled;double center=size==4?.5:0;var selected=new ArrayList<AABB>();var groups=NativeLegacyAssets.groups(kind,size);for(int group=0;group<3;group++)for(var source:groups.get(group)){var box=group==1?source.move(0,-progress,0):group==2?new AABB(source.minX,source.minY-progress,source.minZ,source.maxX,source.maxY,source.maxZ):source;if(!wheel)box=rotate(box,facing);selected.add(box.move(center,0,center));}boxes=List.copyOf(selected);}
  VoxelShape cell(BlockPos delta){if(Math.abs((long)delta.getX())>1||Math.abs((long)delta.getZ())>1||delta.getY()>0||delta.getY()<-5)return Shapes.empty();var found=cells.get(delta);if(found!=null)return found;var selected=new ArrayList<AABB>();for(var box:boxes){var local=box.move(-delta.getX(),-delta.getY(),-delta.getZ());double x0=Math.max(0,local.minX),y0=Math.max(0,local.minY),z0=Math.max(0,local.minZ),x1=Math.min(1,local.maxX),y1=Math.min(1,local.maxY),z1=Math.min(1,local.maxZ);if(x1>x0&&y1>y0&&z1>z0){var clipped=new AABB(x0,y0,z0,x1,y1,z1);selected.add(wheel?rotate(clipped,facing):clipped);}}var shape=com.vandorlabs.neoforge.geometry.ExactVoxelShapes.boxes(selected);cells.put(delta.immutable(),shape);return shape;}
 }
 private static AABB rotate(AABB box,Direction face){double x0=Double.POSITIVE_INFINITY,z0=x0,x1=Double.NEGATIVE_INFINITY,z1=x1;for(double x:new double[]{box.minX,box.maxX})for(double z:new double[]{box.minZ,box.maxZ}){double px,pz;switch(face){case NORTH->{px=x;pz=z;}case EAST->{px=1-z;pz=x;}case SOUTH->{px=1-x;pz=1-z;}default->{px=z;pz=1-x;}}x0=Math.min(x0,px);x1=Math.max(x1,px);z0=Math.min(z0,pz);z1=Math.max(z1,pz);}return new AABB(x0,box.minY,z0,x1,box.maxY,z1);}
 private NativeLegacyShapes(){}
}
