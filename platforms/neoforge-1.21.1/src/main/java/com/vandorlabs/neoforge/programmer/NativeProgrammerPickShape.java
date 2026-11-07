package com.vandorlabs.neoforge.programmer;

import com.vandorlabs.programmer.*;
import java.util.function.Supplier;
import it.unimi.dsi.fastutil.doubles.*;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Full physical/outline grid with the source's separate authored ray intersection. */
final class NativeProgrammerPickShape extends VoxelShape {
    private static final DoubleList COORDS=DoubleLists.unmodifiable(DoubleArrayList.of(0,1));
    private final Supplier<ProgrammerPicking.Mesh> geometry;
    private final ProgrammerTransform.Facing facing;
    private final int cellX,cellY,cellZ,originX,originZ;
    NativeProgrammerPickShape(Supplier<ProgrammerPicking.Mesh> geometry,Direction direction,int part){
        super(BitSetDiscreteVoxelShape.withFilledBounds(1,1,1,0,0,0,1,1,1));
        this.geometry=geometry;facing=ProgrammerTransform.Facing.valueOf(direction.name());
        var layout=NativeProgrammerLayout.of(direction);var offset=layout.cells().get(part);
        cellX=offset.x();cellY=offset.y();cellZ=offset.z();originX=layout.originX();originZ=layout.originZ();
    }
    @Override public DoubleList getCoords(Direction.Axis axis){return COORDS;}
    @Override public BlockHitResult clip(Vec3 start,Vec3 end,BlockPos pos){
        var hit=geometry.get().trace(pos.getX(),pos.getY(),pos.getZ(),pos.getX()-cellX+originX,pos.getY()-cellY,pos.getZ()-cellZ+originZ,facing,start.x,start.y,start.z,end.x,end.y,end.z);
        return hit==null?null:new BlockHitResult(new Vec3(hit.x,hit.y,hit.z),Direction.from3DDataValue(hit.side),pos.immutable(),false);
    }
}
