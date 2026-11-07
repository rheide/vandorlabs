package com.vandorlabs.neoforge.glass;

import com.vandorlabs.render.FlatGlassGeometry;
import com.vandorlabs.render.PanelCornerGeometry;
import com.vandorlabs.neoforge.fluid.NativeFluidVolume;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Installed ordinary-glass collision and fluid publication; construction reads no level. */
public final class NativeGlassGeometry {
 public record Snapshot(FlatGlassGeometry.Snapshot scalar,List<AABB> boxes,AABB bounds,NativeFluidVolume fluids){
  public Snapshot {boxes=List.copyOf(boxes);}
  public int side(double x,double y,double z){return scalar.side(x,y,z);}
 }
 public static Snapshot build(Direction facing,double near,PanelCornerGeometry.FlatCorner contact){
  var scalar=FlatGlassGeometry.build(facing.get3DDataValue(),near,contact);
  List<AABB> boxes=scalar.boxes.stream().map(b->new AABB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ)).toList();
  var b=scalar.bounds;return new Snapshot(scalar,boxes,new AABB(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ),NativeFluidVolume.build(boxes,scalar::side));
 }
 private NativeGlassGeometry(){}
}
