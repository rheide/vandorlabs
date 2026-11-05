package com.vandorlabs.neoforge;

import com.vandorlabs.persistence.ControlMountSettings;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Compare cold exact-grid assembly with the smallest native union API alternative. */
final class PowerShapeCostChecks {
    static void run(){
        var state=VandorLabsBootstrap.COMPACT_POWER_LEVER.get().defaultBlockState().setValue(NativePowerLeverBlock.FACING,Direction.WEST).setValue(NativePowerLeverBlock.FLOOR,true);var mount=ControlMountSettings.of(1,1,1);long start=System.nanoTime();VoxelShape grid=NativeMountedControlGeometry.powerShape(state,mount,1);long gridNanos=System.nanoTime()-start;var boxes=NativeMountedControlGeometry.powerBoxes(state,mount,1);
        System.out.println("[vandorlabs][power-shape-cost] grid key=compact/west/floor/size1/mount21 boxes="+boxes.size()+" ms="+gridNanos/1e6);
        VoxelShape joined=Shapes.empty();start=System.nanoTime();for(AABB box:boxes)joined=Shapes.joinUnoptimized(joined,Shapes.create(box),BooleanOp.OR);long joinNanos=System.nanoTime()-start;
        System.out.println("[vandorlabs][power-shape-cost] native joinUnoptimized ms="+joinNanos/1e6+"; starting single optimize");start=System.nanoTime();VoxelShape optimized=joined.optimize();long optimizeNanos=System.nanoTime()-start;
        if(Shapes.joinIsNotEmpty(grid,joined,BooleanOp.NOT_SAME)||Shapes.joinIsNotEmpty(grid,optimized,BooleanOp.NOT_SAME))throw new IllegalStateException("Exact grid differs from native component union");
        for(AABB box:boxes){Vec3 center=new Vec3((box.minX+box.maxX)/2,(box.minY+box.maxY)/2,(box.minZ+box.maxZ)/2);for(Direction face:Direction.values()){Vec3 delta=new Vec3(face.getStepX()*3,face.getStepY()*3,face.getStepZ()*3);var a=grid.clip(center.add(delta),center.subtract(delta),net.minecraft.core.BlockPos.ZERO);var b=optimized.clip(center.add(delta),center.subtract(delta),net.minecraft.core.BlockPos.ZERO);if((a==null)!=(b==null)||a!=null&&a.getLocation().distanceTo(b.getLocation())>1e-6)throw new IllegalStateException("Grid/native ray mismatch");}}
        System.out.println("[vandorlabs][power-shape-cost] PASS nativeOptimizeMs="+optimizeNanos/1e6+" nativeTotalMs="+(joinNanos+optimizeNanos)/1e6+" gridMs="+gridNanos/1e6+" exact occupancy and all component-center six-axis rays");
    }
    private PowerShapeCostChecks(){}
}
