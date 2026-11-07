package com.vandorlabs.neoforge.glass;

import com.vandorlabs.render.FlatGlassGeometry;
import com.vandorlabs.render.PanelCornerGeometry;
import com.vandorlabs.neoforge.fluid.NativeFluidVolume;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Installed ordinary-glass collision and fluid publication; construction reads no level. */
public final class NativeGlassGeometry {
 public record Snapshot(FlatGlassGeometry.Snapshot scalar,List<AABB> boxes,AABB bounds,NativeFluidVolume fluids,net.minecraft.world.phys.shapes.VoxelShape selection){
  public Snapshot {boxes=List.copyOf(boxes);}
  public int side(double x,double y,double z){return scalar.side(x,y,z);}
  public net.minecraft.world.phys.shapes.VoxelShape collision(){return com.vandorlabs.neoforge.shape.NativeFlatWallGeometry.collision(boxes);}
 }
 public static Snapshot build(Direction facing,double near,PanelCornerGeometry.FlatCorner contact){
  var scalar=FlatGlassGeometry.build(facing.get3DDataValue(),near,contact);
  List<AABB> boxes=scalar.boxes.stream().map(b->new AABB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)).toList();
  var b=scalar.bounds;var bounds=new AABB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);return new Snapshot(scalar,boxes,bounds,NativeFluidVolume.build(boxes,scalar::side),com.vandorlabs.neoforge.shape.NativeFlatWallGeometry.collision(List.of(bounds)));
 }
 private static final Snapshot[] STRAIGHT=new Snapshot[6];
 static{for(int rotated=0;rotated<2;rotated++)for(int depth=0;depth<3;depth++)STRAIGHT[rotated*3+depth]=build(rotated==0?Direction.NORTH:Direction.EAST,com.vandorlabs.render.PanelDepthPolicy.start(depth),null);}
 public static Snapshot straight(boolean rotated,int depth){if(depth<0||depth>2)throw new IllegalArgumentException("Glass depth");return STRAIGHT[(rotated?3:0)+depth];}
 private NativeGlassGeometry(){}
}
