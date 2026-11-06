package com.vandorlabs.neoforge.door;

import com.vandorlabs.fluid.DoorWaterOutlets;
import com.vandorlabs.neoforge.fluid.NativeFluidWorld;
import net.minecraft.core.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;

/** Owned vanilla outlets, independent of the reversible effective fluid/render overlay. */
public final class NativeDoorWaterGush {
    private NativeDoorWaterGush(){}
    public static void update(NativeProgrammableDoorEntity owner,boolean stop){
        Level level=owner.getLevel();if(level==null||level.isClientSide||owner.large())return;
        BlockPos lower=owner.getBlockPos();var base=owner.getBlockState();
        if(!(base.getBlock() instanceof NativeProgrammableDoorBlock block)||base.getValue(NativeProgrammableDoorBlock.HALF)!=DoubleBlockHalf.LOWER)return;
        if(!stop&&(!NativeFluidWorld.installed(owner)||!NativeDoorLifecycle.complete(level,lower,block)||owner.placementRecovery()))return;
        if(!owner.beginWaterGush())return;try{
        int previous=owner.waterOutlets(),retained=0;boolean supplying=false;
        if(!stop&&base.getValue(NativeProgrammableDoorBlock.OPEN))for(int half=0;half<2;half++){
            BlockPos pos=lower.above(half);if(!NativeFluidWorld.loaded(level,pos)||!level.getBlockState(pos).is(block)||!(level.getBlockEntity(pos) instanceof NativeProgrammableDoorEntity part))continue;
            var data=part.containedFluids().snapshot();var first=data.side(0).fluid();var second=data.side(1).fluid();int wet=DoorWaterOutlets.wetSide(first.is(Fluids.WATER),second.is(Fluids.WATER),!first.isEmpty(),!second.isEmpty());if(wet<0)continue;supplying=true;
            Direction face=base.getValue(NativeProgrammableDoorBlock.FACING);Direction exit=wet==0?face:face.getOpposite();int bit=1<<(half*4+exit.get2DDataValue());BlockPos out=pos.relative(exit);
            if(!NativeFluidWorld.loaded(level,out)){retained|=previous&bit;continue;}
            var target=level.getBlockState(out);boolean water=target.getBlock() instanceof LiquidBlock&&target.getFluidState().is(FluidTags.WATER);int action=DoorWaterOutlets.supply((previous&bit)!=0,true,water,water&&target.getFluidState().isSource(),!target.getFluidState().isEmpty(),!water&&target.getFluidState().isEmpty()&&target.canBeReplaced());
            if(action==DoorWaterOutlets.RETAIN)retained|=bit;
            else if(action==DoorWaterOutlets.PUBLISH){retained|=bit;level.setBlock(out,Blocks.WATER.defaultBlockState(),Block.UPDATE_ALL);level.scheduleTick(out,Fluids.WATER,Fluids.WATER.getTickDelay(level));}
        }
        for(int half=0;half<2;half++)for(Direction face:Direction.Plane.HORIZONTAL){int bit=1<<(half*4+face.get2DDataValue());if((previous&bit)==0||(retained&bit)!=0)continue;BlockPos out=lower.above(half).relative(face);
            // A destroyed owner has no future tick: resolve its saved boundary outlet now.
            if(!stop&&!NativeFluidWorld.loaded(level,out)){retained|=bit;continue;}
            var target=level.getBlockState(out);boolean water=target.getBlock() instanceof LiquidBlock&&target.getFluidState().is(FluidTags.WATER);int action=DoorWaterOutlets.retire(true,false,true,stop,water,water&&target.getFluidState().isSource());
            if(action==DoorWaterOutlets.DRAIN){level.setBlock(out,Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,1),Block.UPDATE_ALL);level.scheduleTick(out,Fluids.FLOWING_WATER,Fluids.FLOWING_WATER.getTickDelay(level));}
        }
        owner.setWaterOutlets(retained);owner.scheduleWaterGush(!stop&&(supplying||retained!=0));
        }finally{owner.endWaterGush();}
    }
}
