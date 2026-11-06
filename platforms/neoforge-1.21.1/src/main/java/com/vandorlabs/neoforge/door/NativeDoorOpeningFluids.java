package com.vandorlabs.neoforge.door;

import com.vandorlabs.neoforge.fluid.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;

/** Current reversible opening fill; never changes raw saved slot ownership. */
public final class NativeDoorOpeningFluids {
    private NativeDoorOpeningFluids(){}
    /** Scalar equivalent of own-first, opposite-water and directly adjacent normal-side water. */
    public static int sources(boolean open,int resolvedFirst,int resolvedSecond,int ambientSides){
        int result=0;for(int side=0;side<2;side++){
            int own=side==0?resolvedFirst:resolvedSecond,opposite=side==0?resolvedSecond:resolvedFirst;
            if(own!=0)continue;
            int source=open&&opposite==1?1:(ambientSides&(1<<side))!=0||open&&(ambientSides&(1<<(1-side)))!=0?2:0;result|=source<<(side*2);
        }return result;
    }
    private static int kind(NativeFluidSlots.Snapshot stored,int side){var fluid=stored.side(side).fluid();return fluid.isEmpty()?0:fluid.is(Fluids.WATER)?1:2;}
    /** Called on the owning thread after the exact loaded host identity is checked. */
    static int sources(Level level,BlockPos pos,Direction facing,boolean open,NativeFluidSlots.Snapshot stored){
        int ambient=0;for(int side=0;side<2;side++){Direction face=side==0?facing.getOpposite():facing;BlockPos next=pos.relative(face);if(NativeFluidWorld.loaded(level,next)&&level.getFluidState(next).is(net.minecraft.tags.FluidTags.WATER))ambient|=1<<side;}
        return sources(open,kind(stored,0),kind(stored,1),ambient);
    }
}
