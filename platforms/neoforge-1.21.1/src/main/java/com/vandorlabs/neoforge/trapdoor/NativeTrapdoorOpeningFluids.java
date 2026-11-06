package com.vandorlabs.neoforge.trapdoor;

import com.vandorlabs.neoforge.door.NativeDoorOpeningFluids;
import com.vandorlabs.neoforge.fluid.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;

/** Six-face normal-side ambient fill, independent of stationary slot ownership. */
public final class NativeTrapdoorOpeningFluids {
    private NativeTrapdoorOpeningFluids(){}
    public static int ambientSides(NativeTrapdoorFluidGeometry.Plane plane,int adjacentWaterMask){int ambient=0;for(Direction face:Direction.values())if((adjacentWaterMask&(1<<face.get3DDataValue()))!=0){double dot=plane.x()*face.getStepX()+plane.y()*face.getStepY()+plane.z()*face.getStepZ();if(-dot>.1)ambient|=1;if(dot>.1)ambient|=2;}return ambient;}
    public static int sources(NativeTrapdoorFluidGeometry.Plane plane,boolean open,int first,int second,int adjacentWaterMask){return NativeDoorOpeningFluids.sources(open,first,second,ambientSides(plane,adjacentWaterMask));}
    public static int sources(Level level,BlockPos pos,NativeTrapdoorFluidGeometry.Plane plane,boolean open,NativeFluidSlots.Snapshot stored){int water=0;for(Direction face:Direction.values()){var next=pos.relative(face);if(NativeFluidWorld.loaded(level,next)&&level.getFluidState(next).is(net.minecraft.tags.FluidTags.WATER))water|=1<<face.get3DDataValue();}return sources(plane,open,kind(stored,0),kind(stored,1),water);}
    private static int kind(NativeFluidSlots.Snapshot stored,int side){var fluid=stored.side(side).fluid();return fluid.isEmpty()?0:fluid.is(Fluids.WATER)?1:2;}
}
