package com.vandorlabs.neoforge.programmer;
import com.vandorlabs.programmer.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
/** World-free native projection of the shared fixed authored picker. No block or owner lookup. */
public final class NativeProgrammerPicking {
 private NativeProgrammerPicking(){}
 public record Hit(Vec3 point,Direction side,int slot,String group,BlockPos cell){public BlockHitResult blockHit(){return new BlockHitResult(point,side,cell,false);}}
 public static Hit trace(ProgrammerPicking.Mesh mesh,BlockPos root,Direction facing,int part,Vec3 start,Vec3 end){var layout=NativeProgrammerLayout.of(facing);if(part<0||part>3)throw new IllegalArgumentException("Programmer part");var offset=layout.cells().get(part);BlockPos cell=root.offset(offset.x(),offset.y(),offset.z());var hit=mesh.trace(cell.getX(),cell.getY(),cell.getZ(),root.getX()+layout.originX(),root.getY(),root.getZ()+layout.originZ(),ProgrammerTransform.Facing.valueOf(facing.name()),start.x,start.y,start.z,end.x,end.y,end.z);return hit==null?null:new Hit(new Vec3(hit.x,hit.y,hit.z),Direction.from3DDataValue(hit.side),hit.slot,hit.group,cell);}
}
