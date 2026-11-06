package com.vandorlabs.neoforge.segmented;

import com.vandorlabs.neoforge.door.NativeDoorFluidGeometry;
import com.vandorlabs.neoforge.fluid.NativeFluidVolume;
import com.vandorlabs.segmented.SegmentedDoorGeometry;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.AABB;

/** Stationary fluid space: closed leaf and fixed header only, independent of the animated body pose. */
public final class NativeSegmentedFluidGeometry {
    public record Snapshot(NativeDoorFluidGeometry.Plane plane,NativeFluidVolume volume){}
    private NativeSegmentedFluidGeometry(){}
    public static Snapshot build(Direction facing,int direction,int position,int height,int row,int column,int width,boolean closed,BlockPos cell){
        double depth=position==1?-13D/32:position==2?13D/32:0;
        double center=(facing.getStepX()+facing.getStepZ())*.5-depth;
        var plane=new NativeDoorFluidGeometry.Plane(facing.getStepX(),facing.getStepZ(),center-SegmentedDoorGeometry.HALF_THICKNESS,center+SegmentedDoorGeometry.HALF_THICKNESS);
        var solid=new ArrayList<AABB>(2);if(closed)clip(solid,NativeSegmentedGeometry.closedPanel(facing,position),cell);var header=NativeSegmentedGeometry.header(facing,direction,position,height,row,column,width);if(header!=null)clip(solid,header,cell);
        var volume=NativeFluidVolume.build(solid,plane::side,plane.cuts());if(volume.cells().size()>256)throw new IllegalStateException("Segmented fluid volume exceeds checked cell budget");return new Snapshot(plane,volume);
    }
    private static void clip(List<AABB> out,AABB local,BlockPos pos){
        var world=local.move(pos);if(!world.intersects(new AABB(pos)))return;
        out.add(new AABB(Math.max(0,world.minX-pos.getX()),Math.max(0,world.minY-pos.getY()),Math.max(0,world.minZ-pos.getZ()),Math.min(1,world.maxX-pos.getX()),Math.min(1,world.maxY-pos.getY()),Math.min(1,world.maxZ-pos.getZ())));
    }
}
