package com.vandorlabs.neoforge.door;
import com.vandorlabs.neoforge.fluid.NativeFluidVolume;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
/** Each reserved cell owns local runs; the root's physical timeline and closed plane stay authoritative. */
public final class NativeLargeDoorFluidGeometry {
    private NativeLargeDoorFluidGeometry(){}
    public static NativeDoorFluidGeometry.Snapshot build(NativeDoorSettings settings,NativeLargeDoorGeometry.Snapshot geometry,int column,int row){
        var key=geometry.key();var plane=NativeDoorFluidGeometry.plane(settings,key.face());
        if(key.horizontal()){
            int origin=key.face().getAxisDirection()==net.minecraft.core.Direction.AxisDirection.POSITIVE?1:0;
            plane=new NativeDoorFluidGeometry.Plane(0,-1,0,plane.near()-origin,plane.far()-origin);
        }
        BlockPos pos=NativeLargeDoorLifecycle.cell(geometry.root(),key.face(),key.horizontal(),column,row);var unit=new AABB(pos);List<AABB> solids=new ArrayList<>();
        for(var box:geometry.boxes())if(box.intersects(unit)){double x0=Math.max(0,box.minX-pos.getX()),y0=Math.max(0,box.minY-pos.getY()),z0=Math.max(0,box.minZ-pos.getZ()),x1=Math.min(1,box.maxX-pos.getX()),y1=Math.min(1,box.maxY-pos.getY()),z1=Math.min(1,box.maxZ-pos.getZ());solids.add(new AABB(x0,y0,z0,x1,y1,z1));}
        return new NativeDoorFluidGeometry.Snapshot(plane,NativeFluidVolume.build(solids,plane::side,plane.cuts()));
    }
}
